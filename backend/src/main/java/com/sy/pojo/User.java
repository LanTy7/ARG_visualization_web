package com.sy.pojo;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

/**
 * 用户实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("users")
public class User {
    
    @TableId(value = "user_id", type = IdType.AUTO)
    private Long userId;
    
    private String username;
    
    private String email;
    
    @TableField("password")
    private String password; // 对应数据库的password字段
    
    private String nickname;
    
    @TableField("avatar")
    private String avatar; // 对应数据库的avatar字段
    
    private String role; // USER, ADMIN
    
    private String status; // ACTIVE, BANNED
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    
    private LocalDateTime lastLoginAt;
    

    @TableField(exist = false)
    private String passwordHash;
    
    
    @TableField(exist = false)
    private String avatarUrl;
    
    public String getPasswordHash() {
        return this.password;
    }
    
    public void setPasswordHash(String passwordHash) {
        this.password = passwordHash;
    }
    
    public String getAvatarUrl() {
        return this.avatar;
    }
    
    public void setAvatarUrl(String avatarUrl) {
        this.avatar = avatarUrl;
    }
} 