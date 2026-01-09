package com.sy.service.impl;

import com.sy.mapper.AnalysisTaskMapper;
import com.sy.pojo.AnalysisTask;
import com.sy.service.DockerService;
import com.sy.service.MagAnalysisService;
import com.sy.service.ProdigalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * MAG 分析服务实现
 * 编排 MAG 文件夹的完整分析流程
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MagAnalysisServiceImpl implements MagAnalysisService {

    private final ProdigalService prodigalService;
    private final DockerServiceImpl dockerService;
    private final AnalysisTaskMapper analysisTaskMapper;

    @Value("${analysis.output-dir:./genome_outputs}")
    private String outputBaseDir;

    // FASTA 文件扩展名
    private static final List<String> FASTA_EXTENSIONS = Arrays.asList(
            ".fa", ".fasta", ".fna"
    );

    @Override
    public Map<String, Object> analyzeMag(Long taskId, Path magDir, Path outputDir, Map<String, Object> params) {
        log.info("开始 MAG 分析: taskId={}, magDir={}", taskId, magDir);

        try {
            // 1. 扫描 FASTA 文件
            List<Path> fastaFiles = scanFastaFiles(magDir);
            if (fastaFiles.isEmpty()) {
                throw new RuntimeException("MAG 文件夹中没有找到 FASTA 文件: " + magDir);
            }
            log.info("找到 {} 个 FASTA 文件", fastaFiles.size());

            // 创建输出子目录
            Path prodigalOutputDir = outputDir.resolve("prodigal");
            Path argOutputDir = outputDir.resolve("arg");
            Files.createDirectories(prodigalOutputDir);
            Files.createDirectories(argOutputDir);

            // ============================================
            // 阶段 1：Prodigal 预处理 (并行)
            // ============================================
            updateProgress(taskId, 1, 0, "开始 Prodigal 预处理...");
            updateTaskStatus(taskId, "PREPROCESSING");

            log.info("阶段 1/2: Prodigal 预处理");
            List<Path> faaFiles = prodigalService.processFilesParallel(fastaFiles, prodigalOutputDir);

            if (faaFiles.isEmpty()) {
                throw new RuntimeException("Prodigal 预处理未产生任何输出文件");
            }

            updateProgress(taskId, 1, 100, "Prodigal 预处理完成，共 " + faaFiles.size() + " 个文件");
            log.info("Prodigal 预处理完成，共 {} 个 .faa 文件", faaFiles.size());

            // ============================================
            // 合并 .faa 文件
            // ============================================
            Path mergedFaa = prodigalOutputDir.resolve("merged.faa");
            prodigalService.mergeFiles(faaFiles, mergedFaa);
            log.info("文件合并完成: {}", mergedFaa);

            // ============================================
            // 阶段 2：ARG 分析
            // ============================================
            updateProgress(taskId, 2, 0, "开始 ARG 分析...");
            updateTaskStatus(taskId, "ANALYZING");

            log.info("阶段 2/2: ARG 分析");
            Map<String, Object> argResult = dockerService.runArgDetection(
                    taskId,
                    mergedFaa.toAbsolutePath().toString(),
                    argOutputDir.toAbsolutePath().toString(),
                    params
            );

            updateProgress(taskId, 2, 100, "ARG 分析完成");
            log.info("ARG 分析完成");

            // 构建返回结果
            Map<String, Object> result = new HashMap<>();
            result.put("fastaFileCount", fastaFiles.size());
            result.put("faaFileCount", faaFiles.size());
            result.put("prodigalOutputDir", prodigalOutputDir.toString());
            result.put("argOutputDir", argOutputDir.toString());
            result.put("mergedFile", mergedFaa.toString());
            result.putAll(argResult);

            return result;

        } catch (Exception e) {
            log.error("MAG 分析失败: taskId={}", taskId, e);
            throw new RuntimeException("MAG 分析失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Path> scanFastaFiles(Path magDir) {
        log.info("扫描 FASTA 文件: {}", magDir);

        if (!Files.exists(magDir)) {
            throw new RuntimeException("目录不存在: " + magDir);
        }

        if (!Files.isDirectory(magDir)) {
            throw new RuntimeException("不是目录: " + magDir);
        }

        try (Stream<Path> stream = Files.walk(magDir, 1)) {
            List<Path> fastaFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String fileName = path.getFileName().toString().toLowerCase();
                        return FASTA_EXTENSIONS.stream().anyMatch(fileName::endsWith);
                    })
                    .sorted()
                    .collect(Collectors.toList());

            log.info("找到 {} 个 FASTA 文件", fastaFiles.size());
            fastaFiles.forEach(f -> log.debug("  - {}", f.getFileName()));

            return fastaFiles;

        } catch (IOException e) {
            log.error("扫描目录失败: {}", magDir, e);
            throw new RuntimeException("扫描目录失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void updateProgress(Long taskId, int stage, int progress, String message) {
        log.info("任务进度: taskId={}, 阶段={}/2, 进度={}%, 消息={}", taskId, stage, progress, message);

        try {
            AnalysisTask task = analysisTaskMapper.selectById(taskId);
            if (task != null) {
                // 计算总体进度：阶段1占50%，阶段2占50%
                int totalProgress;
                if (stage == 1) {
                    totalProgress = progress / 2;  // 0-50%
                } else {
                    totalProgress = 50 + progress / 2;  // 50-100%
                }

                task.setProgress(totalProgress);
                // 使用 errorMessage 字段存储进度消息（可选）
                // task.setErrorMessage("阶段 " + stage + "/2: " + message);
                analysisTaskMapper.updateById(task);
            }
        } catch (Exception e) {
            log.warn("更新任务进度失败: taskId={}", taskId, e);
        }
    }

    /**
     * 更新任务状态
     */
    private void updateTaskStatus(Long taskId, String status) {
        try {
            AnalysisTask task = analysisTaskMapper.selectById(taskId);
            if (task != null) {
                task.setStatus(status);
                if ("PREPROCESSING".equals(status) || "ANALYZING".equals(status)) {
                    if (task.getStartedAt() == null) {
                        task.setStartedAt(LocalDateTime.now());
                    }
                }
                analysisTaskMapper.updateById(task);
            }
        } catch (Exception e) {
            log.warn("更新任务状态失败: taskId={}", taskId, e);
        }
    }
}
