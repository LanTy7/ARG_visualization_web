package com.sy.controller;

import com.sy.mapper.AnalysisTaskMapper;
import com.sy.pojo.AnalysisTask;
import com.sy.util.JwtUtil;
import com.sy.vo.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 文件下载控制器
 * 提供分析结果文件的下载功能
 */
@Slf4j
@RestController
@RequestMapping("/api/download")
@RequiredArgsConstructor
public class DownloadController {

    private final AnalysisTaskMapper analysisTaskMapper;
    private final JwtUtil jwtUtil;

    /**
     * 获取任务的可下载文件列表
     */
    @GetMapping("/files/{taskId}")
    public Result<Map<String, Object>> getDownloadableFiles(
            @PathVariable Long taskId,
            @RequestHeader("Authorization") String token) {
        try {
            Long userId = jwtUtil.getUserIdFromToken(token.replace("Bearer ", ""));
            AnalysisTask task = validateTask(taskId, userId);

            Map<String, Object> result = new HashMap<>();
            result.put("taskId", taskId);
            result.put("taskType", task.getTaskType());
            result.put("isMagTask", "MAG".equals(task.getTaskType()));

            List<Map<String, Object>> files = new ArrayList<>();
            String outputDir = task.getOutputDir();

            if (outputDir != null && Files.exists(Paths.get(outputDir))) {
                // ARG 预测结果
                Path argFile = findArgResultFile(outputDir);
                if (argFile != null && Files.exists(argFile)) {
                    files.add(createFileInfo("arg_predictions", "ARG 预测结果", 
                            argFile.getFileName().toString(), Files.size(argFile), "tsv"));
                }

                // MAG 任务特有的文件
                if ("MAG".equals(task.getTaskType())) {
                    Path prodigalDir = Paths.get(outputDir, "prodigal");
                    
                    // 合并后的序列文件
                    Path mergedFile = prodigalDir.resolve("merged.faa");
                    if (Files.exists(mergedFile)) {
                        files.add(createFileInfo("merged_faa", "合并后的蛋白质序列", 
                                "merged.faa", Files.size(mergedFile), "faa"));
                    }

                    // Prodigal 结果目录（多个 .faa 文件）
                    if (Files.exists(prodigalDir)) {
                        long prodigalSize = 0;
                        int prodigalCount = 0;
                        try (DirectoryStream<Path> stream = Files.newDirectoryStream(prodigalDir, "*.faa")) {
                            for (Path file : stream) {
                                if (!file.getFileName().toString().equals("merged.faa")) {
                                    prodigalSize += Files.size(file);
                                    prodigalCount++;
                                }
                            }
                        }
                        if (prodigalCount > 0) {
                            files.add(createFileInfo("prodigal_results", 
                                    "Prodigal 预测结果 (" + prodigalCount + " 个文件)", 
                                    "prodigal_results.zip", prodigalSize, "zip"));
                        }
                    }
                }
            }

            result.put("files", files);
            return Result.success(result);

        } catch (Exception e) {
            log.error("获取可下载文件列表失败", e);
            return Result.error(e.getMessage());
        }
    }

