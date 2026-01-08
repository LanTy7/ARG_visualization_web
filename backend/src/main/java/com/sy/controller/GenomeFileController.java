package com.sy.controller;

import com.sy.service.GenomeFileService;
import com.sy.util.JwtUtil;
import com.sy.vo.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 基因文件上传控制器
 * 用于上传 FASTA、GenBank 等基因组文件
 */
@Slf4j
@RestController
@RequestMapping("/api/genome")
@RequiredArgsConstructor
public class GenomeFileController {

    private final GenomeFileService genomeFileService;
    private final JwtUtil jwtUtil;

    /**
     * 上传基因组文件（FASTA, GenBank, GFF等）
     * 支持Galaxy风格的上传选项
     * @param file 基因组文件
     * @param fileType 文件类型（auto-detect, fasta, genbank, gff等）
     * @param reference 参考基因组（可选）
     * @param description 文件描述
     * @param isPublic 是否公开
     * @param token JWT token
     * @return 文件ID和基本信息
     */
    @PostMapping("/upload")
    public Result<Map<String, Object>> uploadGenomeFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "fileType", required = false, defaultValue = "auto-detect") String fileType,
            @RequestParam(value = "reference", required = false) String reference,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "isPublic", required = false, defaultValue = "false") Boolean isPublic,
            @RequestParam(value = "metadata", required = false) String metadata,
            @RequestHeader("Authorization") String token) {
        try {
            log.info("开始上传基因组文件: {}, 类型: {}", file.getOriginalFilename(), fileType);
            
            // 从token中获取用户ID
            Long userId = jwtUtil.getUserIdFromToken(token.replace("Bearer ", ""));
            
            // 构建上传选项
            java.util.Map<String, Object> options = new java.util.HashMap<>();
            options.put("fileType", fileType);
            options.put("reference", reference);
            options.put("description", description);
            options.put("isPublic", isPublic);
            options.put("metadata", metadata);
            
            // 上传并保存文件信息
            Map<String, Object> result = genomeFileService.uploadGenomeFile(file, userId, options);
            
            log.info("基因组文件上传成功，文件ID: {}", result.get("fileId"));
            return Result.success("文件上传成功", result);
        } catch (Exception e) {
            log.error("基因组文件上传失败", e);
            return Result.error("文件上传失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取支持的文件类型列表
     * @return 文件类型选项
     */
    @GetMapping("/file-types")
    public Result<List<Map<String, String>>> getFileTypes() {
        List<Map<String, String>> fileTypes = List.of(
                Map.of("value", "auto-detect", "label", "Auto-detect", "description", "自动检测文件类型"),
                Map.of("value", "fasta", "label", "FASTA", "description", "FASTA格式基因组序列"),
                Map.of("value", "genbank", "label", "GenBank", "description", "GenBank格式基因组注释"),
                Map.of("value", "gff", "label", "GFF/GFF3", "description", "基因特征格式文件"),
                Map.of("value", "embl", "label", "EMBL", "description", "EMBL格式基因组文件"),
                Map.of("value", "fastq", "label", "FASTQ", "description", "带质量值的序列文件")
        );
        return Result.success(fileTypes);
    }
    
    /**
     * 获取参考基因组列表
     * @return 参考基因组选项
     */
    @GetMapping("/references")
    public Result<List<Map<String, String>>> getReferences() {
        List<Map<String, String>> references = List.of(
                Map.of("value", "unspecified", "label", "Unspecified", "description", "未指定参考基因组"),
                Map.of("value", "hg38", "label", "Human (GRCh38/hg38)", "description", "人类参考基因组hg38"),
                Map.of("value", "hg19", "label", "Human (GRCh37/hg19)", "description", "人类参考基因组hg19"),
                Map.of("value", "mm10", "label", "Mouse (GRCm38/mm10)", "description", "小鼠参考基因组mm10"),
                Map.of("value", "dm6", "label", "D. melanogaster (dm6)", "description", "果蝇参考基因组")
        );
        return Result.success(references);
    }

    /**
     * 获取用户的基因组文件列表
     * @param token JWT token
     * @return 文件列表
     */
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> getUserGenomeFiles(
            @RequestHeader("Authorization") String token) {
        try {
            Long userId = jwtUtil.getUserIdFromToken(token.replace("Bearer ", ""));
            List<Map<String, Object>> files = genomeFileService.getUserGenomeFiles(userId);
            return Result.success(files);
        } catch (Exception e) {
            log.error("获取文件列表失败", e);
            return Result.error("获取文件列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取文件详细信息
     * @param fileId 文件ID
     * @param token JWT token
     * @return 文件详情
     */
    @GetMapping("/{fileId}")
    public Result<Map<String, Object>> getGenomeFileInfo(
            @PathVariable Long fileId,
            @RequestHeader("Authorization") String token) {
        try {
            Long userId = jwtUtil.getUserIdFromToken(token.replace("Bearer ", ""));
            Map<String, Object> fileInfo = genomeFileService.getFileInfo(fileId, userId);
            return Result.success(fileInfo);
        } catch (Exception e) {
            log.error("获取文件信息失败", e);
            return Result.error("获取文件信息失败: " + e.getMessage());
        }
    }

    /**
     * 删除基因组文件
     * @param fileId 文件ID
     * @param token JWT token
     * @return 删除结果
     */
    @DeleteMapping("/{fileId}")
    public Result<Void> deleteGenomeFile(
            @PathVariable Long fileId,
            @RequestHeader("Authorization") String token) {
        try {
            Long userId = jwtUtil.getUserIdFromToken(token.replace("Bearer ", ""));
            genomeFileService.deleteFile(fileId, userId);
            return Result.success();
        } catch (Exception e) {
            log.error("删除文件失败", e);
            return Result.error("删除文件失败: " + e.getMessage());
        }
    }
}

