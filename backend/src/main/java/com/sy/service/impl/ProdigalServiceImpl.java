package com.sy.service.impl;

import com.sy.service.ProdigalService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Prodigal 服务实现
 * 调用 Docker 容器进行基因预测
 */
@Slf4j
@Service
public class ProdigalServiceImpl implements ProdigalService {

    @Value("${docker.prodigal.image-name:prodigal:latest}")
    private String prodigalImageName;

    @Value("${docker.prodigal.timeout:600}")
    private int timeoutSeconds;

    @Value("${docker.prodigal.parallel-threads:8}")
    private int parallelThreads;

    @Value("${docker.prodigal.use-meta-mode:true}")
    private boolean useMetaMode;

    private ExecutorService executorService;

    /**
     * 获取线程池（懒加载）
     */
    private ExecutorService getExecutorService() {
        if (executorService == null || executorService.isShutdown()) {
            executorService = Executors.newFixedThreadPool(parallelThreads);
        }
        return executorService;
    }

    @Override
    public Path processFile(Path inputFile, Path outputDir) {
        String fileName = inputFile.getFileName().toString();
        String baseName = fileName.replaceAll("\\.(fa|fasta|fna)$", "");
        
        Path outputFaa = outputDir.resolve(baseName + ".faa");
        Path outputGff = outputDir.resolve(baseName + ".gff");

        log.info("开始 Prodigal 处理: {} -> {}", inputFile, outputFaa);

        try {
            // 确保输出目录存在
            Files.createDirectories(outputDir);

            // 构建 Docker 命令
            String command = buildProdigalCommand(inputFile, outputFaa, outputGff);
            log.debug("执行命令: {}", command);

            // 执行命令
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
            pb.redirectErrorStream(false);
            Process process = pb.start();

            // 读取输出
            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();

            Thread stdoutThread = new Thread(() -> readStream(process.getInputStream(), stdout));
            Thread stderrThread = new Thread(() -> readStream(process.getErrorStream(), stderr));

            stdoutThread.start();
            stderrThread.start();

            // 等待完成
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("Prodigal 处理超时（超过 " + timeoutSeconds + " 秒）");
            }

            stdoutThread.join(5000);
            stderrThread.join(5000);

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.error("Prodigal 执行失败，退出码: {}", exitCode);
                log.error("stderr: {}", stderr);
                throw new RuntimeException("Prodigal 执行失败: " + stderr);
            }

            // 检查输出文件是否存在
            if (!Files.exists(outputFaa)) {
                throw new RuntimeException("Prodigal 输出文件不存在: " + outputFaa);
            }

            log.info("Prodigal 处理完成: {}", outputFaa);
            return outputFaa;

        } catch (Exception e) {
            log.error("Prodigal 处理失败: {}", inputFile, e);
            throw new RuntimeException("Prodigal 处理失败: " + e.getMessage(), e);
        }
    }

    @Override
    public CompletableFuture<Path> processFileAsync(Path inputFile, Path outputDir) {
        return CompletableFuture.supplyAsync(() -> processFile(inputFile, outputDir), getExecutorService());
    }

    @Override
    public List<Path> processFilesParallel(List<Path> inputFiles, Path outputDir) {
        log.info("开始并行处理 {} 个文件，并行度: {}", inputFiles.size(), parallelThreads);

        List<CompletableFuture<Path>> futures = new ArrayList<>();
        
        for (Path inputFile : inputFiles) {
            futures.add(processFileAsync(inputFile, outputDir));
        }

        // 等待所有任务完成
        List<Path> results = new ArrayList<>();
        for (int i = 0; i < futures.size(); i++) {
            try {
                Path result = futures.get(i).get(timeoutSeconds * 2, TimeUnit.SECONDS);
                results.add(result);
                log.info("文件处理完成 ({}/{}): {}", i + 1, inputFiles.size(), result.getFileName());
            } catch (Exception e) {
                log.error("文件处理失败: {}", inputFiles.get(i), e);
                throw new RuntimeException("文件处理失败: " + inputFiles.get(i), e);
            }
        }

        log.info("所有文件处理完成，共 {} 个", results.size());
        return results;
    }

    @Override
    public Path mergeFiles(List<Path> faaFiles, Path outputFile) {
        log.info("开始合并 {} 个文件到: {}", faaFiles.size(), outputFile);

        try {
            // 确保输出目录存在
            Files.createDirectories(outputFile.getParent());

            try (BufferedWriter writer = Files.newBufferedWriter(outputFile)) {
                for (Path faaFile : faaFiles) {
                    if (!Files.exists(faaFile)) {
                        log.warn("文件不存在，跳过: {}", faaFile);
                        continue;
                    }

                    // 获取源文件名作为前缀
                    String sourceFileName = faaFile.getFileName().toString().replace(".faa", "");

                    try (BufferedReader reader = Files.newBufferedReader(faaFile)) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            // 如果是序列头，添加源文件名前缀以便追溯
                            if (line.startsWith(">")) {
                                // 格式: >sourceFile__originalId
                                String originalId = line.substring(1);
                                writer.write(">" + sourceFileName + "__" + originalId);
                            } else {
                                writer.write(line);
                            }
                            writer.newLine();
                        }
                    }
                }
            }

            long fileSize = Files.size(outputFile);
            log.info("文件合并完成: {}, 大小: {} KB", outputFile, fileSize / 1024);

            return outputFile;

        } catch (Exception e) {
            log.error("文件合并失败", e);
            throw new RuntimeException("文件合并失败: " + e.getMessage(), e);
        }
    }

    /**
     * 构建 Prodigal Docker 命令
     */
    private String buildProdigalCommand(Path inputFile, Path outputFaa, Path outputGff) {
        Path inputDir = inputFile.getParent();
        Path outputDir = outputFaa.getParent();
        String inputFileName = inputFile.getFileName().toString();
        String outputFaaName = outputFaa.getFileName().toString();
        String outputGffName = outputGff.getFileName().toString();

        StringBuilder cmd = new StringBuilder();

        // docker run --rm
        cmd.append("docker run --rm ");

        // 挂载输入目录（只读）
        cmd.append("-v ").append(inputDir.toAbsolutePath()).append(":/input:ro ");

        // 挂载输出目录（读写）
        cmd.append("-v ").append(outputDir.toAbsolutePath()).append(":/output ");

        // 镜像名
        cmd.append(prodigalImageName).append(" ");

        // Prodigal 参数
        cmd.append("-i /input/").append(inputFileName).append(" ");
        cmd.append("-a /output/").append(outputFaaName).append(" ");
        cmd.append("-o /output/").append(outputGffName).append(" ");
        cmd.append("-f gff ");

        // 宏基因组模式（对于短序列更好）
        if (useMetaMode) {
            cmd.append("-p meta");
        }

        return cmd.toString();
    }

    /**
     * 读取流
     */
    private void readStream(InputStream is, StringBuilder sb) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } catch (IOException e) {
            log.error("读取流失败", e);
        }
    }
}
