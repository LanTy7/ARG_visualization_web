package com.sy.interceptor;

import com.sy.mapper.UserMapper;
import com.sy.pojo.User;
import com.sy.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 管理员权限拦截器
 * 验证用户是否为管理员
 */
@Component
@RequiredArgsConstructor
public class AdminAuthInterceptor implements HandlerInterceptor {
    
    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 放行登录接口
        if (request.getRequestURI().contains("/admin/login") || 
            request.getRequestURI().contains("/auth/login")) {
            return true;
        }
        
        // 获取token
        String token = request.getHeader("Authorization");
        if (token == null || !token.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        
        token = token.substring(7);
        
        // 验证token并检查管理员权限
        if (!jwtUtil.validateToken(token)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        
        // 获取用户ID并检查是否为管理员
        Long userId = jwtUtil.getUserIdFromToken(token);
        if (userId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
        
        User user = userMapper.findById(userId);
        if (user == null || !"ADMIN".equals(user.getRole())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }
        
        return true;
    }
} 