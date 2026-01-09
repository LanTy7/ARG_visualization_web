package com.sy.service;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * MAG 分析服务接口
 * 编排 MAG 文件夹的完整分析流程：Prodigal 预处理 + ARG 分析
 */
public interface MagAnalysisService {
    
    /**
     * 执行 MAG 分析
     * @param taskId 任务ID
     * @param magDir MAG 文件夹路径（包含多个 .fa 文件）
     * @param outputDir 输出目录
     * @param params 分析参数
     * @return 分析结果
     */
    Map<String, Object> analyzeMag(Long taskId, Path magDir, Path outputDir, Map<String, Object> params);
    
    /**
     * 扫描 MAG 文件夹中的 FASTA 文件
     * @param magDir MAG 文件夹路径
     * @return FASTA 文件列表
     */
    List<Path> scanFastaFiles(Path magDir);
    
    /**
     * 更新任务进度
     * @param taskId 任务ID
     * @param stage 当前阶段 (1=预处理, 2=分析)
     * @param progress 进度百分比 (0-100)
     * @param message 进度消息
     */
    void updateProgress(Long taskId, int stage, int progress, String message);
}
