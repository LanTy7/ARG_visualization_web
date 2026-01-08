package com.sy.service;

import java.util.Map;

/**
 * 可视化服务接口
 */
public interface VisualizationService {
    
    /**
     * 获取基因组可视化数据
     * @param taskId 任务ID
     * @param userId 用户ID
     * @return 可视化数据
     */
    Map<String, Object> getGenomeVisualization(Long taskId, Long userId);
    
    /**
     * 获取原噬菌体区域详情
     * @param taskId 任务ID
     * @param regionId 区域ID
     * @param userId 用户ID
     * @return 区域详情
     */
    Map<String, Object> getProphageDetail(Long taskId, Long regionId, Long userId);
    
    /**
     * 获取统计数据
     * @param taskId 任务ID
     * @param userId 用户ID
     * @return 统计数据
     */
    Map<String, Object> getStatistics(Long taskId, Long userId);
    
    /**
     * 导出可视化数据
     * @param taskId 任务ID
     * @param userId 用户ID
     * @return 完整数据
     */
    Map<String, Object> exportVisualizationData(Long taskId, Long userId);
}

