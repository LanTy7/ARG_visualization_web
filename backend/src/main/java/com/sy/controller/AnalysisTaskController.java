package com.sy.controller;

import com.sy.service.AnalysisTaskService;
import com.sy.util.JwtUtil;
import com.sy.vo.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 分析任务控制器
 * 用于创建、查询、管理原噬菌体识别任务
 */
@Slf4j
@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
public class AnalysisTaskController {

    private final AnalysisTaskService analysisTaskService;
    private final JwtUtil jwtUtil;

    /**·
     * 创建分析任务
     * @param request 包含文件ID和分析参数
     * @param token JWT token
     * @return 任务ID和初始状态
     */
    @PostMapping("/create")
    public Result<Map<String, Object>> createAnalysisTask(
            @RequestBody Map<String, Object> request,
            @RequestHeader("Authorization") String token) {
        try {
            Long userId = jwtUtil.getUserIdFromToken(token.replace("Bearer ", ""));
            Long fileId = Long.valueOf(request.get("fileId").toString());
            
            log.info("用户 {} 创建分析任务，文件ID: {}", userId, fileId);
            
            // 创建任务并启动 Docker 容器进行分析
            Map<String, Object> task = analysisTaskService.createTask(fileId, userId, request);
            
            return Result.success("分析任务创建成功", task);
        } catch (Exception e) {
            log.error("创建分析任务失败", e);
            return Result.error("创建任务失败: " + e.getMessage());
        }
    }

    /**
     * 获取任务列表
     * @param token JWT token
     * @return 任务列表
     */
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> getTaskList(
            @RequestHeader("Authorization") String token,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "keyword", required = false) String keyword) {
        try {
            Long userId = jwtUtil.getUserIdFromToken(token.replace("Bearer ", ""));
            List<Map<String, Object>> tasks;
            if (keyword != null && !keyword.trim().isEmpty()) {
                tasks = analysisTaskService.searchTasks(userId, keyword.trim());
            } else {
                tasks = analysisTaskService.getUserTasks(userId, status);
            }
            return Result.success(tasks);
        } catch (Exception e) {
            log.error("获取任务列表失败", e);
            return Result.error("获取任务列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取任务详情
     * @param taskId 任务ID
     * @param token JWT token
     * @return 任务详情
     */
    @GetMapping("/{taskId}")
    public Result<Map<String, Object>> getTaskDetail(
            @PathVariable Long taskId,
            @RequestHeader("Authorization") String token) {
        try {
            Long userId = jwtUtil.getUserIdFromToken(token.replace("Bearer ", ""));
            Map<String, Object> task = analysisTaskService.getTaskDetail(taskId, userId);
            return Result.success(task);
        } catch (Exception e) {
            log.error("获取任务详情失败", e);
            return Result.error("获取任务详情失败: " + e.getMessage());
        }
    }

    /**
     * 获取任务状态
     * @param taskId 任务ID
     * @param token JWT token
     * @return 任务状态信息
     */
    @GetMapping("/{taskId}/status")
    public Result<Map<String, Object>> getTaskStatus(
            @PathVariable Long taskId,
            @RequestHeader("Authorization") String token) {
        try {
            Long userId = jwtUtil.getUserIdFromToken(token.replace("Bearer ", ""));
            Map<String, Object> status = analysisTaskService.getTaskStatus(taskId, userId);
            return Result.success(status);
        } catch (Exception e) {
            log.error("获取任务状态失败", e);
            return Result.error("获取任务状态失败: " + e.getMessage());
        }
    }

    /**
     * 取消任务
     * @param taskId 任务ID
     * @param token JWT token
     * @return 取消结果
     */
    @PostMapping("/{taskId}/cancel")
    public Result<Void> cancelTask(
            @PathVariable Long taskId,
            @RequestHeader("Authorization") String token) {
        try {
            Long userId = jwtUtil.getUserIdFromToken(token.replace("Bearer ", ""));
            analysisTaskService.cancelTask(taskId, userId);
            return Result.success("任务已取消", null);
        } catch (Exception e) {
            log.error("取消任务失败", e);
            return Result.error("取消任务失败: " + e.getMessage());
        }
    }

    /**
     * 删除任务
     * @param taskId 任务ID
     * @param token JWT token
     * @return 删除结果
     */
    @DeleteMapping("/{taskId}")
    public Result<Void> deleteTask(
            @PathVariable Long taskId,
            @RequestHeader("Authorization") String token) {
        try {
            Long userId = jwtUtil.getUserIdFromToken(token.replace("Bearer ", ""));
            analysisTaskService.deleteTask(taskId, userId);
            return Result.success();
        } catch (Exception e) {
            log.error("删除任务失败", e);
            return Result.error("删除任务失败: " + e.getMessage());
        }
    }

    /**
     * 获取任务结果（原噬菌体识别结果）
     * @param taskId 任务ID
     * @param token JWT token
     * @return 分析结果
     */
    @GetMapping("/{taskId}/result")
    public Result<Map<String, Object>> getTaskResult(
            @PathVariable Long taskId,
            @RequestHeader("Authorization") String token) {
        try {
            Long userId = jwtUtil.getUserIdFromToken(token.replace("Bearer ", ""));
            Map<String, Object> result = analysisTaskService.getTaskResult(taskId, userId);
            return Result.success(result);
        } catch (Exception e) {
            log.error("获取任务结果失败", e);
            return Result.error("获取任务结果失败: " + e.getMessage());
        }
    }
}

