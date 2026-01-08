package com.sy.service.impl;

import com.sy.service.EmailService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@Service
public class EmailServiceImpl implements EmailService {
    
    private static final Logger logger = LoggerFactory.getLogger(EmailServiceImpl.class);

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    public StringRedisTemplate redisTemplate;

    @Value("${spring.mail.username}")
    private String fromEmail;

    // 邮件调试模式配置
    @Value("${email.debug-mode:true}")
    private boolean debugMode;
    
    @Value("${email.debug-recipient:277862362@qq.com}")
    private String debugRecipient;

    private static final String EMAIL_CODE_PREFIX = "email:code:";
    private static final long CODE_EXPIRE_MINUTES = 10;

    @PostConstruct
    public void init() {
        // 显示邮件调试模式状态
        if (debugMode) {
            logger.warn("========================================");
            logger.warn("邮件调试模式已启用！");
            logger.warn("所有验证码将发送到: {}", debugRecipient);
            logger.warn("========================================");
        } else {
            logger.info("邮件正常模式：验证码将发送到用户真实邮箱");
        }
        
        // 测试Redis连接
        try {
            String testKey = "test:connection";
            redisTemplate.opsForValue().set(testKey, "test", 1, TimeUnit.MINUTES);
            String value = redisTemplate.opsForValue().get(testKey);
            if ("test".equals(value)) {
                logger.info("Redis连接测试成功");
            } else {
                logger.error("Redis连接测试失败：值不匹配");
            }
        } catch (Exception e) {
            logger.error("Redis连接测试失败：", e);
            throw new RuntimeException("无法连接到Redis服务器，请检查Redis配置", e);
        }
    }

    @Override
    public void sendVerificationEmail(String to, String verificationCode) {
        String actualRecipient = debugMode ? debugRecipient : to;
        
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(actualRecipient);
        message.setSubject("邮箱验证码");
        
        // 在调试模式下，邮件内容包含原始目标邮箱信息
        String text;
        if (debugMode) {
            text = String.format("【调试模式】\n目标邮箱: %s\n验证码: %s\n验证码有效期为10分钟。", to, verificationCode);
            logger.info("调试模式：验证码邮件发送到 {} (原始目标: {})", actualRecipient, to);
        } else {
            text = String.format("您的验证码是: %s\n验证码有效期为10分钟。", verificationCode);
        }
        message.setText(text);
        
        mailSender.send(message);
        
        // 将验证码保存到Redis，使用原始邮箱作为key
        redisTemplate.opsForValue().set(
            EMAIL_CODE_PREFIX + to,
            verificationCode,
            CODE_EXPIRE_MINUTES,
            TimeUnit.MINUTES
        );
    }

    @Override
    public void sendCodeEmailOnly(String to, String verificationCode, String subject) {
        try {
            String actualRecipient = debugMode ? debugRecipient : to;
            
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(actualRecipient);
            message.setSubject(subject);
            
            // 在调试模式下，邮件内容包含原始目标邮箱信息
            String text;
            if (debugMode) {
                text = String.format("【调试模式 - %s】\n目标邮箱: %s\n验证码: %s\n验证码有效期为5分钟。", 
                                   subject, to, verificationCode);
                logger.info("调试模式：{}邮件发送到 {} (原始目标: {}), 验证码: {}", 
                           subject, actualRecipient, to, verificationCode);
            } else {
                text = String.format("您的验证码是: %s\n验证码有效期为5分钟。", verificationCode);
                logger.info("验证码邮件已发送到: {}", to);
            }
            message.setText(text);
            
            mailSender.send(message);
        } catch (Exception e) {
            logger.error("发送验证码邮件失败: {}", e.getMessage());
            throw new RuntimeException("发送验证码邮件失败，请稍后重试");
        }
    }

    @Override
    public boolean verifyCode(String email, String code) {
        String savedCode = redisTemplate.opsForValue().get(EMAIL_CODE_PREFIX + email);
        if (savedCode != null && savedCode.equals(code)) {
            // 验证成功后删除验证码
            redisTemplate.delete(EMAIL_CODE_PREFIX + email);
            return true;
        }
        return false;
    }

    /**
     * 生成6位数字验证码
     */
    public String generateVerificationCode() {
        Random random = new Random();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            code.append(random.nextInt(10));
        }
        return code.toString();
    }
} 