package com.sy.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sy.mapper.AnalysisTaskMapper;
import com.sy.pojo.AnalysisTask;
import com.sy.service.VisualizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * 可视化服务实现
 * 处理 ARG 抗性基因检测结果的可视化
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VisualizationServiceImpl implements VisualizationService {

    private final AnalysisTaskMapper analysisTaskMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Value("${analysis.output-dir:./genome_outputs}")
    private String outputDir;

    @Override
    public Map<String, Object> getGenomeVisualization(Long taskId, Long userId) {
        // 验证任务
        AnalysisTask task = validateTask(taskId, userId);
        
        try {
            String taskOutputDir = Paths.get(outputDir, "task_" + taskId).toString();
            
            Map<String, Object> visualization = new HashMap<>();
            
            // 基本信息
            Map<String, Object> genomeInfo = new HashMap<>();
            genomeInfo.put("taskId", taskId);
            genomeInfo.put("taskName", task.getTaskName());
            genomeInfo.put("status", task.getStatus());
            genomeInfo.put("argCount", task.getProphageCount());
            visualization.put("genomeInfo", genomeInfo);
            
            // 读取 ARG 预测结果
            List<Map<String, Object>> argResults = parseArgResultsList(taskOutputDir);
            visualization.put("argResults", argResults);
            
            // 日志
            try {
                String jsonStr = objectMapper.writeValueAsString(visualization);
                int jsonBytes = jsonStr.getBytes("UTF-8").length;
                log.info("任务 {} 返回数据大小: {} KB", taskId, jsonBytes / 1024);
            } catch (Exception e) {
                log.debug("计算 JSON 大小失败", e);
            }
            
            log.info("成功加载任务 {} 的 ARG 可视化数据，共 {} 条记录", taskId, argResults.size());
            
            return visualization;
            
        } catch (Exception e) {
            log.error("读取可视化数据失败: taskId={}", taskId, e);
            throw new RuntimeException("读取可视化数据失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 解析 ARG 预测结果（公共方法，供 DockerServiceImpl 调用）
     */
    public Map<String, Object> parseArgOutput(String taskOutputDir) throws IOException {
        Map<String, Object> result = new HashMap<>();
        
        Path argFile = Paths.get(taskOutputDir, "arg_predictions.tsv");
        
        // 如果默认文件不存在，尝试查找其他 TSV 文件
        if (!Files.exists(argFile)) {
            log.warn("未找到 arg_predictions.tsv，尝试查找其他文件");
            
            // 先检查 arg 子目录（MAG 任务）
            Path argSubDir = Paths.get(taskOutputDir, "arg");
            File outputDirFile;
            if (Files.exists(argSubDir) && Files.isDirectory(argSubDir)) {
                outputDirFile = argSubDir.toFile();
            } else {
                outputDirFile = new File(taskOutputDir);
            }
            
            File[] files = outputDirFile.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.getName().endsWith(".tsv")) {
                        argFile = f.toPath();
                        log.info("找到备用文件: {}", f.getName());
                        break;
                    }
                }
            }
        }
        
        List<Map<String, Object>> argResults;
        if (Files.exists(argFile)) {
            argResults = parseArgResultsListFromFile(argFile);
        } else {
            log.warn("未找到任何 TSV 输出文件，返回空结果");
            argResults = new ArrayList<>();
        }
        
        result.put("argCount", argResults.size());
        result.put("argResults", argResults);
        return result;
    }
    
    /**
     * 解析 ARG 预测结果（返回列表）
     */
    private List<Map<String, Object>> parseArgResultsList(String taskOutputDir) throws IOException {
        // 优先查找 all_predictions.tsv
        Path argFile = Paths.get(taskOutputDir, "all_predictions.tsv");
        
        if (!Files.exists(argFile)) {
            // 尝试 arg_predictions.tsv
            argFile = Paths.get(taskOutputDir, "arg_predictions.tsv");
        }
        
        // 如果根目录没找到，检查 arg 子目录（MAG 任务）
        if (!Files.exists(argFile)) {
            Path argSubDir = Paths.get(taskOutputDir, "arg");
            if (Files.exists(argSubDir) && Files.isDirectory(argSubDir)) {
                argFile = argSubDir.resolve("all_predictions.tsv");
                if (!Files.exists(argFile)) {
                    argFile = argSubDir.resolve("arg_predictions.tsv");
                }
                log.info("在 arg 子目录查找输出文件: {}", argFile);
            }
        }
        
        if (!Files.exists(argFile)) {
            log.warn("ARG 输出文件不存在: {}", taskOutputDir);
            return new ArrayList<>();
        }
        
        return parseArgResultsListFromFile(argFile);
    }
    
    /**
     * 从指定文件解析 ARG 预测结果
     */
    private List<Map<String, Object>> parseArgResultsListFromFile(Path argFile) throws IOException {
        List<Map<String, Object>> results = new ArrayList<>();
        
        try (BufferedReader reader = Files.newBufferedReader(argFile)) {
            String line;
            String[] headers = null;
            int index = 1;
            
            while ((line = reader.readLine()) != null) {
                // 跳过空行和注释
                if (line.trim().isEmpty() || line.startsWith("#")) {
                    continue;
                }
                
                String[] values = line.split("\t");
                
                // 第一行是表头
                if (headers == null) {
                    headers = values;
                    continue;
                }
                
                // 解析数据行
                // 格式: id, is_arg, pred_prob, arg_class, class_prob, prob
                Map<String, Object> result = new HashMap<>();
                result.put("index", index++);
                result.put("id", values.length > 0 ? values[0] : "");
                result.put("isArg", values.length > 1 ? "True".equalsIgnoreCase(values[1]) : false);
                result.put("predProb", values.length > 2 && !values[2].isEmpty() ? parseDouble(values[2]) : null);
                result.put("argClass", values.length > 3 ? values[3] : "");
                result.put("classProb", values.length > 4 && !values[4].isEmpty() ? parseDouble(values[4]) : null);
                result.put("prob", values.length > 5 && !values[5].isEmpty() ? parseDouble(values[5]) : null);
                
                results.add(result);
            }
        }
        
        log.info("解析到 {} 个 ARG 预测结果", results.size());
        return results;
    }
    
    /**
     * 安全解析 double
     */
    private Double parseDouble(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            log.warn("无法解析数值: {}", value);
            return null;
        }
    }

    @Override
    public Map<String, Object> getProphageDetail(Long taskId, Long regionId, Long userId) {
        throw new UnsupportedOperationException("此功能不再支持");
    }

    @Override
    public Map<String, Object> getStatistics(Long taskId, Long userId) {
        // 验证任务
        AnalysisTask task = validateTask(taskId, userId);
        
        try {
            String taskOutputDir = Paths.get(outputDir, "task_" + taskId).toString();
            List<Map<String, Object>> argResults = parseArgResultsList(taskOutputDir);
            
            Map<String, Object> statistics = new HashMap<>();
            
            // 基本统计
            long argCount = argResults.stream().filter(r -> Boolean.TRUE.equals(r.get("isArg"))).count();
            long nonArgCount = argResults.size() - argCount;
            
            statistics.put("totalSequences", argResults.size());
            statistics.put("argCount", argCount);
            statistics.put("nonArgCount", nonArgCount);
            
            // ARG 类别统计
            Map<String, Long> classDistribution = new HashMap<>();
            for (Map<String, Object> result : argResults) {
                if (Boolean.TRUE.equals(result.get("isArg"))) {
                    String argClass = (String) result.get("argClass");
                    if (argClass != null && !argClass.isEmpty()) {
                        classDistribution.merge(argClass, 1L, Long::sum);
                    }
                }
            }
            statistics.put("classDistribution", classDistribution);
            
            log.info("成功生成任务 {} 的统计数据", taskId);
            return statistics;
            
        } catch (Exception e) {
            log.error("生成统计数据失败: taskId={}", taskId, e);
            throw new RuntimeException("生成统计数据失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> exportVisualizationData(Long taskId, Long userId) {
        // 验证任务
        AnalysisTask task = validateTask(taskId, userId);
        
        try {
            Map<String, Object> exportData = new HashMap<>();
            
            // 任务信息
            Map<String, Object> taskInfo = new HashMap<>();
            taskInfo.put("taskId", task.getTaskId());
            taskInfo.put("taskName", task.getTaskName());
            taskInfo.put("status", task.getStatus());
            taskInfo.put("createdAt", task.getCreatedAt() != null ? task.getCreatedAt().toString() : null);
            taskInfo.put("completedAt", task.getCompletedAt() != null ? task.getCompletedAt().toString() : null);
            exportData.put("taskInfo", taskInfo);
            
            // 可视化数据
            exportData.put("visualization", getGenomeVisualization(taskId, userId));
            
            // 统计数据
            exportData.put("statistics", getStatistics(taskId, userId));
            
            log.info("成功导出任务 {} 的完整数据", taskId);
            return exportData;
            
        } catch (Exception e) {
            log.error("导出数据失败: taskId={}", taskId, e);
            throw new RuntimeException("导出数据失败: " + e.getMessage(), e);
        }
    }

    /**
     * 验证任务
     */
    private AnalysisTask validateTask(Long taskId, Long userId) {
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
        return task;
    }
}
