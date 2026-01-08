package com.sy.service.impl;

import com.sy.mapper.AnalysisResultMapper;
import com.sy.mapper.AnalysisTaskMapper;
import com.sy.mapper.GenomeFileMapper;
import com.sy.pojo.AnalysisTask;
import com.sy.pojo.GenomeFile;
import com.sy.service.AnalysisTaskService;
import com.sy.service.TaskQueueManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
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
    
    @Value("${analysis.output-dir:./outputs}")
    private String outputBaseDir;

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
                .map(task -> {
                    GenomeFile file = genomeFileMapper.selectById(task.getFileId());
                    return convertTaskToMap(task, file != null ? file.getOriginalFilename() : "Unknown");
                })
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
                .map(task -> {
                    GenomeFile file = genomeFileMapper.selectById(task.getFileId());
                    return convertTaskToMap(task, file != null ? file.getOriginalFilename() : "Unknown");
                })
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
        
        GenomeFile file = genomeFileMapper.selectById(task.getFileId());
        return convertTaskToMap(task, file != null ? file.getOriginalFilename() : "Unknown");
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
}
