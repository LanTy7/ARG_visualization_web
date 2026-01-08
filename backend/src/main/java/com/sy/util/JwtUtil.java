package com.sy.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class JwtUtil {

    private final SecretKey key;
    private final RedisTemplate<String, String> redisTemplate;
    
    @Value("${jwt.expiration}")
    private Long expiration;

    public JwtUtil(RedisTemplate<String, String> redisTemplate, @Value("${jwt.secret}") String secretKey) {
        // 使用配置文件中的密钥
        this.key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
        this.redisTemplate = redisTemplate;
    }

    /**
     * 生成JWT token
     * @param userId 用户ID
     * @param role 角色（user/admin）
     * @return JWT token字符串
     */
    public String generateToken(Long userId, String role) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim("role", role)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(key)
                .compact();
    }
    
    /**
     * 验证token是否有效
     * @param token JWT token字符串
     * @return 是否有效
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token);
            return !isTokenBlacklisted(token);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 从token中获取用户ID
     * @param token JWT token字符串
     * @return 用户ID，如果token无效则返回null
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return Long.parseLong(claims.getSubject());
    }

    /**
     * 从Token中获取角色
     */
    public String getRoleFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.get("role", String.class);
    }

    /**
     * 检查Token是否在黑名单中
     */
    public boolean isTokenBlacklisted(String token) {
        String key = "token_blacklist:" + token;
        Boolean exists = redisTemplate.hasKey(key);
        log.debug("检查token黑名单状态：key={}, exists={}", key, exists);
        return Boolean.TRUE.equals(exists);
    }

    public Claims getClaimsFromToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * 将 token 加入黑名单
     * @param token JWT token
     */
    public void addToBlacklist(String token) {
        try {
            Claims claims = getClaimsFromToken(token);
            Date expiration = claims.getExpiration();
            long ttl = expiration.getTime() - System.currentTimeMillis();
            
            if (ttl > 0) {
                String key = "token_blacklist:" + token;
                redisTemplate.opsForValue().set(key, "blacklisted", ttl, TimeUnit.MILLISECONDS);
                log.info("Token已加入黑名单，key={}, ttl={}ms", key, ttl);
                
                // 验证是否成功加入黑名单
                Boolean exists = redisTemplate.hasKey(key);
                log.info("验证黑名单状态：key={}, exists={}", key, exists);
            } else {
                log.warn("Token已过期，无需加入黑名单");
            }
        } catch (Exception e) {
            log.error("将token加入黑名单时发生错误", e);
        }
    }

    /**
     * 从请求头中获取用户ID
     * @param request HTTP请求
     * @return 用户ID，如果token无效或不存在则返回null
     */
    public Long getUserIdFromRequest(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
            return getUserIdFromToken(token);
        }
        return null;
    }
} 