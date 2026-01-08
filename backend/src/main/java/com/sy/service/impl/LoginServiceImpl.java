package com.sy.service.impl;

import com.sy.mapper.LoginLogMapper;
import com.sy.mapper.UserMapper;
import com.sy.pojo.LoginLog;
import com.sy.pojo.User;
import com.sy.service.LoginService;
import com.sy.service.EmailService;
import com.sy.util.JwtUtil;
import com.sy.util.EmailValidator;
import com.sy.util.IpLocationUtil;
import com.sy.vo.RegisterRequest;
import com.sy.vo.ResetPasswordRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

@Service
public class LoginServiceImpl implements LoginService {
    
    @Autowired
    private UserMapper userMapper;
    
    @Autowired
    private LoginLogMapper loginLogMapper;
    
    @Autowired
    private JwtUtil jwtUtil;
    
    @Autowired
    private EmailValidator emailValidator;
    
    @Autowired
    private EmailService emailService;
    
    @Autowired
    private IpLocationUtil ipLocationUtil;
    
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    
    @Override
    public void sendVerificationCode(String email) {
        // 验证邮箱格式
        if (!emailValidator.isValidEmailFormat(email)) {
            throw new RuntimeException("邮箱格式无效");
        }
        
        // 检查邮箱是否已被注册
        if (userMapper.findByEmail(email) != null) {
            throw new RuntimeException("该邮箱已被注册");
        }
        
        // 生成并发送验证码
        String verificationCode = ((EmailServiceImpl) emailService).generateVerificationCode();
        emailService.sendVerificationEmail(email, verificationCode);
    }
    
    @Override
    @Transactional
    public void register(RegisterRequest request) {
        // 验证两次密码是否一致
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("两次输入的密码不一致");
        }
        
        // 验证邮箱格式
        if (!emailValidator.isValidEmailFormat(request.getEmail())) {
            throw new RuntimeException("邮箱格式无效");
        }
        
        // 验证邮箱验证码
        if (!emailService.verifyCode(request.getEmail(), request.getVerificationCode())) {
            throw new RuntimeException("验证码无效或已过期");
        }
        
        // 检查用户名是否已存在
        if (userMapper.countByUsername(request.getUsername()) > 0) {
            throw new RuntimeException("用户名已存在");
        }
        
        // 检查邮箱是否已存在（再次检查，以防在发送验证码后到注册这段时间内被注册）
        if (userMapper.findByEmail(request.getEmail()) != null) {
            throw new RuntimeException("邮箱已被注册");
        }
        
        // 创建新用户
        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setAvatarUrl("https://api.dicebear.com/7.x/avataaars/svg?seed=" + request.getUsername());
        user.setRole("USER");  // 设置默认角色为普通用户
        user.setStatus("ACTIVE");  // 设置默认状态为活跃
        user.setCreatedAt(LocalDateTime.now());
        
