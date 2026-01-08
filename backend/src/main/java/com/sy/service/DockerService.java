package com.sy.service;

import java.util.Map;

/**
 * Docker 容器服务接口
 * 用于调用 Docker 容器执行原噬菌体识别分析
 */
public interface DockerService {
    
    /**
     * 运行原噬菌体识别容器
     * @param inputFilePath 输入文件路径
     * @param outputDir 输出目录
     * @param params 分析参数
     * @return 容器执行结果
     */
    Map<String, Object> runProphageDetection(String inputFilePath, String outputDir, Map<String, Object> params);
    
    /**
     * 检查容器状态
     * @param containerId 容器ID
     * @return 容器状态
     */
    Map<String, Object> checkContainerStatus(String containerId);
    
    /**
     * 停止容器
     * @param containerId 容器ID
     */
    void stopContainer(String containerId);
    
    /**
     * 终止正在运行的分析进程
     * @param taskId 任务ID
     */
    void cancelAnalysis(Long taskId);
}

