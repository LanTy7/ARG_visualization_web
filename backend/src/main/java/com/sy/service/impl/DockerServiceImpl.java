package com.sy.service.impl;

import com.sy.service.DockerService;
import com.sy.service.impl.VisualizationServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Docker 服务实现 - genomad 集成
 * 使用长期运行的 Docker 容器 + docker exec 执行分析
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DockerServiceImpl implements DockerService {
    
    private final VisualizationServiceImpl visualizationService;

    @Value("${docker.enabled:false}")
    private boolean dockerEnabled;

    @Value("${docker.command-prefix:}")
    private String commandPrefix;

    @Value("${docker.genomad.container-name:genomad-worker}")
    private String containerName;

    @Value("${docker.genomad.input-mount:/input}")
    private String inputMount;

    @Value("${docker.genomad.output-mount:/output}")
    private String outputMount;

    @Value("${docker.genomad.database-path:/genomad_db}")
    private String databasePath;

    @Value("${docker.genomad.image-name:antoniopcamargo/genomad:latest}")
    private String imageName;

    // ARG 配置
    @Value("${docker.arg.image-name:arg-predictor:latest}")
    private String argImageName;

    @Value("${docker.arg.model-path:/app/models}")
    private String argModelPath;

    @Value("${docker.arg.input-mount:/input}")
    private String argInputMount;

    @Value("${docker.arg.output-mount:/output}")
    private String argOutputMount;

    @Value("${analysis.timeout:3600}")
    private int timeoutSeconds;

    // 保存正在运行的任务进程（taskId -> Process）
    private final Map<Long, Process> runningProcesses = new ConcurrentHashMap<>();

    @Override
    public Map<String, Object> runProphageDetection(
            String inputFilePath, String outputDir, Map<String, Object> params) {
        return runProphageDetection(null, inputFilePath, outputDir, params);
    }

    /**
     * 运行原噬菌体识别（带任务ID，用于取消功能）
     */
    public Map<String, Object> runProphageDetection(
            Long taskId, String inputFilePath, String outputDir, Map<String, Object> params) {

        log.info("开始运行原噬菌体识别: inputFile={}, outputDir={}", inputFilePath, outputDir);

        if (!dockerEnabled) {
            log.warn("Docker 未启用，返回模拟数据");
            return generateMockResult();
        }

        try {
            // 1. 创建输出目录（确保隔离）
            File outputDirFile = new File(outputDir);
            if (!outputDirFile.exists()) {
                outputDirFile.mkdirs();
                log.info("创建输出目录: {}", outputDir);
            }

            // 2. 构建 genomad 命令
            String command = buildGenomadCommand(inputFilePath, outputDir, params);
            log.info("执行命令: {}", command);

            // 3. 执行命令（如果提供了taskId，保存进程引用以便后续取消）
            ProcessResult result = executeCommand(command, taskId);

            // 4. 检查执行结果
            if (result.exitCode != 0) {
                log.error("genomad 执行失败，退出码: {}", result.exitCode);
                log.error("stderr: {}", result.stderr);
                throw new RuntimeException("genomad 执行失败: " + result.stderr);
            }

            log.info("genomad 执行成功");
            log.debug("stdout: {}", result.stdout);

            // 5. 解析输出文件
            Map<String, Object> analysisResult = parseGenomadOutput(outputDir, inputFilePath);

            log.info("分析完成，识别到 {} 个原噬菌体",
                    analysisResult.getOrDefault("prophageCount", 0));

            return analysisResult;

        } catch (Exception e) {
            log.error("Docker 分析失败", e);
            throw new RuntimeException("分析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 构建 genomad 命令
     * 关键：使用 taskId 作为输出目录名，确保隔离
     */
    private String buildGenomadCommand(
            String inputFilePath, String outputDir, Map<String, Object> params) {

        // 规范化路径（确保路径格式正确）
        // databasePath 应该是数据库文件夹的路径，如 /home/zhaoshuy/genomad_docker/genomad_db
        // 需要挂载父目录，这样容器内路径为 /genomad_db/genomad_db
        String normalizedDbPath = normalizePath(databasePath);
        File dbPathFile = new File(normalizedDbPath);
        String dbParentDir = dbPathFile.getParent(); // 父目录：/home/zhaoshuy/genomad_docker
        String dbDirName = dbPathFile.getName();     // 数据库目录名：genomad_db
        String normalizedDbParentDir = normalizePath(dbParentDir);

        // 获取输入文件的父目录和文件名
        File inputFile = new File(inputFilePath);
        String inputDir = inputFile.getParent();
        String normalizedInputDir = normalizePath(inputDir);
        String containerInputFile = inputMount + "/" + inputFile.getName();

        // 获取输出目录的父目录和子目录名
        File outputDirFile = new File(outputDir);
        String outputParentDir = outputDirFile.getParent();
        String outputSubDir = outputDirFile.getName();
        String normalizedOutputParentDir = normalizePath(outputParentDir);
        String containerOutputDir = outputMount + "/" + outputSubDir;

        // 容器内挂载点：挂载父目录到 /genomad_db
        String containerDbMount = "/genomad_db";
        // 命令中使用的数据库路径：/genomad_db/genomad_db
        String containerDb = containerDbMount + "/" + dbDirName;

        StringBuilder cmd = new StringBuilder();

        // 添加命令前缀（如果配置了，如 "wsl "）
        if (commandPrefix != null && !commandPrefix.trim().isEmpty()) {
            cmd.append(commandPrefix.trim()).append(" ");
        }

        // 使用官方推荐的方式：docker run --rm（每次运行新容器）
        // 使用当前用户的 UID:GID，确保生成的文件权限正确
        cmd.append("docker run --rm --user $(id -u):$(id -g) ");

        // 挂载输入目录（只读）
        cmd.append("-v ").append(normalizedInputDir).append(":").append(inputMount).append(":ro ");

        // 挂载输出父目录（读写）
        cmd.append("-v ").append(normalizedOutputParentDir).append(":").append(outputMount).append(" ");

        // 挂载数据库父目录（读写，genomad 需要写入 version.txt）
        // 挂载父目录，这样容器内路径为 /genomad_db/genomad_db
        cmd.append("-v ").append(normalizedDbParentDir).append(":").append(containerDbMount).append(" ");

        // 镜像名
        cmd.append(imageName).append(" ");

        // genomad 命令
        cmd.append("end-to-end ");
        cmd.append(containerInputFile).append(" ");
        cmd.append(containerOutputDir).append(" ");
        cmd.append(containerDb);

        // 添加可选参数
        if (params != null) {
            if (params.containsKey("min_score")) {
                cmd.append(" --min-score ").append(params.get("min_score"));
            }
            if (params.containsKey("min_length")) {
                cmd.append(" --min-length ").append(params.get("min_length"));
            }
            if (params.containsKey("splits")) {
                cmd.append(" --splits ").append(params.get("splits"));
            }
        }
        
        // 默认添加 --splits 8 以避免内存不足（OOM）
        // 如果 params 中没有指定 splits，则添加默认值
        if (params == null || !params.containsKey("splits")) {
            cmd.append(" --splits 8");
        }

        return cmd.toString();
    }

    /**
     * 规范化路径（统一路径格式）
     * - 如果路径已经是 Linux 格式（以 / 开头），直接返回
     * - 如果是 Windows 路径（包含 :），转换为绝对路径并统一分隔符
     * - 如果是相对路径，转换为绝对路径
     */
    private String normalizePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return path;
        }

        // 如果已经是 Linux/WSL 格式的路径（以 / 开头），直接返回
        if (path.startsWith("/")) {
            return path;
        }

        // 如果是相对路径，先转换为绝对路径
        File file = new File(path);
        String absolutePath = file.getAbsolutePath();

        // 统一使用正斜杠作为路径分隔符
        absolutePath = absolutePath.replace("\\", "/");

        // 如果是 Windows 路径（包含盘符），需要转换为 WSL 路径格式
        // 但这里我们假设配置文件中已经配置了正确的路径格式
        // 如果用户配置的是 Windows 路径，需要在配置时转换为 WSL 路径
        // 如果用户配置的是 Linux 路径，直接使用
        if (absolutePath.contains(":") && absolutePath.length() > 2) {
            // Windows 路径格式：C:/path -> /mnt/c/path
            String drive = absolutePath.substring(0, 1).toLowerCase();
            String restPath = absolutePath.substring(2);
            return "/mnt/" + drive + restPath;
        }

        return absolutePath;
    }

    /**
     * 执行命令
     */
    private ProcessResult executeCommand(String command) throws Exception {
        return executeCommand(command, null);
    }

    /**
     * 执行命令（带任务ID，用于进程管理）
     */
    private ProcessResult executeCommand(String command, Long taskId) throws Exception {
        ProcessBuilder pb = new ProcessBuilder();

        // 根据命令前缀判断执行方式
        // 如果命令前缀包含 "wsl"，说明需要通过 cmd.exe 执行
        // 否则直接使用 bash 执行
        if (commandPrefix != null && !commandPrefix.trim().isEmpty() && 
            commandPrefix.trim().toLowerCase().contains("wsl")) {
            // WSL 模式：通过 cmd.exe 执行
            pb.command("cmd.exe", "/c", command);
        } else {
            // Linux 集群模式：直接使用 bash 执行
            pb.command("bash", "-c", command);
        }

        // 合并错误输出和标准输出
        pb.redirectErrorStream(false);

        Process process = pb.start();

        // 如果提供了taskId，保存进程引用以便后续取消
        if (taskId != null) {
            runningProcesses.put(taskId, process);
            log.info("保存任务进程引用: taskId={}, pid={}", taskId, getProcessId(process));
        }

        // 读取输出（防止缓冲区满导致阻塞）
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();

        Thread stdoutThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stdout.append(line).append("\n");
                    log.debug("[stdout] {}", line);
                }
            } catch (IOException e) {
                log.error("读取 stdout 失败", e);
            }
        });

        Thread stderrThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stderr.append(line).append("\n");
                    log.warn("[stderr] {}", line);
                }
            } catch (IOException e) {
                log.error("读取 stderr 失败", e);
            }
        });

        stdoutThread.start();
        stderrThread.start();

        // 等待进程结束（带超时）
        boolean finished = process.waitFor(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);

        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("任务超时（超过 " + timeoutSeconds + " 秒）");
        }

        // 等待输出线程结束
        stdoutThread.join(5000);
        stderrThread.join(5000);

        int exitCode = process.exitValue();

        // 进程结束后，从Map中移除
        if (taskId != null) {
            runningProcesses.remove(taskId);
            log.info("任务进程已结束，移除引用: taskId={}", taskId);
        }

        return new ProcessResult(exitCode, stdout.toString(), stderr.toString());
    }

    /**
     * 获取进程ID（跨平台）
     */
    private Long getProcessId(Process process) {
        try {
            // 使用反射获取进程ID
            if (process.getClass().getName().equals("java.lang.ProcessImpl")) {
                Field pidField = process.getClass().getDeclaredField("pid");
                pidField.setAccessible(true);
                return pidField.getLong(process);
            }
        } catch (Exception e) {
            log.debug("无法获取进程ID", e);
        }
        return null;
    }

    /**
     * 解析 genomad 输出
     * genomad 输出结构：
     * {outputDir}/
     *   └─ {filename}_find_proviruses/
     *       ├─ {filename}_provirus.tsv  (原噬菌体区域)
     *       └─ {filename}_provirus.fna
     */
    private Map<String, Object> parseGenomadOutput(String outputDir, String inputFilePath) {
        Map<String, Object> result = new HashMap<>();

        try {
            String fileName = new File(inputFilePath).getName();
            // 去掉扩展名
            String baseName = fileName.replaceAll("\\.(fna|fasta|fa)$", "");

            // genomad 原噬菌体输出文件路径
            File provirusDir = new File(outputDir, baseName + "_find_proviruses");
            File provirusFile = new File(provirusDir, baseName + "_provirus.tsv");

            log.info("查找原噬菌体输出文件: {}", provirusFile.getAbsolutePath());

            if (!provirusFile.exists()) {
                log.warn("未找到原噬菌体输出文件，尝试查找其他可能的文件");
                // 尝试列出实际生成的文件
                if (provirusDir.exists()) {
                    File[] files = provirusDir.listFiles();
                    if (files != null) {
                        log.info("find_proviruses 目录中的文件:");
                        for (File f : files) {
                            log.info("  - {}", f.getName());
                        }
                        // 查找任何 provirus.tsv 文件
                        for (File f : files) {
                            if (f.getName().endsWith("_provirus.tsv")) {
                                provirusFile = f;
                                log.info("找到备用文件: {}", f.getName());
                                break;
                            }
                        }
                    }
                }
            }

            if (provirusFile.exists()) {
                result = parseProvirusTSV(provirusFile);
            } else {
                log.warn("未找到 provirus.tsv，返回空结果");
                result.put("genomeLength", 0L);
                result.put("prophageRegions", new ArrayList<>());
            }

        } catch (Exception e) {
            log.error("解析输出失败", e);
            throw new RuntimeException("解析输出失败: " + e.getMessage(), e);
        }

        return result;
    }

    /**
     * 解析 virus_summary.tsv 文件
     */
    private Map<String, Object> parseVirusSummaryTSV(File tsvFile) throws IOException {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> prophageRegions = new ArrayList<>();

        long maxEndPos = 0;

        try (BufferedReader reader = new BufferedReader(new FileReader(tsvFile))) {
            String line;
            String[] headers = null;
            int lineNum = 0;

            while ((line = reader.readLine()) != null) {
                lineNum++;

                // 跳过注释和空行
                if (line.startsWith("#") || line.trim().isEmpty()) {
                    continue;
                }

                String[] values = line.split("\t");

                // 第一行是表头
                if (headers == null) {
                    headers = values;
                    log.debug("TSV 表头: {}", Arrays.toString(headers));
                    continue;
                }

                try {
                    // 解析每一行数据
                    // genomad 输出格式: seq_name, start, end, length, topology, n_genes, genetic_code, virus_score, ...
                    Map<String, Object> prophage = new HashMap<>();

                    // 提取关键字段（根据实际 genomad 输出调整索引）
                    // 使用 start/end 而不是 startPos/endPos，与 AnalysisTaskServiceImpl 保持一致
                    prophage.put("regionId", prophageRegions.size() + 1);                  // 区域ID（从1开始）
                    prophage.put("seqName", values[0]);                                    // 序列名称
                    prophage.put("start", Integer.parseInt(values[1]));                    // 起始位置
                    prophage.put("end", Integer.parseInt(values[2]));                      // 结束位置
                    prophage.put("length", Integer.parseInt(values[3]));                   // 长度
                    prophage.put("score", values.length > 7 ? Double.parseDouble(values[7]) : 0.0);  // 病毒得分
                    int geneCount = values.length > 5 ? Integer.parseInt(values[5]) : 0;
                    prophage.put("nGenes", geneCount);      // 基因数（genomad 输出）
                    prophage.put("geneCount", geneCount);   // 基因数（AnalysisTaskServiceImpl 期望的字段名）

                    // 计算置信度（0-1）
                    double score = prophage.get("score") != null ? (Double)prophage.get("score") : 0.0;
                    prophage.put("confidence", Math.min(score / 10.0, 1.0)); // 假设满分10

                    // 完整性（简化判断）
                    int length = (Integer) prophage.get("length");
                    prophage.put("completeness", length > 30000 ? "complete" : "incomplete");

                    prophageRegions.add(prophage);

                    // 更新最大位置（使用 end 而不是 endPos）
                    int endPos = (Integer) prophage.get("end");
                    if (endPos > maxEndPos) {
                        maxEndPos = endPos;
                    }

                } catch (Exception e) {
                    log.warn("解析第 {} 行失败: {}", lineNum, e.getMessage());
                }
            }
        }

        result.put("genomeLength", maxEndPos);
        result.put("prophageRegions", prophageRegions);

        log.info("成功解析 {} 个原噬菌体区域", prophageRegions.size());

        return result;
    }

    /**
     * 解析 provirus.tsv 文件（原噬菌体区域）
     * 格式：seq_name, source_seq, start, end, length, n_genes, v_vs_c_score, in_seq_edge, integrases
     */
    private Map<String, Object> parseProvirusTSV(File tsvFile) throws IOException {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> prophageRegions = new ArrayList<>();

        long maxEndPos = 0;

        // 解析原噬菌体区域
        try (BufferedReader reader = new BufferedReader(new FileReader(tsvFile))) {
            String line;
            String[] headers = null;
            int lineNum = 0;

            while ((line = reader.readLine()) != null) {
                lineNum++;

                // 跳过注释和空行
                if (line.startsWith("#") || line.trim().isEmpty()) {
                    continue;
                }

                String[] values = line.split("\t");

                // 第一行是表头
                if (headers == null) {
                    headers = values;
                    log.debug("原噬菌体 TSV 表头: {}", Arrays.toString(headers));
                    continue;
                }

                try {
                    // 解析原噬菌体数据
                    // 格式: seq_name(0), source_seq(1), start(2), end(3), length(4), n_genes(5), v_vs_c_score(6), in_seq_edge(7), integrases(8)
                    Map<String, Object> prophage = new HashMap<>();

                    prophage.put("regionId", prophageRegions.size() + 1);  // 区域ID（从1开始）
                    String seqName = values[0];
                    prophage.put("seqName", seqName);                      // 原噬菌体序列名
                    prophage.put("sourceSeq", values[1]);                  // 宿主序列名
                    prophage.put("start", Integer.parseInt(values[2]));    // 起始位置
                    prophage.put("end", Integer.parseInt(values[3]));      // 结束位置
                    prophage.put("length", Integer.parseInt(values[4]));   // 长度

                    int geneCount = Integer.parseInt(values[5]);
                    prophage.put("nGenes", geneCount);
                    prophage.put("geneCount", geneCount);

                    // v_vs_c_score 作为得分
                    double score = Double.parseDouble(values[6]);
                    prophage.put("score", score);

                    // 计算置信度（v_vs_c_score 通常在 0-100 范围）
                    prophage.put("confidence", Math.min(score / 100.0, 1.0));

                    // 完整性判断
                    int length = Integer.parseInt(values[4]);
                    boolean inEdge = "True".equals(values[7]);
                    prophage.put("completeness", (!inEdge && length > 30000) ? "complete" : "incomplete");

                    prophageRegions.add(prophage);

                    // 更新最大位置
                    int endPos = Integer.parseInt(values[3]);
                    if (endPos > maxEndPos) {
                        maxEndPos = endPos;
                    }

                } catch (Exception e) {
                    log.warn("解析第 {} 行失败: {}", lineNum, e.getMessage());
                }
            }
        }

        // 解析基因信息并关联到原噬菌体
        File genesFile = new File(tsvFile.getParent(), tsvFile.getName().replace("_provirus.tsv", "_provirus_genes.tsv"));
        if (genesFile.exists()) {
            Map<String, List<Map<String, Object>>> genesByProphage = parseGenesFile(genesFile);

            // 将基因信息添加到对应的原噬菌体
            for (Map<String, Object> prophage : prophageRegions) {
                String seqName = (String) prophage.get("seqName");
                String prophagePrefix = seqName.split("\\|provirus_")[0] + "|provirus_" +
                        prophage.get("start") + "_" + prophage.get("end");

                List<Map<String, Object>> genes = genesByProphage.get(prophagePrefix);
                if (genes != null) {
                    prophage.put("genes", genes);
                    log.debug("为原噬菌体 {} 关联了 {} 个基因", seqName, genes.size());
                }
            }
        } else {
            log.warn("未找到基因文件: {}", genesFile.getAbsolutePath());
        }

        result.put("genomeLength", maxEndPos);
        result.put("prophageRegions", prophageRegions);

        log.info("成功解析 {} 个原噬菌体区域", prophageRegions.size());

        return result;
    }

    /**
     * 解析基因文件
     * 格式：gene, start, end, length, strand, gc_content, genetic_code, rbs_motif, marker, evalue, bitscore, uscg, taxid, taxname, annotation_accessions, annotation_description
     */
    private Map<String, List<Map<String, Object>>> parseGenesFile(File genesFile) throws IOException {
        Map<String, List<Map<String, Object>>> genesByProphage = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(genesFile))) {
            String line;
            String[] headers = null;
            int lineNum = 0;

            while ((line = reader.readLine()) != null) {
                lineNum++;

                if (line.startsWith("#") || line.trim().isEmpty()) {
                    continue;
                }

                String[] values = line.split("\t");

                if (headers == null) {
                    headers = values;
                    log.debug("基因 TSV 表头: {}", Arrays.toString(headers));
                    continue;
                }

                try {
                    // 解析基因数据
                    String geneId = values[0];  // 如: Unbinned_3894_contig-100_0|provirus_3_21536_1

                    // 提取原噬菌体前缀（去掉最后的基因编号）
                    String prophagePrefix = geneId.substring(0, geneId.lastIndexOf('_'));

                    Map<String, Object> gene = new HashMap<>();
                    gene.put("geneId", geneId);
                    gene.put("start", Integer.parseInt(values[1]));
                    gene.put("end", Integer.parseInt(values[2]));
                    gene.put("length", Integer.parseInt(values[3]));
                    gene.put("strand", Integer.parseInt(values[4]));
                    gene.put("gcContent", Double.parseDouble(values[5]));

                    // 功能注释（annotation_description）
                    String annotation = values.length > 15 ? values[15] : "NA";
                    if ("NA".equals(annotation) || annotation.trim().isEmpty()) {
                        annotation = "无注释";
                    }
                    gene.put("annotation", annotation);

                    // 添加到对应原噬菌体的基因列表
                    genesByProphage.computeIfAbsent(prophagePrefix, k -> new ArrayList<>()).add(gene);

                } catch (Exception e) {
                    log.warn("解析基因第 {} 行失败: {}", lineNum, e.getMessage());
                }
            }
        }

        log.info("成功解析 {} 个原噬菌体的基因信息", genesByProphage.size());
        return genesByProphage;
    }

    /**
     * 生成模拟结果（用于测试）
     */
    private Map<String, Object> generateMockResult() {
        Map<String, Object> result = new HashMap<>();

        // 基因组信息
        result.put("genomeLength", 4500000L);  // 使用 Long 类型
        result.put("gcContent", 51.5);

        // 原噬菌体区域（使用与真实数据一致的字段名）
        List<Map<String, Object>> prophageRegions = new ArrayList<>();

        Map<String, Object> region1 = new HashMap<>();
        region1.put("regionId", 1);
        region1.put("seqName", "scaffold_1");
        region1.put("start", 125000);
        region1.put("end", 175000);
        region1.put("length", 50000);
        region1.put("score", 8.5);
        region1.put("confidence", 0.85);
        region1.put("completeness", "complete");
        region1.put("nGenes", 45);
        region1.put("geneCount", 45);
        prophageRegions.add(region1);

        Map<String, Object> region2 = new HashMap<>();
        region2.put("regionId", 2);
        region2.put("seqName", "scaffold_1");
        region2.put("start", 850000);
        region2.put("end", 895000);
        region2.put("length", 45000);
        region2.put("score", 7.8);
        region2.put("confidence", 0.78);
        region2.put("completeness", "complete");
        region2.put("nGenes", 42);
        region2.put("geneCount", 42);
        prophageRegions.add(region2);

        Map<String, Object> region3 = new HashMap<>();
        region3.put("regionId", 3);
        region3.put("seqName", "scaffold_1");
        region3.put("start", 3200000);
        region3.put("end", 3230000);
        region3.put("length", 30000);
        region3.put("score", 6.5);
        region3.put("confidence", 0.65);
        region3.put("completeness", "incomplete");
        region3.put("nGenes", 28);
        region3.put("geneCount", 28);
        prophageRegions.add(region3);

        result.put("prophageRegions", prophageRegions);

        return result;
    }

    /**
     * 检查容器状态
     */
    @Override
    public Map<String, Object> checkContainerStatus(String containerId) {
        Map<String, Object> status = new HashMap<>();

        try {
            // 构建 docker inspect 命令
            StringBuilder cmd = new StringBuilder();
            if (commandPrefix != null && !commandPrefix.trim().isEmpty()) {
                cmd.append(commandPrefix.trim()).append(" ");
            }
            cmd.append("docker inspect ").append(containerId);

            ProcessResult result = executeCommand(cmd.toString());

            if (result.exitCode == 0) {
                status.put("running", true);
                status.put("output", result.stdout);
            } else {
                status.put("running", false);
                status.put("error", result.stderr);
            }
        } catch (Exception e) {
            log.error("检查容器状态失败: {}", containerId, e);
            status.put("running", false);
            status.put("error", e.getMessage());
        }

        return status;
    }

    /**
     * 停止容器
     */
    @Override
    public void stopContainer(String containerId) {
        try {
            // 构建 docker stop 命令
            StringBuilder cmd = new StringBuilder();
            if (commandPrefix != null && !commandPrefix.trim().isEmpty()) {
                cmd.append(commandPrefix.trim()).append(" ");
            }
            cmd.append("docker stop ").append(containerId);

            ProcessResult result = executeCommand(cmd.toString());

            if (result.exitCode == 0) {
                log.info("容器已停止: {}", containerId);
            } else {
                log.warn("停止容器失败: {}, 错误: {}", containerId, result.stderr);
            }
        } catch (Exception e) {
            log.error("停止容器失败: {}", containerId, e);
            throw new RuntimeException("停止容器失败: " + e.getMessage());
        }
    }

    /**
     * 运行抗性基因检测（ARG）
     * 命令格式：docker run --rm -v {inputDir}:/data arg-predictor end-to-end /data/{inputFile} /data/{outputSubDir} /app/models
     */
    public Map<String, Object> runArgDetection(
            Long taskId, String inputFilePath, String outputDir, Map<String, Object> params) {

        log.info("开始运行抗性基因检测: inputFile={}, outputDir={}", inputFilePath, outputDir);

        if (!dockerEnabled) {
            log.warn("Docker 未启用，返回模拟数据");
            return generateArgMockResult();
        }

        try {
            // 1. 创建输出目录
            File outputDirFile = new File(outputDir);
            if (!outputDirFile.exists()) {
                outputDirFile.mkdirs();
                log.info("创建输出目录: {}", outputDir);
            }

            // 2. 构建 ARG 命令
            String command = buildArgCommand(inputFilePath, outputDir, params);
            log.info("执行命令: {}", command);

            // 3. 执行命令
            ProcessResult result = executeCommand(command, taskId);

            // 4. 检查执行结果
            if (result.exitCode != 0) {
                log.error("ARG 执行失败，退出码: {}", result.exitCode);
                log.error("stderr: {}", result.stderr);
                throw new RuntimeException("ARG 执行失败: " + result.stderr);
            }

            log.info("ARG 执行成功");
            log.debug("stdout: {}", result.stdout);

            // 5. 解析输出文件（使用 VisualizationService 统一解析）
            Map<String, Object> analysisResult = visualizationService.parseArgOutput(outputDir);

            log.info("分析完成，识别到 {} 个抗性基因",
                    analysisResult.getOrDefault("argCount", 0));

            return analysisResult;

        } catch (Exception e) {
            log.error("ARG Docker 分析失败", e);
            throw new RuntimeException("分析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 构建 ARG 命令
     * docker run --rm -v {inputDir}:{inputMount}:ro -v {outputDir}:{outputMount} arg-predictor end-to-end {inputMount}/{inputFile} {outputMount} {modelPath}
     */
    private String buildArgCommand(
            String inputFilePath, String outputDir, Map<String, Object> params) {

        // 获取输入文件的父目录和文件名
        File inputFile = new File(inputFilePath);
        String inputDir = inputFile.getParent();
        String normalizedInputDir = normalizePath(inputDir);
        String inputFileName = inputFile.getName();

        // 规范化输出目录路径
        String normalizedOutputDir = normalizePath(outputDir);

        StringBuilder cmd = new StringBuilder();

        // 添加命令前缀（如果配置了，如 "wsl "）
        if (commandPrefix != null && !commandPrefix.trim().isEmpty()) {
            cmd.append(commandPrefix.trim()).append(" ");
        }

        // docker run --gpus all
        cmd.append("docker run --gpus all ");

        // 挂载输入目录（只读）
        cmd.append("-v ").append(normalizedInputDir).append(":").append(argInputMount).append(":ro ");

        // 挂载输出目录（读写）
        cmd.append("-v ").append(normalizedOutputDir).append(":").append(argOutputMount).append(" ");

        // 镜像名
        cmd.append(argImageName).append(" ");

        // ARG 命令：end-to-end {inputMount}/{inputFile} {outputMount} {modelPath}
        cmd.append("end-to-end ");
        cmd.append(argInputMount).append("/").append(inputFileName).append(" ");
        cmd.append(argOutputMount).append(" ");
        cmd.append(argModelPath);

        return cmd.toString();
    }


    /**
     * 生成 ARG 模拟结果（用于测试）
     */
    private Map<String, Object> generateArgMockResult() {
        Map<String, Object> result = new HashMap<>();

        List<Map<String, Object>> argResults = new ArrayList<>();

        Map<String, Object> arg1 = new HashMap<>();
        arg1.put("id", 1);
        arg1.put("sequence_id", "seq_001");
        arg1.put("arg_type", "beta-lactamase");
        arg1.put("drug_class", "beta-lactam");
        arg1.put("confidence", 0.95);
        arg1.put("start", 100);
        arg1.put("end", 500);
        argResults.add(arg1);

        Map<String, Object> arg2 = new HashMap<>();
        arg2.put("id", 2);
        arg2.put("sequence_id", "seq_002");
        arg2.put("arg_type", "tetracycline resistance");
        arg2.put("drug_class", "tetracycline");
        arg2.put("confidence", 0.88);
        arg2.put("start", 200);
        arg2.put("end", 600);
        argResults.add(arg2);

        result.put("argCount", argResults.size());
        result.put("argResults", argResults);

        return result;
    }

    /**
     * 终止正在运行的分析进程
     */
    @Override
    public void cancelAnalysis(Long taskId) {
        Process process = runningProcesses.get(taskId);
        if (process != null) {
            try {
                log.info("正在终止任务进程: taskId={}", taskId);

                // 尝试优雅终止
                if (process.isAlive()) {
                    process.destroy();
                    log.info("已发送终止信号给进程: taskId={}", taskId);

                    // 等待一段时间，如果还没结束，强制终止
                    boolean terminated = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
                    if (!terminated) {
                        log.warn("进程未在5秒内终止，强制终止: taskId={}", taskId);
                        process.destroyForcibly();
                    }
                }

                // 从Map中移除
                runningProcesses.remove(taskId);
                log.info("任务进程已终止: taskId={}", taskId);

            } catch (Exception e) {
                log.error("终止任务进程失败: taskId={}", taskId, e);
                // 即使出错也尝试从Map中移除
                runningProcesses.remove(taskId);
            }
        } else {
            log.warn("未找到任务进程: taskId={}", taskId);

            // 如果找不到进程，尝试通过命令前缀查找并终止
            // 这适用于docker run --rm的情况，因为容器ID可能已经变化
            try {
                // 如果配置了命令前缀（如 WSL），尝试查找并终止相关的docker进程
                if (commandPrefix != null && !commandPrefix.trim().isEmpty()) {
                    String killCommand = commandPrefix.trim() + " bash -c \"pkill -f 'docker run.*genomad' || true\"";
                    ProcessBuilder killPb = new ProcessBuilder();
                    if (commandPrefix.trim().toLowerCase().contains("wsl")) {
                        killPb.command("cmd.exe", "/c", killCommand);
                    } else {
                        killPb.command("bash", "-c", killCommand);
                    }
                    Process killProcess = killPb.start();
                    killProcess.waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
                    log.info("已尝试通过命令前缀终止相关docker进程");
                }
            } catch (Exception e) {
                log.debug("终止进程失败（可能进程已结束）", e);
            }
        }
    }

    /**
     * 进程执行结果
     */
    private static class ProcessResult {
        int exitCode;
        String stdout;
        String stderr;

        ProcessResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }
    }
}
