package com.sy.interceptor;

import com.sy.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

//import javax.servlet.http.HttpServletRequest;
//import javax.servlet.http.HttpServletResponse;

@Component
public class JwtInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 获取请求路径
        String requestURI = request.getRequestURI();
        
        // 对于认证相关的路径，直接放行
        if (requestURI.startsWith("/api/auth/")) {
            return true;
        }

        // 从请求头中获取Token
        String token = request.getHeader("Authorization");

        // 检查Token是否存在且以"Bearer "开头
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7); // 移除"Bearer "前缀

            // 验证Token的有效性
            if (jwtUtil.validateToken(token)) {
                // Token有效，可以将用户信息（如用户ID）存储到请求属性中供后续使用
                Long userId = jwtUtil.getUserIdFromToken(token);
                if (userId != null) {
                    request.setAttribute("userId", userId);
                    return true; // 继续处理请求
                }
            }
        }

        // Token无效或缺失，拒绝访问
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 设置状态码 401 Unauthorized
        // 可以添加更多的错误信息返回给客户端
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\": 0, \"msg\": \"Unauthorized\", \"data\": null}");

        return false; // 阻止请求继续
    }

    // postHandle 和 afterCompletion 方法这里暂不实现
} 