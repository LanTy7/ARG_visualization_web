package com.sy.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 基因组文件服务接口
 */
public interface GenomeFileService {
    
    /**
     * 上传基因组文件
     * @param file 文件
     * @param userId 用户ID
     * @param options 上传选项（fileType, reference, description等）
     * @return 文件信息
     */
    Map<String, Object> uploadGenomeFile(MultipartFile file, Long userId, Map<String, Object> options);
    
    /**
     * 获取用户的基因组文件列表
     * @param userId 用户ID
     * @return 文件列表
     */
    List<Map<String, Object>> getUserGenomeFiles(Long userId);
    
    /**
     * 获取文件详细信息
     * @param fileId 文件ID
     * @param userId 用户ID
     * @return 文件信息
     */
    Map<String, Object> getFileInfo(Long fileId, Long userId);
    
    /**
     * 删除文件
     * @param fileId 文件ID
     * @param userId 用户ID
     */
    void deleteFile(Long fileId, Long userId);
    
    /**
     * 搜索文件（根据用户名、用户ID、文件ID或文件名）
     * @param keyword 搜索关键字
     * @return 文件列表
     */
    List<Map<String, Object>> searchFiles(String keyword);
}

