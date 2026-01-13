package com.sy.service.impl;

import com.sy.mapper.AnalysisTaskMapper;
import com.sy.mapper.GenomeFileMapper;
import com.sy.pojo.AnalysisTask;
import com.sy.pojo.GenomeFile;
import com.sy.service.BlastService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * BLAST 比对服务实现
 * 调用 Docker 容器执行 blastp 比对
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlastServiceImpl implements BlastService {

    private final AnalysisTaskMapper analysisTaskMapper;
    private final GenomeFileMapper genomeFileMapper;

    @Value("${blast.enabled:true}")
    private boolean blastEnabled;

    @Value("${blast.db-path:/home/lanty/Documents/study/intership_project/ARG_whole_project/blast_db/db/ARGNet_DB}")
    private String blastDbPath;

    @Value("${blast.image-name:ncbi/blast:latest}")
    private String blastImageName;

    @Value("${blast.evalue:1e-5}")
    private String evalue;

    @Value("${blast.max-hits:10}")
    private int maxHits;

    @Value("${blast.threads:4}")
    private int threads;

    @Value("${analysis.output-dir:./outputs}")
    private String outputBaseDir;

    @Value("${file.upload.genome-dir:./uploads/genome}")
    private String genomeUploadDir;

    @Override
    public Map<String, Object> blastSingleSequence(Long taskId, String sequenceId, Long userId) {
        log.info("开始 BLAST 比对: taskId={}, sequenceId={}", taskId, sequenceId);

        // 1. 验证任务
        AnalysisTask task = analysisTaskMapper.selectById(taskId);
        if (task == null) {
            throw new RuntimeException("任务不存在");
        }
        if (!task.getUserId().equals(userId)) {
            throw new RuntimeException("无权访问该任务");
        }

        // 2. 提取序列
        String sequence = extractSequence(taskId, sequenceId);
        if (sequence == null || sequence.isEmpty()) {
            throw new RuntimeException("未找到序列: " + sequenceId);
        }

        log.info("成功提取序列，长度: {} aa", sequence.length());

        // 3. 执行 BLAST
        List<Map<String, Object>> hits = runBlast(sequenceId, sequence);

        // 4. 构建返回结果
        Map<String, Object> result = new HashMap<>();
        
        Map<String, Object> queryInfo = new HashMap<>();
        queryInfo.put("id", sequenceId);
        queryInfo.put("length", sequence.length());
        result.put("queryInfo", queryInfo);
        
        result.put("hits", hits);
        result.put("totalHits", hits.size());

        log.info("BLAST 比对完成，找到 {} 个匹配", hits.size());
        return result;
    }

    @Override
    public String extractSequence(Long taskId, String sequenceId) {
        AnalysisTask task = analysisTaskMapper.selectById(taskId);
        if (task == null) {
            return null;
        }

        // 确定输入文件路径
        Path inputFilePath = null;

        // MAG 任务：从 prodigal/merged.faa 或各个 .faa 文件中查找
        if ("MAG".equals(task.getTaskType())) {
            Path taskOutputDir = Paths.get(outputBaseDir, "task_" + taskId);
            Path prodigalDir = taskOutputDir.resolve("prodigal");
            
            // 先尝试 merged.faa
            Path mergedFile = prodigalDir.resolve("merged.faa");
            if (Files.exists(mergedFile)) {
                String seq = extractSequenceFromFasta(mergedFile, sequenceId);
                if (seq != null) return seq;
            }
            
            // 再尝试各个单独的 .faa 文件
            if (Files.exists(prodigalDir)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(prodigalDir, "*.faa")) {
                    for (Path faaFile : stream) {
                        if (!faaFile.getFileName().toString().equals("merged.faa")) {
                            String seq = extractSequenceFromFasta(faaFile, sequenceId);
                            if (seq != null) return seq;
                        }
                    }
                } catch (IOException e) {
                    log.error("遍历 prodigal 目录失败", e);
                }
            }
        } else {
            // 普通任务：从上传的文件中查找
            if (task.getFileId() != null) {
                GenomeFile genomeFile = genomeFileMapper.selectById(task.getFileId());
                if (genomeFile != null && genomeFile.getFilePath() != null) {
                    inputFilePath = Paths.get(genomeFile.getFilePath());
                    if (Files.exists(inputFilePath)) {
                        String seq = extractSequenceFromFasta(inputFilePath, sequenceId);
                        if (seq != null) return seq;
                    }
                }
            }
            
            // 尝试从任务输出目录中的 input 文件查找
            Path taskOutputDir = Paths.get(outputBaseDir, "task_" + taskId);
            try {
                if (Files.exists(taskOutputDir)) {
                    try (DirectoryStream<Path> stream = Files.newDirectoryStream(taskOutputDir)) {
                        for (Path file : stream) {
                            String name = file.getFileName().toString().toLowerCase();
                            if (name.endsWith(".fasta") || name.endsWith(".fa") || 
                                name.endsWith(".faa") || name.endsWith(".fna")) {
                                String seq = extractSequenceFromFasta(file, sequenceId);
                                if (seq != null) return seq;
                            }
                        }
                    }
                }
            } catch (IOException e) {
                log.error("遍历任务目录失败", e);
            }
        }

        log.warn("未找到序列: {}", sequenceId);
        return null;
    }

    /**
     * 从 FASTA 文件中提取指定 ID 的序列
     */
    private String extractSequenceFromFasta(Path fastaFile, String targetId) {
        try (BufferedReader reader = Files.newBufferedReader(fastaFile)) {
            String line;
            StringBuilder sequence = new StringBuilder();
            boolean found = false;

            while ((line = reader.readLine()) != null) {
                if (line.startsWith(">")) {
                    // 如果已经找到目标序列，遇到下一个序列头则结束
                    if (found) {
                        break;
                    }
                    // 检查是否是目标序列
                    // 序列头格式可能是 ">ID description" 或 ">ID"
                    String header = line.substring(1).trim();
                    String id = header.split("\\s+")[0]; // 取第一个空格前的部分作为 ID
                    
                    if (id.equals(targetId) || header.equals(targetId)) {
                        found = true;
                    }
                } else if (found) {
                    // 收集序列内容
                    sequence.append(line.trim());
                }
            }

            if (found && sequence.length() > 0) {
                return sequence.toString();
            }
        } catch (IOException e) {
            log.error("读取 FASTA 文件失败: {}", fastaFile, e);
        }
        return null;
    }

    /**
     * 执行 BLAST 比对
     */
    private List<Map<String, Object>> runBlast(String sequenceId, String sequence) {
        List<Map<String, Object>> hits = new ArrayList<>();

        if (!blastEnabled) {
            log.warn("BLAST 未启用，返回空结果");
            return hits;
        }

        Path tempDir = null;
        try {
            // 1. 创建临时目录和查询文件
            tempDir = Files.createTempDirectory("blast_query_");
            Path queryFile = tempDir.resolve("query.fasta");
            Files.writeString(queryFile, ">" + sequenceId + "\n" + sequence + "\n");

            Path outputFile = tempDir.resolve("blast_result.tsv");

            // 2. 获取数据库目录
            Path dbPath = Paths.get(blastDbPath);
            Path dbDir = dbPath.getParent();
            String dbName = dbPath.getFileName().toString();

            // 3. 构建 Docker 命令
            String command = String.format(
                "docker run --rm " +
                "-v %s:/query:ro " +
                "-v %s:/blast_db:ro " +
                "-v %s:/output " +
                "%s blastp " +
                "-query /query/query.fasta " +
                "-db /blast_db/%s " +
                "-outfmt \"6 qseqid sseqid pident length evalue bitscore qlen slen qstart qend sstart send stitle\" " +
                "-evalue %s " +
                "-max_target_seqs %d " +
                "-num_threads %d " +
                "-out /output/blast_result.tsv",
                tempDir.toAbsolutePath(),
                dbDir.toAbsolutePath(),
                tempDir.toAbsolutePath(),
                blastImageName,
                dbName,
                evalue,
                maxHits,
                threads
            );

            log.info("执行 BLAST 命令: {}", command);

            // 4. 执行命令
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // 读取输出
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(120, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("BLAST 执行超时");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.error("BLAST 执行失败，退出码: {}, 输出: {}", exitCode, output);
                throw new RuntimeException("BLAST 执行失败: " + output);
            }

            // 5. 解析结果
            if (Files.exists(outputFile)) {
                hits = parseBlastOutput(outputFile);
            }

        } catch (Exception e) {
            log.error("BLAST 执行异常", e);
            throw new RuntimeException("BLAST 比对失败: " + e.getMessage(), e);
        } finally {
            // 清理临时文件
            if (tempDir != null) {
                try {
                    Files.walk(tempDir)
                         .sorted(Comparator.reverseOrder())
                         .forEach(path -> {
                             try {
                                 Files.delete(path);
                             } catch (IOException e) {
                                 log.warn("删除临时文件失败: {}", path);
                             }
                         });
                } catch (IOException e) {
                    log.warn("清理临时目录失败", e);
                }
            }
        }

        return hits;
    }

    /**
     * 解析 BLAST 输出文件
     * 格式: qseqid sseqid pident length evalue bitscore qlen slen qstart qend sstart send stitle
     */
    private List<Map<String, Object>> parseBlastOutput(Path outputFile) throws IOException {
        List<Map<String, Object>> hits = new ArrayList<>();

        List<String> lines = Files.readAllLines(outputFile);
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;

            String[] fields = line.split("\t");
            if (fields.length < 12) continue;

            Map<String, Object> hit = new HashMap<>();
            hit.put("queryId", fields[0]);
            hit.put("subjectId", fields[1]);
            hit.put("identity", parseDouble(fields[2]));
            hit.put("alignLength", parseInt(fields[3]));
            hit.put("evalue", fields[4]); // 保持字符串格式，科学计数法
            hit.put("bitScore", parseDouble(fields[5]));
            hit.put("queryLength", parseInt(fields[6]));
            hit.put("subjectLength", parseInt(fields[7]));
            hit.put("queryStart", parseInt(fields[8]));
            hit.put("queryEnd", parseInt(fields[9]));
            hit.put("subjectStart", parseInt(fields[10]));
            hit.put("subjectEnd", parseInt(fields[11]));
            
            // 描述信息（如果有）
            if (fields.length > 12) {
                hit.put("description", fields[12]);
            } else {
                hit.put("description", "");
            }

            hits.add(hit);
        }

        return hits;
    }

    private Double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
