package com.sy.service.impl;

import com.sy.service.DockerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Docker 服务实现 - ARG 抗性基因检测
 * 调用 Docker 容器执行抗性基因识别和分类
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

    // ARG 配置
    @Value("${docker.arg.image-name:arg-bilstm:gpu}")
    private String argImageName;

    @Value("${docker.arg.model-path:/app/models}")
    private String argModelPath;

    @Value("${docker.arg.input-mount:/input}")
    private String argInputMount;

    @Value("${docker.arg.output-mount:/output}")
    private String argOutputMount;

    @Value("${docker.arg.use-gpu:true}")
    private boolean useGpu;

    @Value("${analysis.timeout:3600}")
    private int timeoutSeconds;

    // 保存正在运行的任务进程（taskId -> Process）
    private final Map<Long, Process> runningProcesses = new ConcurrentHashMap<>();

    /**
     * 运行抗性基因检测（ARG）
     */
    @Override
    public Map<String, Object> runProphageDetection(String inputFilePath, String outputDir, Map<String, Object> params) {
        return runArgDetection(null, inputFilePath, outputDir, params);
    }

    /**
     * 运行抗性基因检测（带任务ID，用于取消功能）
     */
    public Map<String, Object> runArgDetection(Long taskId, String inputFilePath, String outputDir, Map<String, Object> params) {
        log.info("开始运行抗性基因检测: inputFile={}, outputDir={}", inputFilePath, outputDir);

        if (!dockerEnabled) {
            log.warn("Docker 未启用，返回模拟数据");
            return generateMockResult();
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

            // 5. 解析输出文件
            Map<String, Object> analysisResult = visualizationService.parseArgOutput(outputDir);

            log.info("分析完成，识别到 {} 个抗性基因", analysisResult.getOrDefault("argCount", 0));

            return analysisResult;

        } catch (Exception e) {
            log.error("ARG Docker 分析失败", e);
            throw new RuntimeException("分析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 构建 ARG 命令
     * docker run --rm --gpus all -v {inputDir}:/input:ro -v {outputDir}:/output arg-bilstm:gpu end-to-end /input/{file} /output /app/models
     */
    private String buildArgCommand(String inputFilePath, String outputDir, Map<String, Object> params) {
        File inputFile = new File(inputFilePath);
        String inputDir = inputFile.getParent();
        String inputFileName = inputFile.getName();

        String normalizedInputDir = normalizePath(inputDir);
        String normalizedOutputDir = normalizePath(outputDir);

        StringBuilder cmd = new StringBuilder();

        // 添加命令前缀（如果配置了，如 "wsl "）
        if (commandPrefix != null && !commandPrefix.trim().isEmpty()) {
            cmd.append(commandPrefix.trim()).append(" ");
        }

        // docker run --rm
        cmd.append("docker run --rm ");

        // GPU 支持
        if (useGpu) {
            cmd.append("--gpus all ");
        }

        // 挂载输入目录（只读）
        cmd.append("-v ").append(normalizedInputDir).append(":").append(argInputMount).append(":ro ");

        // 挂载输出目录（读写）
        cmd.append("-v ").append(normalizedOutputDir).append(":").append(argOutputMount).append(" ");

        // 镜像名
        cmd.append(argImageName).append(" ");

        // ARG 命令：end-to-end /input/{file} /output /app/models
        cmd.append("end-to-end ");
        cmd.append(argInputMount).append("/").append(inputFileName).append(" ");
        cmd.append(argOutputMount).append(" ");
        cmd.append(argModelPath);

        return cmd.toString();
    }

    /**
     * 规范化路径
     */
    private String normalizePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return path;
        }

        // 如果已经是 Linux 格式的路径，直接返回
        if (path.startsWith("/")) {
            return path;
        }

        // 如果是相对路径，先转换为绝对路径
        File file = new File(path);
        String absolutePath = file.getAbsolutePath().replace("\\", "/");

        // Windows 路径转换为 WSL 路径格式
        if (absolutePath.contains(":") && absolutePath.length() > 2) {
            String drive = absolutePath.substring(0, 1).toLowerCase();
            String restPath = absolutePath.substring(2);
            return "/mnt/" + drive + restPath;
        }

        return absolutePath;
    }

    /**
     * 执行命令
     */
    private ProcessResult executeCommand(String command, Long taskId) throws Exception {
        ProcessBuilder pb = new ProcessBuilder();

        // 根据命令前缀判断执行方式
        if (commandPrefix != null && !commandPrefix.trim().isEmpty() &&
            commandPrefix.trim().toLowerCase().contains("wsl")) {
            pb.command("cmd.exe", "/c", command);
        } else {
            pb.command("bash", "-c", command);
        }

        pb.redirectErrorStream(false);
        Process process = pb.start();

        // 保存进程引用
        if (taskId != null) {
            runningProcesses.put(taskId, process);
            log.info("保存任务进程引用: taskId={}", taskId);
        }

        // 读取输出
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();

        Thread stdoutThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
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
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
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

        // 等待进程结束
        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("任务超时（超过 " + timeoutSeconds + " 秒）");
        }

        stdoutThread.join(5000);
        stderrThread.join(5000);

        int exitCode = process.exitValue();

        // 移除进程引用
        if (taskId != null) {
            runningProcesses.remove(taskId);
        }

        return new ProcessResult(exitCode, stdout.toString(), stderr.toString());
    }

    /**
     * 生成模拟结果（用于测试）
     */
    private Map<String, Object> generateMockResult() {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> argResults = new ArrayList<>();

        Map<String, Object> arg1 = new HashMap<>();
        arg1.put("id", "seq_001");
        arg1.put("isArg", true);
        arg1.put("predProb", 0.95);
        arg1.put("argClass", "beta-lactam");
        arg1.put("classProb", 0.88);
        argResults.add(arg1);

        Map<String, Object> arg2 = new HashMap<>();
        arg2.put("id", "seq_002");
        arg2.put("isArg", true);
        arg2.put("predProb", 0.87);
        arg2.put("argClass", "tetracycline");
        arg2.put("classProb", 0.82);
        argResults.add(arg2);

        Map<String, Object> arg3 = new HashMap<>();
        arg3.put("id", "seq_003");
        arg3.put("isArg", false);
        arg3.put("predProb", 0.15);
        arg3.put("argClass", null);
        arg3.put("classProb", null);
        argResults.add(arg3);

        result.put("argCount", 2);
        result.put("argResults", argResults);

        return result;
    }

    @Override
    public Map<String, Object> checkContainerStatus(String containerId) {
        Map<String, Object> status = new HashMap<>();
        status.put("running", false);
        status.put("message", "Container status check not implemented");
        return status;
    }

    @Override
    public void stopContainer(String containerId) {
        log.info("停止容器: {}", containerId);
    }

    @Override
    public void cancelAnalysis(Long taskId) {
        Process process = runningProcesses.get(taskId);
        if (process != null) {
            try {
                log.info("正在终止任务进程: taskId={}", taskId);

                if (process.isAlive()) {
                    process.destroy();
                    boolean terminated = process.waitFor(5, TimeUnit.SECONDS);
                    if (!terminated) {
                        process.destroyForcibly();
                    }
                }

                runningProcesses.remove(taskId);
                log.info("任务进程已终止: taskId={}", taskId);

            } catch (Exception e) {
                log.error("终止任务进程失败: taskId={}", taskId, e);
                runningProcesses.remove(taskId);
            }
        } else {
            log.warn("未找到任务进程: taskId={}", taskId);
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
