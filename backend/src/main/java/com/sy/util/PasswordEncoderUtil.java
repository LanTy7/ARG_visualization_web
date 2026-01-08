package com.sy.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码加密工具类
 * 用于生成BCrypt加密后的密码
 */
public class PasswordEncoderUtil {
    
    private static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    
    /**
     * 加密密码
     * @param rawPassword 原始密码
     * @return 加密后的密码
     */
    public static String encode(String rawPassword) {
        return encoder.encode(rawPassword);
    }
    
    /**
     * 验证密码
     * @param rawPassword 原始密码
     * @param encodedPassword 加密后的密码
     * @return 是否匹配
     */
    public static boolean matches(String rawPassword, String encodedPassword) {
        return encoder.matches(rawPassword, encodedPassword);
    }
    
    /**
     * 主方法：用于生成加密密码
     * 运行此方法可以生成加密后的密码，然后复制到SQL语句中
     */
    public static void main(String[] args) {
        // 测试密码
        String[] passwords = {
            "admin123",
            "admin888",
            "prophage123"
        };
        
        System.out.println("=".repeat(60));
        System.out.println("BCrypt密码加密工具");
        System.out.println("=".repeat(60));
        
        for (String password : passwords) {
            String encoded = encode(password);
            System.out.println("\n原始密码: " + password);
            System.out.println("加密后: " + encoded);
            System.out.println("验证结果: " + matches(password, encoded));
        }
        
        System.out.println("\n" + "=".repeat(60));
        System.out.println("使用说明：");
        System.out.println("1. 复制上面生成的加密密码");
        System.out.println("2. 在SQL语句中替换密码字段");
        System.out.println("3. 执行SQL创建管理员账号");
        System.out.println("=".repeat(60));
    }
}

