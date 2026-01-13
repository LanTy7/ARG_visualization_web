package com.sy.controller;

import com.sy.service.BlastService;
import com.sy.util.JwtUtil;
import com.sy.vo.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * BLAST 比对控制器
 * 提供序列 BLAST 比对功能
 */
@Slf4j
@RestController
@RequestMapping("/api/blast")
@RequiredArgsConstructor
public class BlastController {

    private final BlastService blastService;
    private final JwtUtil jwtUtil;

    /**
     * 对单个序列进行 BLAST 比对
     * @param request 包含 taskId 和 sequenceId
     * @param token JWT token
     * @return BLAST 比对结果
     */
    @PostMapping("/single")
    public Result<Map<String, Object>> blastSingle(
            @RequestBody Map<String, Object> request,
            @RequestHeader("Authorization") String token) {
        try {
            Long userId = jwtUtil.getUserIdFromToken(token.replace("Bearer ", ""));
            
            Long taskId = Long.valueOf(request.get("taskId").toString());
            String sequenceId = (String) request.get("sequenceId");
            
            if (taskId == null || sequenceId == null || sequenceId.isEmpty()) {
                return Result.error("参数不完整");
            }
            
            log.info("收到 BLAST 请求: taskId={}, sequenceId={}, userId={}", taskId, sequenceId, userId);
            
            Map<String, Object> result = blastService.blastSingleSequence(taskId, sequenceId, userId);
            
            return Result.success(result);
            
        } catch (Exception e) {
            log.error("BLAST 比对失败", e);
            return Result.error("BLAST 比对失败: " + e.getMessage());
        }
    }

    /**
     * 获取序列内容（用于调试）
     */
    @GetMapping("/sequence/{taskId}/{sequenceId}")
    public Result<Map<String, Object>> getSequence(
            @PathVariable Long taskId,
            @PathVariable String sequenceId,
            @RequestHeader("Authorization") String token) {
        try {
            // 验证用户身份
            jwtUtil.getUserIdFromToken(token.replace("Bearer ", ""));
            
            String sequence = blastService.extractSequence(taskId, sequenceId);
            
            if (sequence == null) {
                return Result.error("未找到序列");
            }
            
            Map<String, Object> result = Map.of(
                "sequenceId", sequenceId,
                "sequence", sequence,
                "length", sequence.length()
            );
            
            return Result.success(result);
            
        } catch (Exception e) {
            log.error("获取序列失败", e);
            return Result.error("获取序列失败: " + e.getMessage());
        }
    }
}
