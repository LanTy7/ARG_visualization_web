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
 * 提供原噬菌体识别结果的可视化数据
 */
@Slf4j
@RestController
@RequestMapping("/api/visualization")
@RequiredArgsConstructor
public class VisualizationController {

    private final VisualizationService visualizationService;
    private final JwtUtil jwtUtil;

    /**
     * 获取基因组可视化数据
     * @param taskId 任务ID
     * @param token JWT token
     * @return 包含基因组序列和原噬菌体区域的可视化数据
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
            log.error("获取基因组可视化数据失败", e);
            return Result.error("获取可视化数据失败: " + e.getMessage());
        }
    }

    /**
     * 获取原噬菌体区域详情
     * @param taskId 任务ID
     * @param regionId 区域ID
     * @param token JWT token
     * @return 原噬菌体区域的详细信息
     */
    @GetMapping("/prophage/{taskId}/{regionId}")
    public Result<Map<String, Object>> getProphageDetail(
            @PathVariable Long taskId,
            @PathVariable Long regionId,
            @RequestHeader("Authorization") String token) {
        try {
            Long userId = jwtUtil.getUserIdFromToken(token.replace("Bearer ", ""));
            Map<String, Object> detail = visualizationService.getProphageDetail(taskId, regionId, userId);
            return Result.success(detail);
        } catch (Exception e) {
            log.error("获取原噬菌体详情失败", e);
            return Result.error("获取详情失败: " + e.getMessage());
        }
    }

    /**
     * 获取统计图表数据
     * @param taskId 任务ID
     * @param token JWT token
     * @return ECharts 格式的统计数据
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
     * 导出可视化数据（JSON格式）
     * @param taskId 任务ID
     * @param token JWT token
     * @return 完整的可视化数据
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

