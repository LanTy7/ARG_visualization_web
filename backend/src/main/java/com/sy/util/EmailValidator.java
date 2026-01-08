package com.sy.util;

import org.springframework.stereotype.Component;
import java.util.regex.Pattern;

@Component
public class EmailValidator {
    // 邮箱格式的正则表达式
    private static final String EMAIL_PATTERN = 
        "^[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)*@"
        + "[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$";
    
    private static final Pattern pattern = Pattern.compile(EMAIL_PATTERN);

    /**
     * 验证邮箱格式是否有效
     * @param email 要验证的邮箱地址
     * @return 如果邮箱格式有效返回true，否则返回false
     */
    public boolean isValidEmailFormat(String email) {
        if (email == null) {
            return false;
        }
        return pattern.matcher(email).matches();
    }

    /**
     * 验证邮箱域名是否存在
     * @param email 要验证的邮箱地址
     * @return 如果邮箱域名有效返回true，否则返回false
     */
    public boolean isValidEmailDomain(String email) {
        if (!isValidEmailFormat(email)) {
            return false;
        }
        
        String domain = email.substring(email.indexOf("@") + 1);
        try {
            // 尝试解析域名
            java.net.InetAddress.getByName(domain);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
} 