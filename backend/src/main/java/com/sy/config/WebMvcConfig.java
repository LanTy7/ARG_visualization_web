package com.sy.config;

import com.sy.interceptor.AdminAuthInterceptor;
import com.sy.interceptor.JwtInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private JwtInterceptor jwtInterceptor;

    @Autowired
    private AdminAuthInterceptor adminAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/**")    // 拦截所有请求
                .excludePathPatterns(    // 排除不需要认证的路径
                    "/api/auth/login",           // 登录
                    "/api/auth/send-code",       // 发送注册验证码
                    "/api/auth/send-login-code", // 发送登录验证码
                    "/api/auth/send-reset-code", // 发送重置密码验证码
                    "/api/auth/register",        // 注册
                    "/api/auth/reset-password",  // 重置密码
                    "/api/auth/logout",          // 登出
                    "/api/auth/verify-token",    // token验证
                    "/error",                    // 错误页面
                    "/swagger-ui/**",            // Swagger UI
                    "/v3/api-docs/**",           // OpenAPI 文档
                    "/uploads/**",               // 允许访问上传文件
                    "/api/admin/**"              // 管理员接口
                );

        // 注册管理员权限拦截器，拦截 /api/admin/** 路径
        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/api/admin/**")
                .excludePathPatterns(
                    "/api/admin/login",
                    "/api/admin/send-register-code",  // 发送管理员注册验证码
                    "/api/admin/register-admin"       // 管理员注册
                );
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*") // 允许所有来源模式
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // 允许的HTTP方法
                .allowedHeaders("*") // 允许所有请求头
                .allowCredentials(true) // 允许发送Cookie
                .maxAge(3600); // 预检请求的缓存时间
    }
} 