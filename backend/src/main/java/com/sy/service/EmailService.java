package com.sy.service;

public interface EmailService {
    /**
     * 发送邮箱验证码
     * @param to 收件人邮箱
     * @param verificationCode 验证码
     */
    void sendVerificationEmail(String to, String verificationCode);
    
    /**
     * 只发送验证码邮件，不保存到Redis（用于登录验证码等已经保存过的场景）
     * @param to 收件人邮箱
     * @param verificationCode 验证码
     * @param subject 邮件主题
     */
    void sendCodeEmailOnly(String to, String verificationCode, String subject);
    
    /**
     * 验证邮箱验证码
     * @param email 邮箱
     * @param code 验证码
     * @return 验证是否成功
     */
    boolean verifyCode(String email, String code);
} 