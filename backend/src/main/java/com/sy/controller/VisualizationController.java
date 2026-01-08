package com.sy.controller;

import com.sy.service.VisualizationService;
import com.sy.util.JwtUtil;
import com.sy.vo.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 可视化数据控制器
 * 提供抗性基因（ARG）识别结果的可视化数据
 */
@Slf4j
@RestController
@RequestMapping("/api/visualization")
@RequiredArgsConstructor
public class VisualizationController {

    private final VisualizationService visualizationService;
    private final JwtUtil jwtUtil;

    /**
     * 获取 ARG 可视化数据
     */
    @GetMapping("/genome/{taskId}")
    public Result<Map<String, Object>> getGenomeVisualization(
            @PathVariable Long taskId,
            @RequestHeader("Authorization") String token) {
        try {
            Long userId = jwtUtil.getUserIdFromToken(token.replace("Bearer ", ""));
            Map<String, Object> data = visualizationService.getGenomeVisualization(taskId, userId);
            return Result.success(data);
        } catch (Exception e) {
            log.error("获取可视化数据失败", e);
            return Result.error("获取可视化数据失败: " + e.getMessage());
        }
    }

    /**
     * 获取统计图表数据
     */
    @GetMapping("/statistics/{taskId}")
    public Result<Map<String, Object>> getStatistics(
            @PathVariable Long taskId,
            @RequestHeader("Authorization") String token) {
        try {
            Long userId = jwtUtil.getUserIdFromToken(token.replace("Bearer ", ""));
            Map<String, Object> statistics = visualizationService.getStatistics(taskId, userId);
            return Result.success(statistics);
        } catch (Exception e) {
            log.error("获取统计数据失败", e);
            return Result.error("获取统计数据失败: " + e.getMessage());
        }
    }

    /**
     * 导出可视化数据
     */
    @GetMapping("/export/{taskId}")
    public Result<Map<String, Object>> exportVisualizationData(
            @PathVariable Long taskId,
            @RequestHeader("Authorization") String token) {
        try {
            Long userId = jwtUtil.getUserIdFromToken(token.replace("Bearer ", ""));
            Map<String, Object> data = visualizationService.exportVisualizationData(taskId, userId);
            return Result.success(data);
        } catch (Exception e) {
            log.error("导出可视化数据失败", e);
            return Result.error("导出数据失败: " + e.getMessage());
        }
    }
}
