package com.sy.service;

import com.sy.pojo.User;
import com.sy.vo.LoginRequest;
import com.sy.vo.RegisterRequest;
import com.sy.vo.ResetPasswordRequest;

/**
 * 登录认证服务接口
 */
public interface LoginService {
    
    /**
     * 发送邮箱验证码
     * @param email 邮箱地址
     */
    void sendVerificationCode(String email);
    
    /**
     * 注册新用户
     * @param request 注册请求（包含验证码）
     */
    void register(RegisterRequest request);
    
    /**
     * 用户登录
     * @param username 用户名或邮箱
     * @param password 密码
     * @param code 验证码
     * @return 用户信息
     * @throws RuntimeException 当用户名或密码错误时抛出异常
     */
    User login(String username, String password, String code);
    
    /**
     * 生成JWT token
     * @param user 用户信息
     * @return JWT token字符串
     */
    String generateToken(User user);
    
    /**
     * 记录用户登录日志
     * @param userId 用户ID
     */
    void recordLogin(Long userId);
    
    /**
     * 验证JWT token
     * @param token JWT token字符串
     * @return 用户ID，如果token无效则返回null
     */
    Long validateToken(String token);
    
    /**
     * 记录用户退出登录
     * @param userId 用户ID
     */
    void recordLogout(Long userId);
    
    /**
     * 发送登录验证码（开发/测试用，直接返回验证码）
     * @param email 邮箱地址
     * @return 验证码
     */
    String sendLoginCode(String email);
    
    /**
     * 发送重置密码验证码
     * @param email 邮箱
     */
    void sendResetCode(String email);
    
    /**
     * 重置密码
     * @param request 请求体
     */
    void resetPassword(ResetPasswordRequest request);
} 