        userMapper.insert(user);
    }
    
    @Override
    public User login(String identifier, String password, String code) {
        // 校验验证码
        org.springframework.data.redis.core.StringRedisTemplate redisTemplate = ((com.sy.service.impl.EmailServiceImpl) emailService).redisTemplate;
        String codeInRedis = redisTemplate.opsForValue().get("login:code:" + identifier);
        if (codeInRedis == null || !codeInRedis.equals(code)) {
            throw new RuntimeException("验证码无效或已过期");
        }
        // 校验邮箱和密码
        User user = userMapper.findByEmail(identifier);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new RuntimeException("邮箱或密码错误");
        }
        // 验证成功后删除验证码
        redisTemplate.delete("login:code:" + identifier);
        return user;
    }
    
    @Override
    public String generateToken(User user) {
        return jwtUtil.generateToken(user.getUserId(), "user");
    }
    
    @Override
    public void recordLogin(Long userId) {
        LoginLog log = new LoginLog();
        log.setUserId(userId);
        log.setLoginTime(LocalDateTime.now());
        log.setStatus("SUCCESS");
        
        // 获取当前请求
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            
            // 获取IP地址
            String ip = request.getHeader("X-Forwarded-For");
            if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("Proxy-Client-IP");
            }
            if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("WL-Proxy-Client-IP");
            }
            if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getRemoteAddr();
            }
            log.setIpAddress(ip);
            
            // 获取IP地理位置
            try {
                String location = ipLocationUtil.getLocationByIp(ip);
                log.setLocation(location);
            } catch (Exception e) {
                // 如果获取地理位置失败，记录错误但不影响登录流程
                System.err.println("获取IP地理位置失败: IP=" + ip + ", 错误=" + e.getMessage());
            }
            
            // 获取设备信息
            String userAgent = request.getHeader("User-Agent");
            log.setUserAgent(userAgent);
        }
        
        loginLogMapper.insert(log);
        
        // 更新用户最后登录时间
        User user = userMapper.findById(userId);
        if (user != null) {
            user.setLastLoginAt(LocalDateTime.now());
            userMapper.updateById(user);
        }
    }
    
    @Override
    public Long validateToken(String token) {
        if (jwtUtil.validateToken(token)) {
            return jwtUtil.getUserIdFromToken(token);
        }
        return null;
    }
    
    @Override
    @Transactional
    public void recordLogout(Long userId) {
        // 查找用户最后一条登录日志，更新退出时间
        LoginLog lastLog = loginLogMapper.findLastLoginByUserId(userId);
        if (lastLog != null && lastLog.getLogoutTime() == null) {
            lastLog.setLogoutTime(LocalDateTime.now());
            loginLogMapper.updateById(lastLog);
        }
        
    }
    
    @Override
    public String sendLoginCode(String email) {
        // 验证邮箱格式
        if (!emailValidator.isValidEmailFormat(email)) {
            throw new RuntimeException("邮箱格式无效");
        }
        // 检查邮箱是否已注册（登录验证码必须已注册）
        if (userMapper.findByEmail(email) == null) {
            throw new RuntimeException("该邮箱未注册");
        }
        // 生成验证码
        String code = ((com.sy.service.impl.EmailServiceImpl) emailService).generateVerificationCode();
        // 保存到Redis，5分钟有效
        org.springframework.data.redis.core.StringRedisTemplate redisTemplate = ((com.sy.service.impl.EmailServiceImpl) emailService).redisTemplate;
        redisTemplate.opsForValue().set("login:code:" + email, code, 5, java.util.concurrent.TimeUnit.MINUTES);
        // 发送登录验证码邮件
        emailService.sendCodeEmailOnly(email, code, "登录验证码");
        return code;
    }
    
    @Override
    public void sendResetCode(String email) {
        // 校验邮箱格式
        if (!emailValidator.isValidEmailFormat(email)) {
            throw new RuntimeException("邮箱格式无效");
        }
        // 检查邮箱是否已注册
        if (userMapper.findByEmail(email) == null) {
            throw new RuntimeException("该邮箱未注册");
        }
        // 生成验证码
        String code = ((com.sy.service.impl.EmailServiceImpl) emailService).generateVerificationCode();
        // 保存到Redis，5分钟有效
        org.springframework.data.redis.core.StringRedisTemplate redisTemplate = ((com.sy.service.impl.EmailServiceImpl) emailService).redisTemplate;
        redisTemplate.opsForValue().set("reset:code:" + email, code, 5, java.util.concurrent.TimeUnit.MINUTES);
        // 发送重置密码验证码邮件
        emailService.sendCodeEmailOnly(email, code, "重置密码验证码");
    }
    
    @Override
    @org.springframework.transaction.annotation.Transactional
    public void resetPassword(ResetPasswordRequest request) {
        // 校验两次密码
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("两次输入的新密码不一致");
        }
        // 校验邮箱格式
        if (!emailValidator.isValidEmailFormat(request.getEmail())) {
            throw new RuntimeException("邮箱格式无效");
        }
        // 校验验证码
        org.springframework.data.redis.core.StringRedisTemplate redisTemplate = ((com.sy.service.impl.EmailServiceImpl) emailService).redisTemplate;
        String codeInRedis = redisTemplate.opsForValue().get("reset:code:" + request.getEmail());
        if (codeInRedis == null || !codeInRedis.equals(request.getCode())) {
            throw new RuntimeException("验证码无效或已过期");
        }
        // 检查用户是否存在
        User user = userMapper.findByEmail(request.getEmail());
        if (user == null) {
            throw new RuntimeException("该邮箱未注册");
        }
        // 更新密码
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userMapper.update(user);
        // 删除验证码
        redisTemplate.delete("reset:code:" + request.getEmail());
    }
} 