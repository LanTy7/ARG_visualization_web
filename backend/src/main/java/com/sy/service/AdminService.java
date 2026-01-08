package com.sy.service;

import java.util.List;
import java.util.Map;

/**
 * 管理员服务接口
 */
public interface AdminService {
    
    /**
     * 删除用户及其所有相关数据
     * @param userId 用户ID
     */
    void deleteUser(Long userId);
    
    /**
     * 删除文件及其所有相关数据（管理员操作）
     * @param fileId 文件ID
     */
    void deleteFile(Long fileId);
    
    /**
     * 获取所有用户列表（包含统计信息）
     * @return 用户列表
     */
    List<Map<String, Object>> getAllUsers();
    
    /**
     * 获取所有文件列表（包含用户信息）
     * @return 文件列表
     */
    List<Map<String, Object>> getAllFiles();
    
    /**
     * 封禁/解封用户
     * @param userId 用户ID
     * @param ban 是否封禁
     */
    void banUser(Long userId, Boolean ban);
    
    /**
     * 获取系统统计信息
     * @return 统计信息
     */
    Map<String, Object> getStatistics();
    
    /**
     * 搜索用户（根据用户名或用户ID）
     * @param keyword 搜索关键字（用户名或ID）
     * @return 用户列表
     */
    List<Map<String, Object>> searchUsers(String keyword);
    
    /**
     * 搜索文件（根据用户信息和文件信息）
     * @param userKeyword 用户搜索关键字（用户ID或用户名），可为空
     * @param fileKeyword 文件搜索关键字（文件ID或文件名），可为空
     * @return 文件列表
     */
    List<Map<String, Object>> searchFiles(String userKeyword, String fileKeyword);
}

