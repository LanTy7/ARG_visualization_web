package com.sy.service;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Prodigal 服务接口
 * 用于调用 Prodigal Docker 容器进行基因预测
 * 将 MAG 原始核酸序列转换为蛋白质序列
 */
public interface ProdigalService {
    
    /**
     * 处理单个 FASTA 文件
     * @param inputFile 输入文件路径 (.fa/.fasta)
     * @param outputDir 输出目录
     * @return 输出的蛋白质序列文件路径 (.faa)
     */
    Path processFile(Path inputFile, Path outputDir);
    
    /**
     * 异步处理单个 FASTA 文件
     * @param inputFile 输入文件路径 (.fa/.fasta)
     * @param outputDir 输出目录
     * @return CompletableFuture 包含输出的蛋白质序列文件路径
     */
    CompletableFuture<Path> processFileAsync(Path inputFile, Path outputDir);
    
    /**
     * 并行处理多个 FASTA 文件
     * @param inputFiles 输入文件路径列表
     * @param outputDir 输出目录
     * @return 输出的蛋白质序列文件路径列表
     */
    List<Path> processFilesParallel(List<Path> inputFiles, Path outputDir);
    
    /**
     * 合并多个 .faa 文件为一个
     * @param faaFiles 要合并的文件列表
     * @param outputFile 合并后的输出文件
     * @return 合并后的文件路径
     */
    Path mergeFiles(List<Path> faaFiles, Path outputFile);
}
