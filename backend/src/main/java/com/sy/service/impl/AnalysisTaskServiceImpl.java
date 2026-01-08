package com.sy.service.impl;

import com.sy.mapper.AnalysisResultMapper;
import com.sy.mapper.AnalysisTaskMapper;
import com.sy.mapper.GenomeFileMapper;
import com.sy.pojo.AnalysisResult;
import com.sy.pojo.AnalysisTask;
import com.sy.pojo.GenomeFile;
import com.sy.service.AnalysisTaskService;
import com.sy.service.DockerService;
import com.sy.service.TaskQueueManager;
import com.sy.service.impl.DockerServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 分析任务服务实现（使用数据库）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisTaskServiceImpl implements AnalysisTaskService {

    private final AnalysisTaskMapper analysisTaskMapper;
    private final AnalysisResultMapper analysisResultMapper;
    private final GenomeFileMapper genomeFileMapper;
    private final DockerService dockerService;
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
        
        // 获取分析类型（默认为 genomad 原噬菌体识别）
        String analysisType = params != null ? (String) params.getOrDefault("analysisType", "genomad") : "genomad";
        String taskNamePrefix = "arg".equals(analysisType) ? "抗性基因检测" : "原噬菌体识别";
        
        // 创建任务
        AnalysisTask task = new AnalysisTask();
        task.setUserId(userId);
        task.setFileId(fileId);
        task.setTaskName(taskNamePrefix + " - " + genomeFile.getOriginalFilename());
        task.setStatus("PENDING");
        task.setProgress(0);
        task.setCreatedAt(LocalDateTime.now());
        
        // 保存参数（JSON格式）
        if (params != null && !params.isEmpty()) {
            task.setParameters(convertToJson(params));
        }
        
        // 保存到数据库（先保存以获取 taskId）
        analysisTaskMapper.insert(task);
        
        // 使用 taskId 创建独立的输出目录（确保隔离）
        String outputDir = outputBaseDir + File.separator + "task_" + task.getTaskId();
        task.setOutputDir(outputDir);
        
        // 更新输出目录
        analysisTaskMapper.updateById(task);
        
        log.info("创建分析任务: taskId={}, fileId={}, userId={}, analysisType={}, outputDir={}", 
                task.getTaskId(), fileId, userId, analysisType, outputDir);
        
        // 通过任务队列提交异步执行（确保按顺序执行，避免并发冲突）
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
        // 尝试将关键字解析为数字（任务ID）
        try {
            Long taskId = Long.parseLong(keyword.trim());
            // 如果是数字，先按任务ID精确查找
            AnalysisTask task = analysisTaskMapper.selectById(taskId);
            if (task != null && task.getUserId().equals(userId)) {
                tasks = List.of(task);
            } else {
                // 如果精确查找失败，使用模糊搜索
                tasks = analysisTaskMapper.searchTasks(userId, keyword.trim());
            }
        } catch (NumberFormatException e) {
            // 如果不是数字，使用模糊搜索
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
        
        // 终止Docker分析进程
        try {
            dockerService.cancelAnalysis(taskId);
            log.info("已终止任务进程: taskId={}", taskId);
        } catch (Exception e) {
            log.error("终止任务进程失败", e);
        }
        
        // 如果任务有容器ID，也尝试停止容器（备用方案）
        if (task.getDockerContainerId() != null) {
            try {
                dockerService.stopContainer(task.getDockerContainerId());
            } catch (Exception e) {
                log.debug("停止容器失败（可能容器已不存在）", e);
            }
        }
        
        task.setStatus("CANCELLED");
        task.setCompletedAt(LocalDateTime.now());
        analysisTaskMapper.updateById(task);
        
        log.info("任务已取消: taskId={}, userId={}", taskId, userId);
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
        
        // 删除关联的结果
        analysisResultMapper.delete(null); // TODO: 使用条件删除
        
        // 删除任务
        analysisTaskMapper.deleteById(taskId);
        
        log.info("任务已删除: taskId={}, userId={}", taskId, userId);
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
        
        String status = task.getStatus();
        if (!"COMPLETED".equals(status)) {
            throw new RuntimeException("任务未完成，无法获取结果");
        }
        
        // 获取文件信息
        GenomeFile fileInfo = genomeFileMapper.selectById(task.getFileId());
        
        // 获取原噬菌体区域结果
        List<AnalysisResult> results = analysisResultMapper.findByTaskId(taskId);
        
        // 构建前端期望的数据格式
        Map<String, Object> resultData = new HashMap<>();
        
        // 任务基本信息
        resultData.put("taskId", task.getTaskId());
        resultData.put("fileName", fileInfo != null ? fileInfo.getOriginalFilename() : "未知文件");
        resultData.put("status", task.getStatus());
        resultData.put("createdAt", task.getCreatedAt());
        resultData.put("completedAt", task.getCompletedAt());
        
        // 计算运行时长
        if (task.getStartedAt() != null && task.getCompletedAt() != null) {
            long seconds = java.time.Duration.between(task.getStartedAt(), task.getCompletedAt()).getSeconds();
            long minutes = seconds / 60;
            long secs = seconds % 60;
            resultData.put("duration", String.format("%d分%d秒", minutes, secs));
        } else {
            resultData.put("duration", "-");
        }
        
        // 分析结果数据
        resultData.put("genomeLength", task.getGenomeLength());
        resultData.put("prophageCount", task.getProphageCount());
        
        // 原噬菌体列表（前端期望的字段名是prophages）
        List<Map<String, Object>> prophages = results.stream()
                .map(result -> {
                    Map<String, Object> prophage = new HashMap<>();
                    prophage.put("name", "Prophage_" + result.getRegionIndex());
                    prophage.put("start", result.getStartPos());
                    prophage.put("end", result.getEndPos());
                    prophage.put("confidence", result.getConfidence() != null ? (int)(result.getConfidence() * 100) : 0);
                    prophage.put("type", result.getCompleteness() != null ? 
                            (result.getCompleteness().equals("complete") ? "完整" : "不完整") : "未知");
                    return prophage;
                })
                .collect(Collectors.toList());
        resultData.put("prophages", prophages);
        
        // 保持兼容性，也包含prophageRegions
        resultData.put("prophageRegions", results.stream()
                .map(this::convertResultToMap)
                .collect(Collectors.toList()));
        
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
        
        // 获取分析类型
        String analysisType = params != null ? (String) params.getOrDefault("analysisType", "genomad") : "genomad";
        
        try {
            // 更新任务状态为运行中
            task.setStatus("RUNNING");
            task.setStartedAt(LocalDateTime.now());
            task.setProgress(10);
            analysisTaskMapper.updateById(task);
            
            log.info("开始执行分析任务: taskId={}, analysisType={}", taskId, analysisType);
            
            // 调用 Docker 服务执行分析
            String inputFilePath = fileInfo.getFilePath();
            String outputDir = task.getOutputDir();
            
            task.setProgress(20);
            analysisTaskMapper.updateById(task);
            
            Map<String, Object> result;
            
            if ("arg".equals(analysisType)) {
                // 执行抗性基因检测
                if (dockerService instanceof DockerServiceImpl) {
                    result = ((DockerServiceImpl) dockerService).runArgDetection(taskId, inputFilePath, outputDir, params);
                } else {
                    throw new RuntimeException("ARG 分析需要 DockerServiceImpl 实现");
                }
            } else {
                // 执行原噬菌体识别（默认）
                if (dockerService instanceof DockerServiceImpl) {
                    result = ((DockerServiceImpl) dockerService).runProphageDetection(taskId, inputFilePath, outputDir, params);
                } else {
                    result = dockerService.runProphageDetection(inputFilePath, outputDir, params);
                }
            }
            
            task.setProgress(90);
            analysisTaskMapper.updateById(task);
            
            // 保存结果到数据库（ARG 分析只需要输出文件，不需要存数据库）
            if (!"arg".equals(analysisType)) {
                saveResults(taskId, result);
            }
            
            // 更新任务状态
            task.setStatus("COMPLETED");
            task.setProgress(100);
            task.setCompletedAt(LocalDateTime.now());
            
            if ("arg".equals(analysisType)) {
                // ARG 结果统计
                List<Map<String, Object>> argResults = (List<Map<String, Object>>) result.get("argResults");
                task.setProphageCount(argResults != null ? argResults.size() : 0); // 复用字段存储 ARG 数量
            } else {
                // 原噬菌体结果统计
                Object genomeLengthObj = result.get("genomeLength");
                if (genomeLengthObj instanceof Number) {
                    task.setGenomeLength(((Number) genomeLengthObj).longValue());
                }
                
                List<Map<String, Object>> prophageRegions = (List<Map<String, Object>>) result.get("prophageRegions");
                task.setProphageCount(prophageRegions != null ? prophageRegions.size() : 0);
            }
            
            analysisTaskMapper.updateById(task);
            
            log.info("分析任务完成: taskId={}, analysisType={}", taskId, analysisType);
            
        } catch (Exception e) {
            log.error("分析任务失败: taskId={}, analysisType={}", taskId, analysisType, e);
            task.setStatus("FAILED");
            task.setErrorMessage(e.getMessage());
            task.setCompletedAt(LocalDateTime.now());
            analysisTaskMapper.updateById(task);
        }
    }


    /**
     * 保存分析结果到数据库
     */
    private void saveResults(Long taskId, Map<String, Object> dockerResult) {
        List<Map<String, Object>> prophageRegions = (List<Map<String, Object>>) dockerResult.get("prophageRegions");
        if (prophageRegions == null || prophageRegions.isEmpty()) {
            return;
        }
        
        for (Map<String, Object> region : prophageRegions) {
            AnalysisResult result = new AnalysisResult();
            result.setTaskId(taskId);
            result.setRegionIndex(((Number) region.get("regionId")).intValue());
            result.setStartPos((Integer) region.get("start"));
            result.setEndPos((Integer) region.get("end"));
            result.setLength((Integer) region.get("length"));
            result.setScore(((Number) region.get("score")).doubleValue());
            result.setCompleteness((String) region.get("completeness"));
            result.setGeneCount((Integer) region.get("geneCount"));
            
            // 保存到数据库
            analysisResultMapper.insert(result);
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
        map.put("isArg", isArgTask(task) ? 1 : 0); // 1=ARG任务, 0=Genomad任务
        map.put("status", task.getStatus());
        map.put("progress", task.getProgress());
        map.put("parameters", task.getParameters());
        map.put("createdAt", task.getCreatedAt() != null ? task.getCreatedAt().toString() : null);
        map.put("startedAt", task.getStartedAt() != null ? task.getStartedAt().toString() : null);
        map.put("completedAt", task.getCompletedAt() != null ? task.getCompletedAt().toString() : null);
        map.put("errorMessage", task.getErrorMessage());
        map.put("genomeLength", task.getGenomeLength());
        map.put("prophageCount", task.getProphageCount());
        return map;
    }
    
    /**
     * 判断是否为 ARG 任务
     */
    private boolean isArgTask(AnalysisTask task) {
        // 优先从 taskName 判断
        if (task.getTaskName() != null && task.getTaskName().contains("抗性基因")) {
            return true;
        }
        // 从 parameters 中解析
        if (task.getParameters() != null && task.getParameters().contains("\"analysisType\":\"arg\"")) {
            return true;
        }
        // 从输出目录判断（检查是否存在 arg_predictions.tsv）
        String taskOutputDir = outputBaseDir + File.separator + "task_" + task.getTaskId();
        File argFile = new File(taskOutputDir, "arg_predictions.tsv");
        return argFile.exists();
    }

    /**
     * 将结果转换为Map
     */
    private Map<String, Object> convertResultToMap(AnalysisResult result) {
        Map<String, Object> map = new HashMap<>();
        map.put("regionId", result.getRegionIndex());
        map.put("start", result.getStartPos());
        map.put("end", result.getEndPos());
        map.put("length", result.getLength());
        map.put("strand", result.getStrand());
        map.put("score", result.getScore());
        map.put("confidence", result.getConfidence());
        map.put("completeness", result.getCompleteness());
        map.put("geneCount", result.getGeneCount());
        map.put("gcContent", result.getGcContent());
        return map;
    }

    /**
     * 将Map转换为JSON字符串
     */
    private String convertToJson(Map<String, Object> map) {
        try {
            // 简单实现，实际应该使用Jackson或Gson
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
