package com.sy.controller;

import com.sy.vo.Result;
import com.sy.pojo.User;
import com.sy.service.LoginService;
import com.sy.util.JwtUtil;
import com.sy.vo.LoginRequest;
import com.sy.vo.LoginResponse;
import com.sy.vo.RegisterRequest;
import com.sy.vo.VerificationCodeRequest;
import com.sy.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

/**
 * 认证控制器
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    
    @Autowired
    private LoginService loginService;
    
    @Autowired
    private JwtUtil jwtUtil;
    
    @Autowired
    private UserMapper userMapper;
    
    /**
     * 发送邮箱验证码
     */
    @PostMapping("/send-code")
    public Result<String> sendVerificationCode(@RequestBody @Valid VerificationCodeRequest request) {
        try {
            loginService.sendVerificationCode(request.getEmail());
            return Result.success("验证码已发送，请查收邮件");
        } catch (RuntimeException e) {
            String errorMsg;
            if (e.getMessage().contains("邮箱格式无效")) {
                errorMsg = "邮箱格式不正确";
            } else if (e.getMessage().contains("已被注册")) {
                errorMsg = "该邮箱已被注册";
            } else {
                errorMsg = "发送验证码失败：" + e.getMessage();
            }
            return Result.error(errorMsg);
        } catch (Exception e) {
            return Result.error("系统错误，请稍后重试");
        }
    }
    
    /**
     * 用户注册
     */
    @PostMapping("/register")
    public Result<String> register(@RequestBody @Valid RegisterRequest registerRequest) {
        try {
            loginService.register(registerRequest);
            return Result.success("注册成功");
        } catch (RuntimeException e) {
            String errorMsg;
            if (e.getMessage().contains("验证码无效")) {
                errorMsg = "验证码无效或已过期";
            } else if (e.getMessage().contains("邮箱")) {
                errorMsg = "该邮箱已被注册";
            } else if (e.getMessage().contains("用户名")) {
                errorMsg = "该用户名已被使用";
            } else if (e.getMessage().contains("密码")) {
                errorMsg = "密码格式不正确，密码长度应为6-20个字符";
            } else if (e.getMessage().contains("validation")) {
                errorMsg = "注册信息不完整或格式不正确：" + e.getMessage();
            } else {
                errorMsg = "注册失败：" + e.getMessage();
            }
            return Result.error(errorMsg);
        } catch (Exception e) {
            return Result.error("系统错误，请稍后重试");
        }
    }
    
    /**
     * 用户登录
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody @Valid LoginRequest loginRequest) {
        try {
            // 验证用户身份
            User user = loginService.login(loginRequest.getIdentifier(), loginRequest.getPassword(), loginRequest.getCode());
            
            // 生成Token
            String token = loginService.generateToken(user);
            
            // 记录登录日志
            loginService.recordLogin(user.getUserId());
            
            // 返回登录响应
            return Result.success(new LoginResponse(token, user));
        } catch (RuntimeException e) {
            String errorMsg;
            if (e.getMessage().contains("not found")) {
                errorMsg = "该邮箱未注册";
            } else if (e.getMessage().contains("password")) {
                errorMsg = "密码错误";
            } else {
                errorMsg = "登录失败：" + e.getMessage();
            }
            return Result.error(errorMsg);
        } catch (Exception e) {
            return Result.error("系统错误，请稍后重试");
        }
    }
    
    /**
     * 退出登录
     * @param request HTTP请求
     * @return 退出结果
     */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        try {
            // 从请求头中获取 token
            String token = request.getHeader("Authorization");
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7); // 移除 "Bearer " 前缀
                
                // 将 token 加入黑名单
                jwtUtil.addToBlacklist(token);
                
                // 记录退出日志
                Long userId = jwtUtil.getUserIdFromToken(token);
                if (userId != null) {
                    loginService.recordLogout(userId);
                }
            }
            
            return Result.success();
        } catch (Exception e) {
            return Result.error("退出登录失败");
        }
    }
    
    /**
     * 发送登录验证码（开发/测试用，直接返回验证码）
     */
    @PostMapping("/send-login-code")
    public Result<String> sendLoginCode(@RequestBody @Valid VerificationCodeRequest request) {
        try {
            String code = loginService.sendLoginCode(request.getEmail());
            return Result.success(code); // 直接返回验证码
        } catch (RuntimeException e) {
            String errorMsg;
            if (e.getMessage().contains("邮箱格式无效")) {
                errorMsg = "邮箱格式不正确";
            } else if (e.getMessage().contains("未注册")) {
                errorMsg = "该邮箱未注册";
            } else {
                errorMsg = "发送验证码失败：" + e.getMessage();
            }
            return Result.error(errorMsg);
        } catch (Exception e) {
            return Result.error("系统错误，请稍后重试");
        }
    }

    /**
     * 发送重置密码验证码
     */
    @PostMapping("/send-reset-code")
    public Result<String> sendResetCode(@RequestBody @Valid VerificationCodeRequest request) {
        try {
            loginService.sendResetCode(request.getEmail());
            return Result.success("验证码已发送，请查收邮件");
        } catch (RuntimeException e) {
            String errorMsg;
            if (e.getMessage().contains("邮箱格式无效")) {
                errorMsg = "邮箱格式不正确";
            } else if (e.getMessage().contains("未注册")) {
                errorMsg = "该邮箱未注册";
            } else {
                errorMsg = "发送验证码失败：" + e.getMessage();
            }
            return Result.error(errorMsg);
        } catch (Exception e) {
            return Result.error("系统错误，请稍后重试");
        }
    }

    /**
     * 重置密码
     */
    @PostMapping("/reset-password")
    public Result<String> resetPassword(@RequestBody @Valid com.sy.vo.ResetPasswordRequest request) {
        try {
            loginService.resetPassword(request);
            return Result.success("密码重置成功");
        } catch (RuntimeException e) {
            String errorMsg;
            if (e.getMessage().contains("验证码无效")) {
                errorMsg = "验证码无效或已过期";
            } else if (e.getMessage().contains("邮箱")) {
                errorMsg = "该邮箱未注册";
            } else if (e.getMessage().contains("密码")) {
                errorMsg = "两次输入的新密码不一致";
            } else {
                errorMsg = "重置密码失败：" + e.getMessage();
            }
            return Result.error(errorMsg);
        } catch (Exception e) {
            return Result.error("系统错误，请稍后重试");
        }
    }

    /**
     * 验证token有效性
     */
    @GetMapping("/verify-token")
    public Result<Void> verifyToken(HttpServletRequest request) {
        try {
            // 从请求头中获取 token
            String token = request.getHeader("Authorization");
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7); // 移除 "Bearer " 前缀
                
                // 验证token
                if (jwtUtil.validateToken(token)) {
                    return Result.success();
                }
            }
            return Result.error("token无效");
        } catch (Exception e) {
            return Result.error("token验证失败");
        }
    }

    /**
     * 获取当前登录用户信息
     */
    @GetMapping("/me")
    public Result<User> getCurrentUser(HttpServletRequest request) {
        try {
            // 从请求头中获取 token
            String token = request.getHeader("Authorization");
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7); // 移除 "Bearer " 前缀
                
                // 从token中获取用户ID
                Long userId = jwtUtil.getUserIdFromToken(token);
                if (userId != null) {
                    // 获取用户信息
                    User user = userMapper.findById(userId);
                    if (user != null) {
                        return Result.success(user);
                    }
                }
            }
            return Result.error("未登录或登录已过期");
        } catch (Exception e) {
            return Result.error("获取用户信息失败");
        }
    }
} 