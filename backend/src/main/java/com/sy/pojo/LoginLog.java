package com.sy.pojo;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 登录日志实体类
 */
@Data
@TableName("login_logs")
public class LoginLog {
    
    @TableId(value = "log_id", type = IdType.AUTO)
    private Long logId;
    
    private Long userId;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime loginTime;
    
    private LocalDateTime logoutTime;
    
    private String ipAddress;
    
    private String userAgent;
    
    private String status; // SUCCESS, FAILED
}
