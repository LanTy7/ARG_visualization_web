package com.sy.vo;

import com.sy.pojo.LoginLog;
import lombok.Data;

@Data
public class LoginLogVO {
    private Long logId;
    private Long userId;
    private String username;  // 从关联查询获取
    private String email;     // 从关联查询获取
    private String loginTime;
    private String logoutTime;
    private String ipAddress;
    private String userAgent;
    private String status;    // SUCCESS, FAILED
    
    public LoginLogVO(LoginLog log, String username, String email) {
        this.logId = log.getLogId();
        this.userId = log.getUserId();
        this.username = username;
        this.email = email;
        this.loginTime = log.getLoginTime() != null ? log.getLoginTime().toString() : null;
        this.logoutTime = log.getLogoutTime() != null ? log.getLogoutTime().toString() : null;
        this.ipAddress = log.getIpAddress();
        this.userAgent = log.getUserAgent();
        this.status = log.getStatus();
    }
} 