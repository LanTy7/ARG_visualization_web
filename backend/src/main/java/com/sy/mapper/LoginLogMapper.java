package com.sy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sy.pojo.LoginLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 登录日志 Mapper 接口
 */
@Mapper
public interface LoginLogMapper extends BaseMapper<LoginLog> {
    
    /**
     * 获取用户最近一次登录记录（logout_time 为 NULL 表示未退出）
     */
    @Select("SELECT * FROM login_logs WHERE user_id = #{userId} ORDER BY login_time DESC LIMIT 1")
    LoginLog findLastLoginByUserId(@Param("userId") Long userId);

    /**
     * 查询登录日志，关联用户表获取用户名
     */
    @Select({
        "<script>",
        "SELECT l.*, u.username ",
        "FROM login_logs l ",
        "LEFT JOIN users u ON l.user_id = u.user_id ",
        "WHERE 1=1 ",
        "<if test='username != null and username != \"\"'>",
        "   AND u.username LIKE CONCAT('%', #{username}, '%') ",
        "</if>",
        "<if test='status != null and status != \"\"'>",
        "   AND l.status = #{status} ",
        "</if>",
        "ORDER BY l.login_time DESC",
        "</script>"
    })
    List<LoginLog> selectLoginLogsWithUsername(@Param("username") String username,
                                             @Param("status") String status);

    /**
     * 统计指定状态的日志数量
     */
    @Select("SELECT COUNT(*) FROM login_logs WHERE status = #{status}")
    long countByStatus(@Param("status") String status);

    /**
     * 统计最近N天的活跃用户数
     */
    @Select("SELECT COUNT(DISTINCT user_id) FROM login_logs " +
            "WHERE status = 'SUCCESS' AND login_time >= #{startTime}")
    long countActiveUsers(@Param("startTime") LocalDateTime startTime);
    
    /**
     * 查询用户的登录历史
     */
    @Select("SELECT * FROM login_logs WHERE user_id = #{userId} ORDER BY login_time DESC LIMIT #{limit}")
    List<LoginLog> findUserLoginHistory(@Param("userId") Long userId, @Param("limit") Integer limit);
} 