package com.sy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sy.pojo.AnalysisResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 分析结果 Mapper 接口
 */
@Mapper
public interface AnalysisResultMapper extends BaseMapper<AnalysisResult> {
    
    /**
     * 根据任务ID查询结果列表
     */
    @Select("SELECT * FROM analysis_results WHERE task_id = #{taskId} ORDER BY region_index ASC")
    List<AnalysisResult> findByTaskId(@Param("taskId") Long taskId);
    
    /**
     * 根据任务ID和区域索引查询结果
     */
    @Select("SELECT * FROM analysis_results WHERE task_id = #{taskId} AND region_index = #{regionIndex} LIMIT 1")
    AnalysisResult findByTaskIdAndRegionIndex(@Param("taskId") Long taskId, @Param("regionIndex") Integer regionIndex);
    
    /**
     * 统计任务的原噬菌体数量
     */
    @Select("SELECT COUNT(*) FROM analysis_results WHERE task_id = #{taskId}")
    Integer countByTaskId(@Param("taskId") Long taskId);
}

