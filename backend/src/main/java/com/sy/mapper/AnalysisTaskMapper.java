package com.sy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sy.pojo.AnalysisTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 分析任务 Mapper 接口
 */
@Mapper
public interface AnalysisTaskMapper extends BaseMapper<AnalysisTask> {
    
    /**
     * 根据用户ID查询任务列表
     */
    @Select("SELECT * FROM analysis_tasks WHERE user_id = #{userId} ORDER BY created_at DESC")
    List<AnalysisTask> findByUserId(@Param("userId") Long userId);
    
    /**
     * 根据用户ID和状态查询任务列表
     */
    @Select("SELECT * FROM analysis_tasks WHERE user_id = #{userId} AND status = #{status} ORDER BY created_at DESC")
    List<AnalysisTask> findByUserIdAndStatus(@Param("userId") Long userId, @Param("status") String status);
    
    /**
     * 查询运行中的任务
     */
    @Select("SELECT * FROM analysis_tasks WHERE status IN ('PENDING', 'RUNNING') ORDER BY created_at ASC")
    List<AnalysisTask> findRunningTasks();
    
    /**
     * 统计用户任务数量
     */
    @Select("SELECT COUNT(*) FROM analysis_tasks WHERE user_id = #{userId}")
    Integer countByUserId(@Param("userId") Long userId);
    
    /**
     * 根据文件ID查询任务列表
     */
    @Select("SELECT * FROM analysis_tasks WHERE file_id = #{fileId} ORDER BY created_at DESC")
    List<AnalysisTask> findByFileId(@Param("fileId") Long fileId);
    
    /**
     * 搜索任务（根据任务ID、文件ID或文件名）
     * @param userId 用户ID
     * @param keyword 搜索关键字
     * @return 任务列表
     */
    @Select({
        "<script>",
        "SELECT at.* FROM analysis_tasks at ",
        "LEFT JOIN genome_files gf ON at.file_id = gf.file_id ",
        "WHERE at.user_id = #{userId} ",
        "<if test='keyword != null and keyword != \"\"'>",
        "   AND (CAST(at.task_id AS CHAR) LIKE CONCAT('%', #{keyword}, '%') ",
        "   OR CAST(gf.file_id AS CHAR) LIKE CONCAT('%', #{keyword}, '%') ",
        "   OR gf.original_filename LIKE CONCAT('%', #{keyword}, '%')) ",
        "</if>",
        "ORDER BY at.created_at DESC",
        "</script>"
    })
    List<AnalysisTask> searchTasks(@Param("userId") Long userId, @Param("keyword") String keyword);
}

