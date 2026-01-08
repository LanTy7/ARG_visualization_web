package com.sy.service;

import java.util.List;
import java.util.Map;

/**
 * 分析任务服务接口
 */
public interface AnalysisTaskService {
    
    /**
     * 创建分析任务
     * @param fileId 文件ID
     * @param userId 用户ID
     * @param params 分析参数
     * @return 任务信息
     */
    Map<String, Object> createTask(Long fileId, Long userId, Map<String, Object> params);
    
    /**
     * 获取用户的任务列表
     * @param userId 用户ID
     * @param status 任务状态（可选）
     * @return 任务列表
     */
    List<Map<String, Object>> getUserTasks(Long userId, String status);
    
    /**
     * 获取任务详情
     * @param taskId 任务ID
     * @param userId 用户ID
     * @return 任务详情
     */
    Map<String, Object> getTaskDetail(Long taskId, Long userId);
    
    /**
     * 获取任务状态
     * @param taskId 任务ID
     * @param userId 用户ID
     * @return 任务状态
     */
    Map<String, Object> getTaskStatus(Long taskId, Long userId);
    
    /**
     * 取消任务
     * @param taskId 任务ID
     * @param userId 用户ID
     */
    void cancelTask(Long taskId, Long userId);
    
    /**
     * 删除任务
     * @param taskId 任务ID
     * @param userId 用户ID
     */
    void deleteTask(Long taskId, Long userId);
    
    /**
     * 获取任务结果
     * @param taskId 任务ID
     * @param userId 用户ID
     * @return 分析结果
     */
    Map<String, Object> getTaskResult(Long taskId, Long userId);
    
    /**
     * 搜索任务（根据任务ID、文件ID或文件名）
     * @param userId 用户ID
     * @param keyword 搜索关键字
     * @return 任务列表
     */
    List<Map<String, Object>> searchTasks(Long userId, String keyword);
}

