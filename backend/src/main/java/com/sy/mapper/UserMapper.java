package com.sy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sy.pojo.User;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 用户数据访问接口
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
    
    /**
     * 根据用户名查询用户数量
     */
    @Select("SELECT COUNT(*) FROM users WHERE username = #{username}")
    int countByUsername(@Param("username") String username);
    
    /**
     * 根据邮箱查询用户
     * 用于登录验证和检查邮箱是否已被注册
     */
    @Select("SELECT * FROM users WHERE email = #{email}")
    User findByEmail(@Param("email") String email);
    
    /**
     * 插入新用户
     * 包含默认头像URL
     */
    @Insert("INSERT INTO users (username, email, password, avatar, role, status, created_at) " +
            "VALUES (#{username}, #{email}, #{password}, #{avatar}, #{role}, #{status}, #{createdAt})")
    @Options(useGeneratedKeys = true, keyProperty = "userId")
    int insert(User user);
    
    /**
     * 根据用户ID查询用户
     */
    @Select("SELECT * FROM users WHERE user_id = #{userId}")
    User findById(@Param("userId") Long userId);
    
    /**
     * 更新用户信息
     */
    @Update("UPDATE users SET username = #{username}, email = #{email}, " +
            "password = #{password}, avatar = #{avatar}, " +
            "nickname = #{nickname}, last_login_at = #{lastLoginAt} " +
            "WHERE user_id = #{userId}")
    int update(User user);

    User selectByUsername(String username);

    /**
     * 批量查询用户
     * @param userIds 用户ID列表
     * @return 用户列表
     */
    @Select("<script>" +
            "SELECT * FROM users WHERE user_id IN " +
            "<foreach collection='userIds' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "</script>")
    List<User> selectBatchIds(@Param("userIds") List<Long> userIds);

    /**
     * 根据用户名查找用户
     */
    @Select("SELECT * FROM users WHERE username = #{username}")
    User findByUsername(String username);

    /**
     * 查询用户列表，支持关键字搜索
     */
    @Select({
        "<script>",
        "SELECT * FROM users ",
        "WHERE 1=1 ",
        "<if test='keyword != null and keyword != \"\"'>",
        "   AND (username LIKE CONCAT('%', #{keyword}, '%') ",
        "   OR email LIKE CONCAT('%', #{keyword}, '%')) ",
        "</if>",
        "ORDER BY created_at DESC",
        "</script>"
    })
    List<User> selectUsersWithKeyword(@Param("keyword") String keyword);
}
