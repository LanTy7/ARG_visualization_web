package com.sy.service;

import java.util.Map;

/**
 * BLAST 比对服务接口
 */
public interface BlastService {
    
    /**
     * 对单个序列进行 BLAST 比对
     * @param taskId 任务ID
     * @param sequenceId 序列ID
     * @param userId 用户ID
     * @return BLAST 比对结果
     */
    Map<String, Object> blastSingleSequence(Long taskId, String sequenceId, Long userId);
    
    /**
     * 从输入文件中提取指定序列
     * @param taskId 任务ID
     * @param sequenceId 序列ID
     * @return 序列内容
     */
    String extractSequence(Long taskId, String sequenceId);
}
