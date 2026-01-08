package com.sy.vo;

import com.sy.pojo.User;
import lombok.Data;
import lombok.AllArgsConstructor;

/**
 * 登录响应VO
 */
@Data
@AllArgsConstructor
public class LoginResponse {
    
    /**
     * JWT token
     */
    private String token;
    
    /**
     * 用户基本信息
     */
    private UserInfo userInfo;
    
    @Data
    @AllArgsConstructor
    public static class UserInfo {
        private Long userId;
        private String username;
        private String email;
        private String avatarUrl;
        private String role;  // 用户角色：USER, ADMIN
        private String status; // 用户状态：ACTIVE, BANNED
    }
    
    public LoginResponse(String token, User user) {
        this.token = token;
        this.userInfo = new UserInfo(
            user.getUserId(),
            user.getUsername(),
            user.getEmail(),
            user.getAvatarUrl(),
            user.getRole(),   // 添加角色信息
            user.getStatus()  // 添加状态信息
        );
    }
} 