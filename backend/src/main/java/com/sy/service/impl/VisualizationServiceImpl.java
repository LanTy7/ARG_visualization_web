package com.sy.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sy.mapper.AnalysisResultMapper;
import com.sy.mapper.AnalysisTaskMapper;
import com.sy.pojo.AnalysisResult;
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
import java.util.stream.Collectors;

/**
 * 可视化服务实现（从genomad输出文件读取数据）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VisualizationServiceImpl implements VisualizationService {

    private final AnalysisTaskMapper analysisTaskMapper;
    private final AnalysisResultMapper analysisResultMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Value("${analysis.output-dir:./genome_outputs}")
    private String outputDir;

    @Override
    public Map<String, Object> getGenomeVisualization(Long taskId, Long userId) {
        // 验证任务
        AnalysisTask task = validateTask(taskId, userId);
        
        try {
            // 判断任务类型
            String analysisType = getAnalysisType(task);
            
            // 构建任务输出目录路径
            String taskOutputDir = Paths.get(outputDir, "task_" + taskId).toString();
            
            // 构建可视化数据
            Map<String, Object> visualization = new HashMap<>();
            
            // 基因组基本信息
            Map<String, Object> genomeInfo = new HashMap<>();
            genomeInfo.put("taskId", taskId);
            genomeInfo.put("taskName", task.getTaskName());
            genomeInfo.put("analysisType", analysisType);
            genomeInfo.put("status", task.getStatus());
            
            if ("arg".equals(analysisType)) {
                // ARG 分析结果
                genomeInfo.put("argCount", task.getProphageCount()); // 复用字段
                visualization.put("genomeInfo", genomeInfo);
                
                // 读取 ARG 预测结果
                List<Map<String, Object>> argResults = parseArgResultsList(taskOutputDir);
                visualization.put("argResults", argResults);
                
                // 计算并打印返回数据的大小
                try {
                    String jsonStr = objectMapper.writeValueAsString(visualization);
                    int jsonBytes = jsonStr.getBytes("UTF-8").length;
                    log.info("任务 {} 返回 JSON 大小: {} bytes ({} KB, {} MB)", 
                            taskId, jsonBytes, jsonBytes / 1024, jsonBytes / 1024 / 1024);
                } catch (Exception e) {
                    log.warn("计算 JSON 大小失败", e);
                }
                
                log.info("成功加载任务 {} 的 ARG 可视化数据，找到 {} 个抗性基因", 
                        taskId, argResults.size());
            } else {
                // 原噬菌体分析结果（默认）
                genomeInfo.put("genomeLength", task.getGenomeLength());
                genomeInfo.put("prophageCount", task.getProphageCount());
                visualization.put("genomeInfo", genomeInfo);
                
                String baseName = getGenomeBaseName(taskOutputDir);
                List<Map<String, Object>> prophageRegions = parseProphageRegions(taskOutputDir, baseName);
                visualization.put("prophageRegions", prophageRegions);
                
                log.info("成功加载任务 {} 的可视化数据，找到 {} 个原噬菌体区域", 
                        taskId, prophageRegions.size());
            }
            
            return visualization;
            
        } catch (Exception e) {
            log.error("读取可视化数据失败: taskId={}", taskId, e);
            throw new RuntimeException("读取可视化数据失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取分析类型
     */
    private String getAnalysisType(AnalysisTask task) {
        // 优先从 taskName 判断
        if (task.getTaskName() != null && task.getTaskName().contains("抗性基因")) {
            return "arg";
        }
        // 从 parameters 中解析
        if (task.getParameters() != null && task.getParameters().contains("\"analysisType\":\"arg\"")) {
            return "arg";
        }
        // 从输出目录判断（检查是否存在 arg_predictions.tsv）
        String taskOutputDir = Paths.get(outputDir, "task_" + task.getTaskId()).toString();
        Path argFile = Paths.get(taskOutputDir, "arg_predictions.tsv");
        if (Files.exists(argFile)) {
            log.info("检测到 ARG 输出文件，任务 {} 为 ARG 类型", task.getTaskId());
            return "arg";
        }
        return "genomad";
    }
    
    /**
     * 解析 ARG 预测结果（公共方法，供 DockerServiceImpl 调用）
     * @param taskOutputDir 任务输出目录
     * @return 包含 argCount 和 argResults 的 Map
     */
    public Map<String, Object> parseArgOutput(String taskOutputDir) throws IOException {
        Map<String, Object> result = new HashMap<>();
        
        Path argFile = Paths.get(taskOutputDir, "arg_predictions.tsv");
        
        // 如果默认文件不存在，尝试查找其他 TSV 文件
        if (!Files.exists(argFile)) {
            log.warn("未找到 arg_predictions.tsv，尝试查找其他文件");
            File outputDir = new File(taskOutputDir);
            File[] files = outputDir.listFiles();
            if (files != null) {
                log.info("输出目录中的文件:");
                for (File f : files) {
                    log.info("  - {}", f.getName());
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
        Path argFile = Paths.get(taskOutputDir, "all_predictions.tsv");
        if (!Files.exists(argFile)) {
            log.warn("ARG 输出文件不存在: {}", argFile);
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
        // 验证任务
        validateTask(taskId, userId);
        
        try {
            // 构建任务输出目录路径
            String taskOutputDir = Paths.get(outputDir, "task_" + taskId).toString();
            String baseName = getGenomeBaseName(taskOutputDir);
            
            // 读取原噬菌体区域信息
            List<Map<String, Object>> prophageRegions = parseProphageRegions(taskOutputDir, baseName);
            
            // 找到指定的区域（注意类型转换：Map中的regionId是Integer类型）
            Optional<Map<String, Object>> targetRegion = prophageRegions.stream()
                    .filter(r -> {
                        Object id = r.get("regionId");
                        if (id instanceof Integer) {
                            return regionId.equals(((Integer) id).longValue());
                        } else if (id instanceof Long) {
                            return regionId.equals(id);
                        }
                        return false;
                    })
                    .findFirst();
            
            if (!targetRegion.isPresent()) {
                log.error("找不到原噬菌体区域 {}, 可用区域: {}", regionId, 
                        prophageRegions.stream()
                                .map(r -> r.get("regionId"))
                                .collect(Collectors.toList()));
                throw new RuntimeException("找不到指定的原噬菌体区域: " + regionId);
            }
            
            Map<String, Object> region = targetRegion.get();
            
            // 读取基因详细信息
            List<Map<String, Object>> genes = parseProphageGenes(taskOutputDir, baseName, 
                    (String) region.get("seqName"));
            region.put("genes", genes);
            
            // 读取序列信息（可选）
            String sequence = readProphageSequence(taskOutputDir, baseName, 
                    (String) region.get("seqName"));
            if (sequence != null) {
                region.put("sequence", sequence);
                region.put("sequenceLength", sequence.length());
            }
            
            log.info("成功加载原噬菌体区域详情: taskId={}, regionId={}, genes={}", 
                    taskId, regionId, genes.size());
            
            return region;
            
        } catch (Exception e) {
            log.error("读取原噬菌体详情失败: taskId={}, regionId={}", taskId, regionId, e);
            throw new RuntimeException("读取详情失败: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> getStatistics(Long taskId, Long userId) {
        // 验证任务
        AnalysisTask task = validateTask(taskId, userId);
        
        try {
            // 构建任务输出目录路径
            String taskOutputDir = Paths.get(outputDir, "task_" + taskId).toString();
            String baseName = getGenomeBaseName(taskOutputDir);
            
            // 读取原噬菌体区域信息
            List<Map<String, Object>> prophageRegions = parseProphageRegions(taskOutputDir, baseName);
            
            Map<String, Object> statistics = new HashMap<>();
            
            // 基本统计
            statistics.put("prophageCount", prophageRegions.size());
            
            // 原噬菌体统计
            if (!prophageRegions.isEmpty()) {
                // 长度分布
                List<Integer> lengths = prophageRegions.stream()
                        .map(r -> (Integer) r.get("length"))
                        .collect(Collectors.toList());
                statistics.put("prophageLengths", lengths);
                
                // 平均长度
                double avgLength = lengths.stream()
                        .mapToInt(Integer::intValue)
                        .average()
                        .orElse(0.0);
                statistics.put("avgProphageLength", (int) avgLength);
                
                // 总覆盖长度
                int totalLength = lengths.stream().mapToInt(Integer::intValue).sum();
                statistics.put("totalProphageLength", totalLength);
                
                // 得分分布
                List<Double> scores = prophageRegions.stream()
                        .map(r -> (Double) r.get("vVsCScore"))
                        .collect(Collectors.toList());
                statistics.put("prophageScores", scores);
                
                // 平均得分
                double avgScore = scores.stream()
                        .mapToDouble(Double::doubleValue)
                        .average()
                        .orElse(0.0);
                statistics.put("avgProphageScore", avgScore);
                
                // 基因数量分布
                List<Integer> geneCounts = prophageRegions.stream()
                        .map(r -> (Integer) r.get("nGenes"))
                        .collect(Collectors.toList());
                statistics.put("geneCountDistribution", geneCounts);
                
                // 总基因数
                int totalGenes = geneCounts.stream().mapToInt(Integer::intValue).sum();
                statistics.put("totalGenes", totalGenes);
            }
            
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
            exportData.put("genome", getGenomeVisualization(taskId, userId));
            
            // 统计数据
            exportData.put("statistics", getStatistics(taskId, userId));
            
            // 构建任务输出目录路径
            String taskOutputDir = Paths.get(outputDir, "task_" + taskId).toString();
            String baseName = getGenomeBaseName(taskOutputDir);
            
            // 读取原噬菌体区域并获取详细信息
            List<Map<String, Object>> prophageRegions = parseProphageRegions(taskOutputDir, baseName);
            List<Map<String, Object>> prophageDetails = new ArrayList<>();
            
            for (Map<String, Object> region : prophageRegions) {
                Map<String, Object> detail = new HashMap<>(region);
                
                // 读取该区域的基因信息
                String seqName = (String) region.get("seqName");
                List<Map<String, Object>> genes = parseProphageGenes(taskOutputDir, baseName, seqName);
                detail.put("genes", genes);
                
                prophageDetails.add(detail);
            }
            
            exportData.put("prophageDetails", prophageDetails);
            
            log.info("成功导出任务 {} 的完整数据", taskId);
            
            return exportData;
            
        } catch (Exception e) {
            log.error("导出数据失败: taskId={}", taskId, e);
            throw new RuntimeException("导出数据失败: " + e.getMessage(), e);
        }
    }

    /**
     * 验证任务并返回
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

    // ==================== genomad 文件解析方法 ====================
    
    /**
     * 获取基因组文件的基础名称
     */
    private String getGenomeBaseName(String taskOutputDir) throws IOException {
        Path dir = Paths.get(taskOutputDir);
        if (!Files.exists(dir)) {
            throw new RuntimeException("任务输出目录不存在: " + taskOutputDir);
        }
        
        // 查找 *_provirus.tsv 文件
        File[] provirusFiles = dir.toFile().listFiles((d, name) -> 
            name.endsWith("_find_proviruses") && new File(d, name).isDirectory());
        
        if (provirusFiles != null && provirusFiles.length > 0) {
            String dirName = provirusFiles[0].getName();
            // 从 "genome_xxx_find_proviruses" 提取 "genome_xxx"
            return dirName.replace("_find_proviruses", "");
        }
        
        throw new RuntimeException("找不到 find_proviruses 目录");
    }
    
    /**
     * 解析原噬菌体区域列表（从 provirus.tsv）
     */
    private List<Map<String, Object>> parseProphageRegions(String taskOutputDir, String baseName) 
            throws IOException {
        List<Map<String, Object>> regions = new ArrayList<>();
        
        Path provirusFile = Paths.get(taskOutputDir, baseName + "_find_proviruses", 
                baseName + "_provirus.tsv");
        
        if (!Files.exists(provirusFile)) {
            log.warn("原噬菌体文件不存在: {}", provirusFile);
            return regions;
        }
        
        try (BufferedReader reader = Files.newBufferedReader(provirusFile)) {
            String line;
            String[] headers = null;
            long regionId = 1L;  // 使用 long 类型以与 API 参数保持一致
            
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
                // 格式: seq_name, source_seq, start, end, length, n_genes, v_vs_c_score, in_seq_edge, integrases
                Map<String, Object> region = new HashMap<>();
                region.put("regionId", regionId++);
                region.put("seqName", values[0]);                           // 原噬菌体序列名
                region.put("sourceSeq", values[1]);                         // 宿主序列名
                region.put("start", Integer.parseInt(values[2]));           // 起始位置
                region.put("end", Integer.parseInt(values[3]));             // 结束位置
                region.put("length", Integer.parseInt(values[4]));          // 长度
                region.put("nGenes", Integer.parseInt(values[5]));          // 基因数
                region.put("vVsCScore", Double.parseDouble(values[6]));     // 病毒vs细胞得分
                region.put("inSeqEdge", values[7]);                         // 是否在序列边缘
                region.put("integrases", values[8]);                        // 整合酶
                
                // 判断完整性
                boolean inEdge = "True".equals(values[7]);
                int length = Integer.parseInt(values[4]);
                region.put("completeness", (!inEdge && length > 30000) ? "complete" : "incomplete");
                
                regions.add(region);
            }
        }
        
        log.info("解析到 {} 个原噬菌体区域", regions.size());
        return regions;
    }
    
    /**
     * 解析原噬菌体基因信息（从 provirus_genes.tsv）
     */
    private List<Map<String, Object>> parseProphageGenes(String taskOutputDir, String baseName, 
            String prophageSeqName) throws IOException {
        List<Map<String, Object>> genes = new ArrayList<>();
        
        Path genesFile = Paths.get(taskOutputDir, baseName + "_find_proviruses", 
                baseName + "_provirus_genes.tsv");
        
        if (!Files.exists(genesFile)) {
            log.warn("基因文件不存在: {}", genesFile);
            return genes;
        }
        
        try (BufferedReader reader = Files.newBufferedReader(genesFile)) {
            String line;
            String[] headers = null;
            
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty() || line.startsWith("#")) {
                    continue;
                }
                
                String[] values = line.split("\t");
                
                if (headers == null) {
                    headers = values;
                    continue;
                }
                
                // 只提取指定原噬菌体的基因
                String geneId = values[0];
                if (!geneId.startsWith(prophageSeqName)) {
                    continue;
                }
                
                // 格式: gene, start, end, length, strand, gc_content, genetic_code, rbs_motif, 
                //       marker, evalue, bitscore, uscg, taxid, [可能更多], taxname, [可能更多], annotation_accessions, annotation_description
                Map<String, Object> gene = new HashMap<>();
                
                // 基本必需字段
                gene.put("gene", values[0]);  // 基因ID
                gene.put("start", Integer.parseInt(values[1]));
                gene.put("end", Integer.parseInt(values[2]));
                gene.put("length", Integer.parseInt(values[3]));
                gene.put("strand", Integer.parseInt(values[4]));
                
                // 可选字段，使用安全访问
                gene.put("gcContent", values.length > 5 ? values[5] : "");
                gene.put("geneticCode", values.length > 6 ? values[6] : "");
                gene.put("rbsMotif", values.length > 7 ? values[7] : "");
                gene.put("marker", values.length > 8 ? values[8] : "");
                gene.put("evalue", values.length > 9 ? values[9] : "");
                gene.put("bitscore", values.length > 10 ? values[10] : "");
                gene.put("uscg", values.length > 11 ? values[11] : "");
                gene.put("taxid", values.length > 12 ? values[12] : "");
                
                // taxname 通常在更靠后的位置，尝试多个可能的索引
                // 根据示例数据，taxname 可能在索引15左右
                String taxname = "";
                for (int i = 13; i < Math.min(values.length, 18); i++) {
                    if (values[i] != null && !values[i].equals("NA") && 
                        !values[i].isEmpty() && !values[i].matches("\\d+")) {
                        taxname = values[i];
                        break;
                    }
                }
                gene.put("taxname", taxname);
                
                // annotation_accessions 和 annotation_description 通常在最后两列
                if (values.length > 1) {
                    String annotationAccessions = values[values.length - 2];
                    gene.put("annotationAccessions", annotationAccessions.equals("NA") ? "" : annotationAccessions);
                    
                    String annotationDescription = values[values.length - 1];
                    gene.put("annotationDescription", annotationDescription.equals("NA") ? "" : annotationDescription);
                } else {
                    gene.put("annotationAccessions", "");
                    gene.put("annotationDescription", "");
                }
                
                genes.add(gene);
            }
        }
        
        log.info("解析到原噬菌体 {} 的 {} 个基因", prophageSeqName, genes.size());
        return genes;
    }
    
    /**
     * 读取原噬菌体序列（从 provirus.fna）
     */
    private String readProphageSequence(String taskOutputDir, String baseName, String prophageSeqName) {
        try {
            Path seqFile = Paths.get(taskOutputDir, baseName + "_find_proviruses", 
                    baseName + "_provirus.fna");
            
            if (!Files.exists(seqFile)) {
                return null;
            }
            
            StringBuilder sequence = new StringBuilder();
            boolean inTarget = false;
            
            try (BufferedReader reader = Files.newBufferedReader(seqFile)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith(">")) {
                        // 检查是否是目标序列
                        String seqId = line.substring(1).split("\\s+")[0];
                        inTarget = seqId.equals(prophageSeqName);
                    } else if (inTarget) {
                        sequence.append(line.trim());
                    }
                }
            }
            
            return sequence.length() > 0 ? sequence.toString() : null;
            
        } catch (IOException e) {
            log.warn("读取序列失败: {}", prophageSeqName, e);
            return null;
        }
    }
    
    /**
     * 从分类信息中提取主要分类
     */
    private String extractTaxonomy(String taxonomy) {
        if (taxonomy == null || taxonomy.isEmpty()) {
            return "Unknown";
        }
        
        // 分类格式: Viruses;Duplodnaviria;Heunggongvirae;Uroviricota;Caudoviricetes;;
        String[] parts = taxonomy.split(";");
        
        // 返回最具体的分类（忽略空值）
        for (int i = parts.length - 1; i >= 0; i--) {
            if (!parts[i].isEmpty()) {
                return parts[i];
            }
        }
        
        return "Unknown";
    }
}
