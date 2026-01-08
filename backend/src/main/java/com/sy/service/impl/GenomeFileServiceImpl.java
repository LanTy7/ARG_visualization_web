package com.sy.service.impl;

import com.sy.mapper.AnalysisTaskMapper;
import com.sy.mapper.GenomeFileMapper;
import com.sy.pojo.AnalysisTask;
import com.sy.pojo.GenomeFile;
import com.sy.service.GenomeFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 基因组文件服务实现（使用数据库）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GenomeFileServiceImpl implements GenomeFileService {

    private final GenomeFileMapper genomeFileMapper;
    private final AnalysisTaskMapper analysisTaskMapper;

    // 文件上传目录（从配置文件读取，如果没有则使用默认值）
    @Value("${file.upload.genome-dir:./uploads/genome}")
    private String uploadDir;

    // 允许的基因文件格式
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "fasta", "fa", "fna", "faa",  // FASTA formats
            "gbk", "gb", "genbank",        // GenBank formats
            "gff", "gff3",                 // GFF formats
            "txt"                          // Text format
    );

    @Override
    public Map<String, Object> uploadGenomeFile(MultipartFile file, Long userId, Map<String, Object> options) {
        // 提取选项
        String description = (String) options.getOrDefault("description", "");
        String fileType = (String) options.getOrDefault("fileType", "auto-detect");
        Boolean isPublic = (Boolean) options.getOrDefault("isPublic", false);
        try {
            // 验证文件
            validateFile(file);
            
            // 计算文件MD5
            String md5Hash = calculateMD5(file.getBytes());
            
            // 检查是否已存在相同文件（去重）
            GenomeFile existingFile = genomeFileMapper.findByMd5Hash(md5Hash, userId);
            if (existingFile != null) {
                log.info("文件已存在，返回已有文件信息: {}", existingFile.getFileId());
                return convertToMap(existingFile);
            }
            
            // 创建上传目录
            File uploadPath = new File(uploadDir);
            if (!uploadPath.exists()) {
                uploadPath.mkdirs();
            }
            
            // 生成唯一文件名
            String originalFilename = file.getOriginalFilename();
            String extension = getFileExtension(originalFilename);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            String newFilename = "genome_" + userId + "_" + timestamp + "." + extension;
            
            // 保存文件
            Path filePath = Paths.get(uploadDir, newFilename);
            Files.write(filePath, file.getBytes());
            
            // 创建数据库记录
            GenomeFile genomeFile = new GenomeFile();
            genomeFile.setUserId(userId);
            genomeFile.setOriginalFilename(originalFilename);
            genomeFile.setStoredFilename(newFilename);
            genomeFile.setFilePath(filePath.toString());
            genomeFile.setFileSize(file.getSize());
            genomeFile.setFileType(extension);
            genomeFile.setFileFormat(extension.toUpperCase());
            genomeFile.setMd5Hash(md5Hash);
            genomeFile.setDescription(description);
            genomeFile.setUploadTime(LocalDateTime.now());
            genomeFile.setStatus("UPLOADED");
            genomeFile.setIsPublic(isPublic);
            
            // 如果指定了fileType且不是auto-detect，使用指定的类型
            if (!"auto-detect".equals(fileType)) {
                genomeFile.setFileFormat(fileType.toUpperCase());
            }
            
            // 保存到数据库
            genomeFileMapper.insert(genomeFile);
            
            log.info("文件上传成功: {}, 用户ID: {}, 文件ID: {}", originalFilename, userId, genomeFile.getFileId());
            
            return convertToMap(genomeFile);
        } catch (IOException e) {
            log.error("文件保存失败", e);
            throw new RuntimeException("文件保存失败: " + e.getMessage());
        }
    }

    @Override
    public List<Map<String, Object>> getUserGenomeFiles(Long userId) {
        List<GenomeFile> files = genomeFileMapper.findByUserId(userId);
        return files.stream()
                .map(this::convertToMap)
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getFileInfo(Long fileId, Long userId) {
        GenomeFile file = genomeFileMapper.selectById(fileId);
        if (file == null) {
            throw new RuntimeException("文件不存在");
        }
        if (!file.getUserId().equals(userId)) {
            throw new RuntimeException("无权访问该文件");
        }
        return convertToMap(file);
    }

    @Override
    @Transactional
    public void deleteFile(Long fileId, Long userId) {
        GenomeFile file = genomeFileMapper.selectById(fileId);
        if (file == null) {
            throw new RuntimeException("文件不存在");
        }
        if (!file.getUserId().equals(userId)) {
            throw new RuntimeException("无权删除该文件");
        }
        
        log.info("开始删除文件: fileId={}, userId={}", fileId, userId);
        
        // 1. 获取该文件的所有分析任务
        List<AnalysisTask> tasks = analysisTaskMapper.findByFileId(fileId);
        
        // 2. 删除每个任务的输出目录
        for (AnalysisTask task : tasks) {
            if (task.getOutputDir() != null) {
                try {
                    deleteDirectory(task.getOutputDir());
                    log.info("删除任务输出目录: {}", task.getOutputDir());
                } catch (Exception e) {
                    log.error("删除任务输出目录失败: {}", task.getOutputDir(), e);
                }
            }
        }
        
        // 3. 删除物理基因文件
        try {
            Files.deleteIfExists(Paths.get(file.getFilePath()));
            log.info("删除物理文件: {}", file.getFilePath());
        } catch (IOException e) {
            log.error("删除物理文件失败: {}", file.getFilePath(), e);
        }
        
        // 4. 删除数据库记录（会级联删除analysis_tasks和analysis_results）
        genomeFileMapper.deleteById(fileId);
        
        log.info("文件及相关数据删除完成: fileId={}, userId={}", fileId, userId);
    }

    @Override
    public List<Map<String, Object>> searchFiles(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            // 返回所有文件
            List<GenomeFile> allFiles = genomeFileMapper.selectList(null);
            return allFiles.stream()
                    .map(this::convertToMap)
                    .collect(Collectors.toList());
        }
        
        String keywordTrimmed = keyword.trim();
        List<GenomeFile> files = new ArrayList<>();
        
        // 尝试将关键字解析为数字（可能是文件ID或用户ID）
        try {
            Long id = Long.parseLong(keywordTrimmed);
            // 先按文件ID查找
            GenomeFile file = genomeFileMapper.selectById(id);
            if (file != null) {
                files.add(file);
            } else {
                // 如果不是文件ID，按用户ID查找
                files = genomeFileMapper.findByUserId(id);
            }
        } catch (NumberFormatException e) {
            // 如果不是数字，按文件名或用户名查找
            files = genomeFileMapper.searchFiles(keywordTrimmed);
        }
        
        return files.stream()
                .map(this::convertToMap)
                .collect(Collectors.toList());
    }
    
    /**
     * 递归删除目录
     */
    private void deleteDirectory(String directoryPath) throws IOException {
        Path path = Paths.get(directoryPath);
        if (!Files.exists(path)) {
            return;
        }
        
        if (Files.isDirectory(path)) {
            // 递归删除目录中的所有文件和子目录
            Files.walk(path)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } else {
            Files.deleteIfExists(path);
        }
    }

    /**
     * 验证文件
     */
    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new RuntimeException("文件不能为空");
        }
        
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            throw new RuntimeException("文件名不能为空");
        }
        
        String extension = getFileExtension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new RuntimeException("不支持的文件格式，支持的格式: " + ALLOWED_EXTENSIONS);
        }
        
        // 文件大小限制（例如：100MB）
        long maxSize = 500 * 1024 * 1024;
        if (file.getSize() > maxSize) {
            throw new RuntimeException("文件大小超过限制（最大100MB）");
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
    
    /**
     * 计算文件MD5
     */
    private String calculateMD5(byte[] fileBytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(fileBytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("计算MD5失败", e);
            return null;
        }
    }
    
    /**
     * 将实体转换为Map
     */
    private Map<String, Object> convertToMap(GenomeFile file) {
        Map<String, Object> map = new HashMap<>();
        map.put("fileId", file.getFileId());
        map.put("userId", file.getUserId());
        map.put("originalFilename", file.getOriginalFilename());
        map.put("storedFilename", file.getStoredFilename());
        map.put("filePath", file.getFilePath());
        map.put("fileSize", file.getFileSize());
        map.put("fileType", file.getFileType());
        map.put("fileFormat", file.getFileFormat());
        map.put("description", file.getDescription());
        map.put("uploadTime", file.getUploadTime() != null ? file.getUploadTime().toString() : null);
        map.put("status", file.getStatus());
        map.put("isPublic", file.getIsPublic());
        return map;
    }
}
