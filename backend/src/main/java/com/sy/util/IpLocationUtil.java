package com.sy.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * IP地理位置解析工具类
 * 使用免费的IP地理位置API服务
 */
@Slf4j
@Component
public class IpLocationUtil {
    
    private static final String IP_API_URL = "http://ip-api.com/json/%s?lang=zh-CN&fields=status,message,country,regionName,city";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 根据IP地址获取地理位置信息
     * @param ip IP地址
     * @return 地理位置信息，格式：省市区，如"浙江省杭州市"，如果解析失败返回null
     */
    public String getLocationByIp(String ip) {
        if (ip == null || ip.isEmpty() || ip.equals("127.0.0.1") || ip.equals("localhost")) {
            return "本地";
        }
        
        try {
            String url = String.format(IP_API_URL, ip);
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                JsonNode jsonNode = objectMapper.readTree(response.toString());
                String status = jsonNode.has("status") ? jsonNode.get("status").asText() : "";
                
                if ("success".equals(status)) {
                    String country = jsonNode.has("country") ? jsonNode.get("country").asText() : "";
                    String region = jsonNode.has("regionName") ? jsonNode.get("regionName").asText() : "";
                    String city = jsonNode.has("city") ? jsonNode.get("city").asText() : "";
                    
                    // 如果是中国，返回"省市区"格式
                    if ("中国".equals(country) || "China".equals(country)) {
                        StringBuilder location = new StringBuilder();
                        if (region != null && !region.isEmpty()) {
                            location.append(region);
                        }
                        if (city != null && !city.isEmpty()) {
                            if (location.length() > 0) {
                                location.append(city);
                            } else {
                                location.append(city);
                            }
                        }
                        return location.length() > 0 ? location.toString() : country;
                    } else {
                        // 非中国，返回"国家 省/州 城市"格式
                        StringBuilder location = new StringBuilder();
                        if (country != null && !country.isEmpty()) {
                            location.append(country);
                        }
                        if (region != null && !region.isEmpty()) {
                            if (location.length() > 0) {
                                location.append(" ").append(region);
                            } else {
                                location.append(region);
                            }
                        }
                        if (city != null && !city.isEmpty()) {
                            if (location.length() > 0) {
                                location.append(" ").append(city);
                            } else {
                                location.append(city);
                            }
                        }
                        return location.length() > 0 ? location.toString() : "未知";
                    }
                } else {
                    log.warn("IP地理位置解析失败: IP={}, 响应={}", ip, response.toString());
                    return null;
                }
            } else {
                log.warn("IP地理位置API请求失败: IP={}, 响应码={}", ip, responseCode);
                return null;
            }
        } catch (Exception e) {
            log.error("获取IP地理位置失败: IP={}, 错误={}", ip, e.getMessage());
            return null;
        }
    }
}

