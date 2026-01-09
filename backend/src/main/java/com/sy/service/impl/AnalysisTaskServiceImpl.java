package com.sy.service.impl;

import com.sy.mapper.AnalysisResultMapper;
import com.sy.mapper.AnalysisTaskMapper;
import com.sy.mapper.GenomeFileMapper;
import com.sy.pojo.AnalysisTask;
import com.sy.pojo.GenomeFile;
import com.sy.service.AnalysisTaskService;
import com.sy.service.MagAnalysisService;
import com.sy.service.TaskQueueManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 分析任务服务实现
 * 处理抗性基因（ARG）识别和分类任务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisTaskServiceImpl implements AnalysisTaskService {

    private final AnalysisTaskMapper analysisTaskMapper;
    private final AnalysisResultMapper analysisResultMapper;
    private final GenomeFileMapper genomeFileMapper;
    private final DockerServiceImpl dockerService;
    private final TaskQueueManager taskQueueManager;
    private final MagAnalysisService magAnalysisService;
    
    @Value("${analysis.output-dir:./outputs}")
    private String outputBaseDir;

    @Value("${file.upload.mag-dir:./uploads/mag}")
    private String magUploadDir;

    @Override
    public Map<String, Object> createTask(Long fileId, Long userId, Map<String, Object> params) {
        // 验证文件是否存在
        GenomeFile genomeFile = genomeFileMapper.selectById(fileId);
        if (genomeFile == null) {
            throw new RuntimeException("文件不存在");
        }
        if (!genomeFile.getUserId().equals(userId)) {
            throw new RuntimeException("无权访问该文件");
        }
        
        // 创建任务
        AnalysisTask task = new AnalysisTask();
        task.setUserId(userId);
        task.setFileId(fileId);
        task.setTaskName("抗性基因检测 - " + genomeFile.getOriginalFilename());
        task.setStatus("PENDING");
        task.setProgress(0);
        task.setCreatedAt(LocalDateTime.now());
        
        // 保存参数
        if (params != null && !params.isEmpty()) {
            task.setParameters(convertToJson(params));
        }
        
        // 保存到数据库
        analysisTaskMapper.insert(task);
        
        // 设置输出目录
        String outputDir = outputBaseDir + File.separator + "task_" + task.getTaskId();
        task.setOutputDir(outputDir);
        analysisTaskMapper.updateById(task);
        
        log.info("创建分析任务: taskId={}, fileId={}, userId={}", task.getTaskId(), fileId, userId);
        
        // 异步执行任务
        taskQueueManager.submitTask(task.getTaskId(), 
                () -> executeAnalysis(task.getTaskId(), genomeFile, params));
        
        return convertTaskToMap(task, genomeFile.getOriginalFilename());
    }

    @Override
    public List<Map<String, Object>> getUserTasks(Long userId, String status) {
        List<AnalysisTask> tasks;
        if (status != null && !status.isEmpty()) {
            tasks = analysisTaskMapper.findByUserIdAndStatus(userId, status);
        } else {
            tasks = analysisTaskMapper.findByUserId(userId);
        }
        
        return tasks.stream()
                .map(task -> convertTaskToMap(task, getTaskDisplayName(task)))
                .collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> searchTasks(Long userId, String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return getUserTasks(userId, null);
        }
        
        List<AnalysisTask> tasks;
        try {
            Long taskId = Long.parseLong(keyword.trim());
            AnalysisTask task = analysisTaskMapper.selectById(taskId);
            if (task != null && task.getUserId().equals(userId)) {
                tasks = List.of(task);
            } else {
                tasks = analysisTaskMapper.searchTasks(userId, keyword.trim());
            }
        } catch (NumberFormatException e) {
            tasks = analysisTaskMapper.searchTasks(userId, keyword.trim());
        }
        
        return tasks.stream()
                .map(task -> convertTaskToMap(task, getTaskDisplayName(task)))
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getTaskDetail(Long taskId, Long userId) {
        AnalysisTask task = analysisTaskMapper.selectById(taskId);
        if (task == null) {
            throw new RuntimeException("任务不存在");
        }
        if (!task.getUserId().equals(userId)) {
            throw new RuntimeException("无权访问该任务");
        }
        
        return convertTaskToMap(task, getTaskDisplayName(task));
    }

    @Override
    public Map<String, Object> getTaskStatus(Long taskId, Long userId) {
        AnalysisTask task = analysisTaskMapper.selectById(taskId);
        if (task == null) {
            throw new RuntimeException("任务不存在");
        }
        if (!task.getUserId().equals(userId)) {
            throw new RuntimeException("无权访问该任务");
        }
        
        Map<String, Object> status = new HashMap<>();
        status.put("taskId", task.getTaskId());
        status.put("status", task.getStatus());
        status.put("progress", task.getProgress());
        status.put("startedAt", task.getStartedAt() != null ? task.getStartedAt().toString() : null);
        status.put("completedAt", task.getCompletedAt() != null ? task.getCompletedAt().toString() : null);
        status.put("errorMessage", task.getErrorMessage());
        return status;
    }

    @Override
    public void cancelTask(Long taskId, Long userId) {
        AnalysisTask task = analysisTaskMapper.selectById(taskId);
        if (task == null) {
            throw new RuntimeException("任务不存在");
        }
        if (!task.getUserId().equals(userId)) {
            throw new RuntimeException("无权操作该任务");
        }
        
        String status = task.getStatus();
        if ("COMPLETED".equals(status) || "FAILED".equals(status)) {
            throw new RuntimeException("任务已完成，无法取消");
        }
        
        // 终止进程
        dockerService.cancelAnalysis(taskId);
        
        task.setStatus("CANCELLED");
        task.setCompletedAt(LocalDateTime.now());
        analysisTaskMapper.updateById(task);
        
        log.info("任务已取消: taskId={}", taskId);
    }

    @Override
    public void deleteTask(Long taskId, Long userId) {
        AnalysisTask task = analysisTaskMapper.selectById(taskId);
        if (task == null) {
            throw new RuntimeException("任务不存在");
        }
        if (!task.getUserId().equals(userId)) {
            throw new RuntimeException("无权删除该任务");
        }
        
        analysisTaskMapper.deleteById(taskId);
        log.info("任务已删除: taskId={}", taskId);
    }

    @Override
    public Map<String, Object> getTaskResult(Long taskId, Long userId) {
        AnalysisTask task = analysisTaskMapper.selectById(taskId);
        if (task == null) {
            throw new RuntimeException("任务不存在");
        }
        if (!task.getUserId().equals(userId)) {
            throw new RuntimeException("无权访问该任务");
        }
        
        if (!"COMPLETED".equals(task.getStatus())) {
            throw new RuntimeException("任务未完成");
        }
        
        GenomeFile fileInfo = genomeFileMapper.selectById(task.getFileId());
        
        Map<String, Object> resultData = new HashMap<>();
        resultData.put("taskId", task.getTaskId());
        resultData.put("fileName", fileInfo != null ? fileInfo.getOriginalFilename() : "未知文件");
        resultData.put("status", task.getStatus());
        resultData.put("createdAt", task.getCreatedAt());
        resultData.put("completedAt", task.getCompletedAt());
        
        // 计算运行时长
        if (task.getStartedAt() != null && task.getCompletedAt() != null) {
            long seconds = java.time.Duration.between(task.getStartedAt(), task.getCompletedAt()).getSeconds();
            resultData.put("duration", formatDuration(seconds));
        } else {
            resultData.put("duration", "-");
        }
        
        resultData.put("argCount", task.getProphageCount());
        
        return resultData;
    }

    /**
     * 执行分析任务
     */
    private void executeAnalysis(Long taskId, GenomeFile fileInfo, Map<String, Object> params) {
        AnalysisTask task = analysisTaskMapper.selectById(taskId);
        if (task == null) {
            log.error("任务不存在: taskId={}", taskId);
            return;
        }
        
        try {
            // 更新任务状态
            task.setStatus("RUNNING");
            task.setStartedAt(LocalDateTime.now());
            task.setProgress(10);
            analysisTaskMapper.updateById(task);
            
            log.info("开始执行分析任务: taskId={}", taskId);
            
            String inputFilePath = fileInfo.getFilePath();
            String outputDir = task.getOutputDir();
            
            task.setProgress(20);
            analysisTaskMapper.updateById(task);
            
            // 执行抗性基因检测
            Map<String, Object> result = dockerService.runArgDetection(taskId, inputFilePath, outputDir, params);
            
            task.setProgress(90);
            analysisTaskMapper.updateById(task);
            
            // 更新任务状态
            task.setStatus("COMPLETED");
            task.setProgress(100);
            task.setCompletedAt(LocalDateTime.now());
            
            // 保存 ARG 数量
            List<Map<String, Object>> argResults = (List<Map<String, Object>>) result.get("argResults");
            if (argResults != null) {
                long argCount = argResults.stream().filter(r -> Boolean.TRUE.equals(r.get("isArg"))).count();
                task.setProphageCount((int) argCount);
            }
            
            analysisTaskMapper.updateById(task);
            log.info("分析任务完成: taskId={}", taskId);
            
        } catch (Exception e) {
            log.error("分析任务失败: taskId={}", taskId, e);
            task.setStatus("FAILED");
            task.setErrorMessage(e.getMessage());
            task.setCompletedAt(LocalDateTime.now());
            analysisTaskMapper.updateById(task);
        }
    }

    /**
     * 将任务转换为Map
     */
    private Map<String, Object> convertTaskToMap(AnalysisTask task, String fileName) {
        Map<String, Object> map = new HashMap<>();
        map.put("taskId", task.getTaskId());
        map.put("userId", task.getUserId());
        map.put("fileId", task.getFileId());
        map.put("fileName", fileName);
        map.put("taskName", task.getTaskName());
        map.put("status", task.getStatus());
        map.put("progress", task.getProgress());
        map.put("createdAt", task.getCreatedAt() != null ? task.getCreatedAt().toString() : null);
        map.put("startedAt", task.getStartedAt() != null ? task.getStartedAt().toString() : null);
        map.put("completedAt", task.getCompletedAt() != null ? task.getCompletedAt().toString() : null);
        map.put("errorMessage", task.getErrorMessage());
        map.put("argCount", task.getProphageCount());
        return map;
    }

    /**
     * 格式化时长
     */
    private String formatDuration(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        
        StringBuilder sb = new StringBuilder();
        if (hours > 0) sb.append(hours).append("小时");
        if (minutes > 0) sb.append(minutes).append("分");
        if (secs > 0 || sb.length() == 0) sb.append(secs).append("秒");
        
        return sb.toString();
    }

    /**
     * 获取任务的显示文件名
     * 对于普通任务，返回关联文件的原始文件名
     * 对于 MAG 任务，返回 MAG 目录名或从任务名称中提取
     */
    private String getTaskDisplayName(AnalysisTask task) {
        // 如果有关联文件，优先使用文件名
        if (task.getFileId() != null) {
            GenomeFile file = genomeFileMapper.selectById(task.getFileId());
            if (file != null) {
                return file.getOriginalFilename();
            }
        }
        
        // MAG 任务：尝试从 magDirPath 提取目录名
        if ("MAG".equals(task.getTaskType()) && task.getMagDirPath() != null) {
            Path magDir = Paths.get(task.getMagDirPath());
            String dirName = magDir.getFileName().toString();
            // 如果目录名是自动生成的（如 mag_1_20260109...），则从任务名称提取
            if (dirName.startsWith("mag_") && task.getTaskName() != null) {
                // 任务名称格式: "MAG 抗性基因检测 - xxx"
                String taskName = task.getTaskName();
                if (taskName.contains(" - ")) {
                    return taskName.substring(taskName.lastIndexOf(" - ") + 3);
                }
            }
            return dirName;
        }
        
        // 从任务名称提取
        if (task.getTaskName() != null && task.getTaskName().contains(" - ")) {
            return task.getTaskName().substring(task.getTaskName().lastIndexOf(" - ") + 3);
        }
        
        return "Unknown";
    }

    /**
     * 将Map转换为JSON字符串
     */
    private String convertToJson(Map<String, Object> map) {
        try {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(entry.getKey()).append("\":");
                Object value = entry.getValue();
                if (value instanceof String) {
                    sb.append("\"").append(value).append("\"");
                } else {
                    sb.append(value);
                }
                first = false;
            }
            sb.append("}");
            return sb.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * 创建 MAG 分析任务
     * @param magDirPath MAG 文件夹路径
     * @param userId 用户ID
     * @param magName MAG 名称
     * @param params 分析参数
     * @return 任务信息
     */
    public Map<String, Object> createMagTask(String magDirPath, Long userId, String magName, Map<String, Object> params) {
        // 统计 MAG 文件数量
        int fileCount = 0;
        try {
            Path magDir = Paths.get(magDirPath);
            if (Files.exists(magDir) && Files.isDirectory(magDir)) {
                fileCount = (int) Files.list(magDir)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase();
                        return name.endsWith(".fa") || name.endsWith(".fasta") || name.endsWith(".fna");
                    })
                    .count();
            }
        } catch (Exception e) {
            log.warn("统计 MAG 文件数量失败", e);
        }
        
        // 创建任务
        AnalysisTask task = new AnalysisTask();
        task.setUserId(userId);
        task.setFileId(null);  // MAG 任务没有对应的单个文件
        task.setTaskType("MAG");  // 设置任务类型
        task.setMagDirPath(magDirPath);  // 设置 MAG 目录路径
        task.setMagFileCount(fileCount);  // 设置文件数量
        task.setTaskName("MAG 抗性基因检测 - " + magName);
        task.setStatus("PENDING");
        task.setProgress(0);
        task.setCreatedAt(LocalDateTime.now());
        
        // 保存参数（包含 MAG 路径和类型标识）
        Map<String, Object> taskParams = new HashMap<>();
        if (params != null) {
            taskParams.putAll(params);
        }
        taskParams.put("analysisType", "mag");
        taskParams.put("magDirPath", magDirPath);
        taskParams.put("magName", magName);
        task.setParameters(convertToJson(taskParams));
        
        // 保存到数据库
        analysisTaskMapper.insert(task);
        
        // 设置输出目录
        String outputDir = outputBaseDir + File.separator + "task_" + task.getTaskId();
        task.setOutputDir(outputDir);
        analysisTaskMapper.updateById(task);
        
        log.info("创建 MAG 分析任务: taskId={}, magDir={}, userId={}", task.getTaskId(), magDirPath, userId);
        
        // 异步执行 MAG 分析任务
        taskQueueManager.submitTask(task.getTaskId(), 
                () -> executeMagAnalysis(task.getTaskId(), magDirPath, params));
        
        return convertTaskToMap(task, magName);
    }

    /**
     * 执行 MAG 分析任务
     */
    private void executeMagAnalysis(Long taskId, String magDirPath, Map<String, Object> params) {
        AnalysisTask task = analysisTaskMapper.selectById(taskId);
        if (task == null) {
            log.error("任务不存在: taskId={}", taskId);
            return;
        }
        
        try {
            // 更新任务状态
            task.setStatus("RUNNING");
            task.setStartedAt(LocalDateTime.now());
            task.setProgress(0);
            analysisTaskMapper.updateById(task);
            
            log.info("开始执行 MAG 分析任务: taskId={}", taskId);
            
            Path magDir = Paths.get(magDirPath);
            Path outputDir = Paths.get(task.getOutputDir());
            
            // 执行 MAG 分析（包含 Prodigal 预处理 + ARG 分析）
            Map<String, Object> result = magAnalysisService.analyzeMag(taskId, magDir, outputDir, params);
            
            // 更新任务状态
            task.setStatus("COMPLETED");
            task.setProgress(100);
            task.setCompletedAt(LocalDateTime.now());
            
            // 保存 ARG 数量
            Object argCountObj = result.get("argCount");
            if (argCountObj != null) {
                task.setProphageCount((Integer) argCountObj);
            }
            
            analysisTaskMapper.updateById(task);
            log.info("MAG 分析任务完成: taskId={}", taskId);
            
        } catch (Exception e) {
            log.error("MAG 分析任务失败: taskId={}", taskId, e);
            task.setStatus("FAILED");
            task.setErrorMessage(e.getMessage());
            task.setCompletedAt(LocalDateTime.now());
            analysisTaskMapper.updateById(task);
        }
    }
}
