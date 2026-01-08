package com.sy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sy.pojo.GenomeFile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 基因文件 Mapper 接口
 */
@Mapper
public interface GenomeFileMapper extends BaseMapper<GenomeFile> {
    
    /**
     * 根据用户ID查询文件列表
     */
    @Select("SELECT * FROM genome_files WHERE user_id = #{userId} AND status = 'UPLOADED' ORDER BY upload_time DESC")
    List<GenomeFile> findByUserId(@Param("userId") Long userId);
    
    /**
     * 根据MD5查找文件（去重）
     */
    @Select("SELECT * FROM genome_files WHERE md5_hash = #{md5Hash} AND user_id = #{userId} AND status = 'UPLOADED' LIMIT 1")
    GenomeFile findByMd5Hash(@Param("md5Hash") String md5Hash, @Param("userId") Long userId);
    
    /**
     * 统计用户文件数量
     */
    @Select("SELECT COUNT(*) FROM genome_files WHERE user_id = #{userId} AND status = 'UPLOADED'")
    Integer countByUserId(@Param("userId") Long userId);
    
    /**
     * 搜索文件（根据文件名或用户名）
     * @param keyword 搜索关键字
     * @return 文件列表
     */
    @Select({
        "<script>",
        "SELECT gf.* FROM genome_files gf ",
        "LEFT JOIN users u ON gf.user_id = u.user_id ",
        "WHERE 1=1 ",
        "<if test='keyword != null and keyword != \"\"'>",
        "   AND (gf.original_filename LIKE CONCAT('%', #{keyword}, '%') ",
        "   OR u.username LIKE CONCAT('%', #{keyword}, '%')) ",
        "</if>",
        "ORDER BY gf.upload_time DESC",
        "</script>"
    })
    List<GenomeFile> searchFiles(@Param("keyword") String keyword);
    
    /**
     * 搜索文件（根据用户信息和文件信息，支持两个独立条件）
     * @param userKeyword 用户搜索关键字（用户ID或用户名），可为null
     * @param fileKeyword 文件搜索关键字（文件ID或文件名），可为null
     * @return 文件列表
     */
    @Select({
        "<script>",
        "SELECT gf.* FROM genome_files gf ",
        "LEFT JOIN users u ON gf.user_id = u.user_id ",
        "WHERE 1=1 ",
        "<if test='userKeyword != null and userKeyword != \"\"'>",
        "   AND (",
        "   CAST(gf.user_id AS CHAR) LIKE CONCAT('%', #{userKeyword}, '%') ",
        "   OR CAST(u.user_id AS CHAR) LIKE CONCAT('%', #{userKeyword}, '%') ",
        "   OR u.username LIKE CONCAT('%', #{userKeyword}, '%')",
        "   ) ",
        "</if>",
        "<if test='fileKeyword != null and fileKeyword != \"\"'>",
        "   AND (",
        "   CAST(gf.file_id AS CHAR) LIKE CONCAT('%', #{fileKeyword}, '%') ",
        "   OR gf.original_filename LIKE CONCAT('%', #{fileKeyword}, '%')",
        "   ) ",
        "</if>",
        "ORDER BY gf.upload_time DESC",
        "</script>"
    })
    List<GenomeFile> searchFilesWithConditions(@Param("userKeyword") String userKeyword, @Param("fileKeyword") String fileKeyword);
}

