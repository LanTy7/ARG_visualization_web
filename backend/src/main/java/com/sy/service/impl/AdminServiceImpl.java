package com.sy.service.impl;

import com.sy.mapper.AnalysisTaskMapper;
import com.sy.mapper.GenomeFileMapper;
import com.sy.mapper.LoginLogMapper;
import com.sy.mapper.UserMapper;
import com.sy.pojo.AnalysisTask;
import com.sy.pojo.GenomeFile;
import com.sy.pojo.User;
import com.sy.service.AdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 管理员服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final UserMapper userMapper;
    private final GenomeFileMapper genomeFileMapper;
    private final AnalysisTaskMapper analysisTaskMapper;
    private final LoginLogMapper loginLogMapper;

    @Override
    @Transactional
    public void deleteUser(Long userId) {
        log.info("开始删除用户: userId={}", userId);
        
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        
        // 获取用户的所有文件
        List<GenomeFile> files = genomeFileMapper.findByUserId(userId);
        
        // 删除每个文件及其相关的任务和输出目录
        for (GenomeFile file : files) {
            try {
                deleteFileAndRelatedData(file);
            } catch (Exception e) {
                log.error("删除用户文件失败: fileId={}", file.getFileId(), e);
            }
        }
        
        // 数据库外键约束会自动级联删除：
        // - genome_files (ON DELETE CASCADE)
        // - analysis_tasks (ON DELETE CASCADE)
        // - analysis_results (通过analysis_tasks级联删除)
        // 但是login_logs没有外键约束，需要手动删除（如果需要保留日志则可以不删除）
        
        // 删除用户记录（会级联删除相关的表记录）
        userMapper.deleteById(userId);
        
        log.info("用户删除成功: userId={}", userId);
    }

    @Override
    @Transactional
    public void deleteFile(Long fileId) {
        log.info("管理员删除文件: fileId={}", fileId);
        
        GenomeFile file = genomeFileMapper.selectById(fileId);
        if (file == null) {
            throw new RuntimeException("文件不存在");
        }
        
        deleteFileAndRelatedData(file);
        
        log.info("文件删除成功: fileId={}", fileId);
    }

    @Override
    public List<Map<String, Object>> getAllUsers() {
        List<User> users = userMapper.selectList(null);
        return users.stream()
                .map(this::convertUserToMap)
                .collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> getAllFiles() {
        List<GenomeFile> files = genomeFileMapper.selectList(null);
        return files.stream()
                .map(this::convertFileToMap)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void banUser(Long userId, Boolean ban) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        
        user.setStatus(ban ? "BANNED" : "ACTIVE");
        userMapper.updateById(user);
        
        log.info("用户状态更新: userId={}, status={}", userId, user.getStatus());
    }

    @Override
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        // 用户统计
        Long totalUsers = userMapper.selectCount(null);
        stats.put("totalUsers", totalUsers);
        
        // 文件统计
        Long totalFiles = genomeFileMapper.selectCount(null);
        stats.put("totalFiles", totalFiles);
        
        // 任务统计
        Long totalTasks = analysisTaskMapper.selectCount(null);
        stats.put("totalTasks", totalTasks);
        
        // 登录统计
        Long totalLogins = loginLogMapper.countByStatus("SUCCESS");
        stats.put("totalLogins", totalLogins);
        
        return stats;
    }

    @Override
    public List<Map<String, Object>> searchUsers(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return getAllUsers();
        }
        
        List<User> users;
        // 尝试将关键字解析为数字（用户ID）
        try {
            Long userId = Long.parseLong(keyword.trim());
            // 如果是数字，按ID查找
            User user = userMapper.findById(userId);
            users = user != null ? List.of(user) : new ArrayList<>();
        } catch (NumberFormatException e) {
            // 如果不是数字，按用户名查找
            users = userMapper.selectUsersWithKeyword(keyword.trim());
        }
        
        return users.stream()
                .map(this::convertUserToMap)
                .collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> searchFiles(String userKeyword, String fileKeyword) {
        // 如果两个关键字都为空，返回所有文件
        if ((userKeyword == null || userKeyword.trim().isEmpty()) && 
            (fileKeyword == null || fileKeyword.trim().isEmpty())) {
            return getAllFiles();
        }
        
        String userKeywordTrimmed = userKeyword != null ? userKeyword.trim() : "";
        String fileKeywordTrimmed = fileKeyword != null ? fileKeyword.trim() : "";
        
        List<GenomeFile> files = genomeFileMapper.searchFilesWithConditions(
            userKeywordTrimmed.isEmpty() ? null : userKeywordTrimmed,
            fileKeywordTrimmed.isEmpty() ? null : fileKeywordTrimmed
        );
        
        return files.stream()
                .map(this::convertFileToMap)
                .collect(Collectors.toList());
    }

    /**
     * 删除文件及其所有相关数据（物理文件、任务输出目录、数据库记录）
     */
    private void deleteFileAndRelatedData(GenomeFile file) {
        Long fileId = file.getFileId();
        
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
        if (file.getFilePath() != null) {
            try {
                Files.deleteIfExists(Paths.get(file.getFilePath()));
                log.info("删除物理文件: {}", file.getFilePath());
            } catch (IOException e) {
                log.error("删除物理文件失败: {}", file.getFilePath(), e);
            }
        }
        
        // 4. 删除数据库记录（会级联删除analysis_tasks和analysis_results）
        genomeFileMapper.deleteById(fileId);
        
        log.info("文件及相关数据删除完成: fileId={}", fileId);
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
     * 转换用户为Map
     */
    private Map<String, Object> convertUserToMap(User user) {
        Map<String, Object> map = new HashMap<>();
        map.put("userId", user.getUserId());
        map.put("username", user.getUsername());
        map.put("email", user.getEmail());
        map.put("nickname", user.getNickname());
        map.put("role", user.getRole());
        map.put("status", user.getStatus());
        map.put("createdAt", user.getCreatedAt() != null ? user.getCreatedAt().toString() : null);
        map.put("lastLoginAt", user.getLastLoginAt() != null ? user.getLastLoginAt().toString() : null);
        
        // 获取最后登录的地理位置
        com.sy.pojo.LoginLog lastLogin = loginLogMapper.findLastLoginByUserId(user.getUserId());
        map.put("lastLoginLocation", lastLogin != null ? lastLogin.getLocation() : null);
        
        // 统计用户数据
        Integer fileCount = genomeFileMapper.countByUserId(user.getUserId());
        Integer taskCount = analysisTaskMapper.countByUserId(user.getUserId());
        map.put("fileCount", fileCount);
        map.put("taskCount", taskCount);
        
        return map;
    }

    /**
     * 转换文件为Map
     */
    private Map<String, Object> convertFileToMap(GenomeFile file) {
        Map<String, Object> map = new HashMap<>();
        map.put("fileId", file.getFileId());
        map.put("userId", file.getUserId());
        
        // 获取用户信息
        User user = userMapper.findById(file.getUserId());
        map.put("username", user != null ? user.getUsername() : "未知");
        
        map.put("originalFilename", file.getOriginalFilename());
        map.put("fileSize", file.getFileSize());
        map.put("fileType", file.getFileType());
        map.put("status", file.getStatus());
        map.put("uploadTime", file.getUploadTime() != null ? file.getUploadTime().toString() : null);
        
        return map;
    }
}

