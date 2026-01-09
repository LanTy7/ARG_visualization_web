package com.sy.controller;

import com.sy.service.impl.AnalysisTaskServiceImpl;
import com.sy.util.JwtUtil;
import com.sy.vo.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * MAG 文件上传和分析控制器
 * 处理 MAG (宏基因组组装基因组) 文件夹的上传和分析
 */
@Slf4j
@RestController
@RequestMapping("/api/mag")
@RequiredArgsConstructor
public class MagController {

    private final AnalysisTaskServiceImpl analysisTaskService;
    private final JwtUtil jwtUtil;

    @Value("${file.upload.mag-dir:./uploads/mag}")
    private String magUploadDir;

    // 允许的 FASTA 文件扩展名
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "fa", "fasta", "fna"
    );

    /**
     * 上传 MAG 文件夹（多个文件）
     * @param files MAG 文件数组
     * @param magName MAG 名称（可选）
     * @param description 描述（可选）
     * @param token JWT token
     * @return 上传结果和任务信息
     */
    @PostMapping("/upload")
    public Result<Map<String, Object>> uploadMag(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "magName", required = false) String magName,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "autoAnalyze", required = false, defaultValue = "true") Boolean autoAnalyze,
            @RequestHeader("Authorization") String token) {
        try {
            Long userId = jwtUtil.getUserIdFromToken(token.replace("Bearer ", ""));
            
            log.info("开始上传 MAG 文件夹: 用户={}, 文件数={}", userId, files.length);

            // 验证文件
            if (files == null || files.length == 0) {
                return Result.error("请选择要上传的文件");
            }

            // 过滤有效的 FASTA 文件
            List<MultipartFile> validFiles = new ArrayList<>();
            for (MultipartFile file : files) {
                String filename = file.getOriginalFilename();
                if (filename != null && !file.isEmpty()) {
                    String ext = getFileExtension(filename).toLowerCase();
                    if (ALLOWED_EXTENSIONS.contains(ext)) {
                        validFiles.add(file);
                    } else {
                        log.warn("跳过不支持的文件: {}", filename);
                    }
                }
            }

            if (validFiles.isEmpty()) {
                return Result.error("没有找到有效的 FASTA 文件（支持 .fa, .fasta, .fna）");
            }

            // 生成 MAG 名称
            if (magName == null || magName.trim().isEmpty()) {
                magName = "MAG_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            }

            // 创建 MAG 目录
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            String magDirName = "mag_" + userId + "_" + timestamp;
            Path magDir = Paths.get(magUploadDir, magDirName);
            Files.createDirectories(magDir);

            // 保存文件
            List<String> savedFiles = new ArrayList<>();
            long totalSize = 0;
            for (MultipartFile file : validFiles) {
                String originalFilename = file.getOriginalFilename();
                // 提取纯文件名（去掉路径前缀，处理文件夹上传时的相对路径）
                String filename = originalFilename;
                if (filename != null) {
                    // 处理 Windows 和 Unix 路径分隔符
                    int lastSlash = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
                    if (lastSlash >= 0) {
                        filename = filename.substring(lastSlash + 1);
                    }
                }
                Path filePath = magDir.resolve(filename);
                Files.write(filePath, file.getBytes());
                savedFiles.add(filename);
                totalSize += file.getSize();
                log.info("保存文件: {}", filePath);
            }

            log.info("MAG 文件上传完成: {} 个文件，总大小 {} KB", savedFiles.size(), totalSize / 1024);

            Map<String, Object> result = new HashMap<>();
            result.put("magName", magName);
            result.put("magDirPath", magDir.toAbsolutePath().toString());
            result.put("fileCount", savedFiles.size());
            result.put("files", savedFiles);
            result.put("totalSize", totalSize);

            // 如果自动分析，创建分析任务
            if (Boolean.TRUE.equals(autoAnalyze)) {
                Map<String, Object> params = new HashMap<>();
                params.put("description", description);
                
                Map<String, Object> taskInfo = analysisTaskService.createMagTask(
                        magDir.toAbsolutePath().toString(),
                        userId,
                        magName,
                        params
                );
                result.put("task", taskInfo);
                result.put("autoAnalyze", true);
                
                log.info("MAG 分析任务已创建: taskId={}", taskInfo.get("taskId"));
            } else {
                result.put("autoAnalyze", false);
            }

            return Result.success("MAG 上传成功", result);

        } catch (IOException e) {
            log.error("MAG 文件上传失败", e);
            return Result.error("文件上传失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("MAG 处理失败", e);
            return Result.error("处理失败: " + e.getMessage());
        }
    }

    /**
     * 为已上传的 MAG 创建分析任务
     * @param request 包含 magDirPath 和 magName 的请求体
     * @param token JWT token
     * @return 任务信息
     */
    @PostMapping("/analyze")
    public Result<Map<String, Object>> analyzeMag(
            @RequestBody Map<String, Object> request,
            @RequestHeader("Authorization") String token) {
        try {
            Long userId = jwtUtil.getUserIdFromToken(token.replace("Bearer ", ""));
            
            String magDirPath = (String) request.get("magDirPath");
            String magName = (String) request.get("magName");
            
            if (magDirPath == null || magDirPath.trim().isEmpty()) {
                return Result.error("请提供 MAG 目录路径");
            }
            
            // 验证目录存在
            Path magDir = Paths.get(magDirPath);
            if (!Files.exists(magDir) || !Files.isDirectory(magDir)) {
                return Result.error("MAG 目录不存在");
            }
            
            if (magName == null || magName.trim().isEmpty()) {
                magName = magDir.getFileName().toString();
            }
            
            Map<String, Object> params = new HashMap<>();
            if (request.containsKey("description")) {
                params.put("description", request.get("description"));
            }
            
            Map<String, Object> taskInfo = analysisTaskService.createMagTask(
                    magDirPath,
                    userId,
                    magName,
                    params
            );
            
            return Result.success("MAG 分析任务已创建", taskInfo);
            
        } catch (Exception e) {
            log.error("创建 MAG 分析任务失败", e);
            return Result.error("创建任务失败: " + e.getMessage());
        }
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < filename.length() - 1) {
            return filename.substring(lastDotIndex + 1);
        }
        return "";
    }
}
