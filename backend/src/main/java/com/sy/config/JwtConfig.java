package com.sy.config;

import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Configuration
public class JwtConfig {
    
    @Value("${jwt.secret}")
    private String secretKey;
    
    @Value("${jwt.expiration:86400000}")  // 默认24小时
    private long expiration;
    
    @Bean
    public SecretKey jwtSecretKey() {
        // 将配置的密钥字符串转换为 SecretKey 对象
        return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }
    
    public long getExpiration() {
        return expiration;
    }
} 