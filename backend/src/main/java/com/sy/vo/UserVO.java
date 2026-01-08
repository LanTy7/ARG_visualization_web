package com.sy.vo;

import com.sy.pojo.User;
import lombok.Data;

@Data
public class UserVO {
    private Long userId;
    private String username;
    private String email;
    private String avatarUrl;
    private String createdAt;
    
    public UserVO(User user) {
        this.userId = user.getUserId();
        this.username = user.getUsername();
        this.email = user.getEmail();
        this.avatarUrl = user.getAvatarUrl();
        this.createdAt = user.getCreatedAt().toString();
    }
} 