    /**
     * 下载 ARG 预测结果
     */
    @GetMapping("/arg/{taskId}")
    public ResponseEntity<Resource> downloadArgResult(
            @PathVariable Long taskId,
            @RequestHeader("Authorization") String token) {
        try {
            Long userId = jwtUtil.getUserIdFromToken(token.replace("Bearer ", ""));
            AnalysisTask task = validateTask(taskId, userId);

            Path argFile = findArgResultFile(task.getOutputDir());
            if (argFile == null || !Files.exists(argFile)) {
                return ResponseEntity.notFound().build();
            }

            String filename = "task_" + taskId + "_arg_predictions.tsv";
            return createFileResponse(argFile, filename);

        } catch (Exception e) {
            log.error("下载 ARG 结果失败", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 下载合并后的蛋白质序列
     */
    @GetMapping("/merged/{taskId}")
    public ResponseEntity<Resource> downloadMergedFaa(
            @PathVariable Long taskId,
            @RequestHeader("Authorization") String token) {
        try {
            Long userId = jwtUtil.getUserIdFromToken(token.replace("Bearer ", ""));
            AnalysisTask task = validateTask(taskId, userId);

            if (!"MAG".equals(task.getTaskType())) {
                return ResponseEntity.badRequest().build();
            }

            Path mergedFile = Paths.get(task.getOutputDir(), "prodigal", "merged.faa");
            if (!Files.exists(mergedFile)) {
                return ResponseEntity.notFound().build();
            }

            String filename = "task_" + taskId + "_merged.faa";
            return createFileResponse(mergedFile, filename);

        } catch (Exception e) {
            log.error("下载合并序列失败", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 下载 Prodigal 结果（ZIP 打包）
     */
    @GetMapping("/prodigal/{taskId}")
    public ResponseEntity<Resource> downloadProdigalResults(
            @PathVariable Long taskId,
            @RequestHeader("Authorization") String token) {
        try {
            Long userId = jwtUtil.getUserIdFromToken(token.replace("Bearer ", ""));
            AnalysisTask task = validateTask(taskId, userId);

            if (!"MAG".equals(task.getTaskType())) {
                return ResponseEntity.badRequest().build();
            }

            Path prodigalDir = Paths.get(task.getOutputDir(), "prodigal");
            if (!Files.exists(prodigalDir)) {
                return ResponseEntity.notFound().build();
            }

            // 创建临时 ZIP 文件
            Path zipFile = Files.createTempFile("prodigal_results_" + taskId + "_", ".zip");
            
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile.toFile()))) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(prodigalDir, "*.faa")) {
                    for (Path file : stream) {
                        // 排除 merged.faa
                        if (!file.getFileName().toString().equals("merged.faa")) {
                            ZipEntry entry = new ZipEntry(file.getFileName().toString());
                            zos.putNextEntry(entry);
                            Files.copy(file, zos);
                            zos.closeEntry();
                        }
                    }
                }
                
                // 添加 GFF 文件（如果存在）
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(prodigalDir, "*.gff")) {
                    for (Path file : stream) {
                        ZipEntry entry = new ZipEntry(file.getFileName().toString());
                        zos.putNextEntry(entry);
                        Files.copy(file, zos);
                        zos.closeEntry();
                    }
                }
            }

            String filename = "task_" + taskId + "_prodigal_results.zip";
            Resource resource = new FileSystemResource(zipFile.toFile()) {
                @Override
                public InputStream getInputStream() throws IOException {
                    return new FileInputStream(getFile()) {
                        @Override
                        public void close() throws IOException {
                            super.close();
                            // 下载完成后删除临时文件
                            Files.deleteIfExists(zipFile);
                        }
                    };
                }
            };

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(Files.size(zipFile))
                    .body(resource);

        } catch (Exception e) {
            log.error("下载 Prodigal 结果失败", e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 下载全部结果（ZIP 打包）
     */
    @GetMapping("/all/{taskId}")
    public ResponseEntity<Resource> downloadAllResults(
            @PathVariable Long taskId,
            @RequestHeader("Authorization") String token) {
        try {
            Long userId = jwtUtil.getUserIdFromToken(token.replace("Bearer ", ""));
            AnalysisTask task = validateTask(taskId, userId);

            String outputDir = task.getOutputDir();
            if (outputDir == null || !Files.exists(Paths.get(outputDir))) {
                return ResponseEntity.notFound().build();
            }

            // 创建临时 ZIP 文件
            Path zipFile = Files.createTempFile("all_results_" + taskId + "_", ".zip");

            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile.toFile()))) {
                // 添加 ARG 结果
                Path argFile = findArgResultFile(outputDir);
                if (argFile != null && Files.exists(argFile)) {
                    ZipEntry entry = new ZipEntry("arg_predictions.tsv");
                    zos.putNextEntry(entry);
                    Files.copy(argFile, zos);
                    zos.closeEntry();
                }

                // MAG 任务：添加 Prodigal 结果
                if ("MAG".equals(task.getTaskType())) {
                    Path prodigalDir = Paths.get(outputDir, "prodigal");
                    if (Files.exists(prodigalDir)) {
                        // 添加所有 .faa 文件
                        try (DirectoryStream<Path> stream = Files.newDirectoryStream(prodigalDir, "*.faa")) {
                            for (Path file : stream) {
                                ZipEntry entry = new ZipEntry("prodigal/" + file.getFileName().toString());
                                zos.putNextEntry(entry);
                                Files.copy(file, zos);
                                zos.closeEntry();
                            }
                        }
                        // 添加 .gff 文件
                        try (DirectoryStream<Path> stream = Files.newDirectoryStream(prodigalDir, "*.gff")) {
                            for (Path file : stream) {
                                ZipEntry entry = new ZipEntry("prodigal/" + file.getFileName().toString());
                                zos.putNextEntry(entry);
                                Files.copy(file, zos);
                                zos.closeEntry();
                            }
                        }
                    }
                }
            }

            String filename = "task_" + taskId + "_all_results.zip";
            Resource resource = new FileSystemResource(zipFile.toFile()) {
                @Override
                public InputStream getInputStream() throws IOException {
                    return new FileInputStream(getFile()) {
                        @Override
                        public void close() throws IOException {
                            super.close();
                            Files.deleteIfExists(zipFile);
                        }
                    };
                }
            };

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(Files.size(zipFile))
                    .body(resource);

        } catch (Exception e) {
            log.error("下载全部结果失败", e);
            return ResponseEntity.badRequest().build();
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

    /**
     * 查找 ARG 结果文件
     */
    private Path findArgResultFile(String outputDir) {
        // 先检查 arg 子目录
        Path argDir = Paths.get(outputDir, "arg");
        if (Files.exists(argDir)) {
            Path file = argDir.resolve("all_predictions.tsv");
            if (Files.exists(file)) return file;
            file = argDir.resolve("arg_predictions.tsv");
            if (Files.exists(file)) return file;
        }
        
        // 再检查根目录
        Path file = Paths.get(outputDir, "all_predictions.tsv");
        if (Files.exists(file)) return file;
        file = Paths.get(outputDir, "arg_predictions.tsv");
        if (Files.exists(file)) return file;
        
        return null;
    }

    /**
     * 创建文件下载响应
     */
    private ResponseEntity<Resource> createFileResponse(Path file, String filename) throws IOException {
        Resource resource = new FileSystemResource(file.toFile());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(Files.size(file))
                .body(resource);
    }

    /**
     * 创建文件信息 Map
     */
    private Map<String, Object> createFileInfo(String key, String name, String filename, long size, String type) {
        Map<String, Object> info = new HashMap<>();
        info.put("key", key);
        info.put("name", name);
        info.put("filename", filename);
        info.put("size", size);
        info.put("sizeFormatted", formatFileSize(size));
        info.put("type", type);
        return info;
    }

    /**
     * 格式化文件大小
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
