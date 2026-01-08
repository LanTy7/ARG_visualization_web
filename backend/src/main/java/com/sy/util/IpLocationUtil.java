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
     * 判断是否为内网IP
     */
    private boolean isPrivateIp(String ip) {
        if (ip == null || ip.isEmpty()) {
            return true;
        }
        // IPv6本地地址
        if (ip.equals("::1") || ip.equals("0:0:0:0:0:0:0:1")) {
            return true;
        }
        // IPv4本地地址
        if (ip.equals("127.0.0.1") || ip.equals("localhost")) {
            return true;
        }
        // 内网IP段：10.x.x.x, 172.16-31.x.x, 192.168.x.x
        if (ip.startsWith("10.") || ip.startsWith("192.168.")) {
            return true;
        }
        if (ip.startsWith("172.")) {
            try {
                int secondOctet = Integer.parseInt(ip.split("\\.")[1]);
                if (secondOctet >= 16 && secondOctet <= 31) {
                    return true;
                }
            } catch (Exception e) {
                // 忽略解析错误
            }
        }
        return false;
    }
    
    /**
     * 根据IP地址获取地理位置信息
     * @param ip IP地址
     * @return 地理位置信息，格式：省市区，如"浙江省杭州市"，如果解析失败返回null
     */
    public String getLocationByIp(String ip) {
        // 如果是内网IP，尝试获取服务器出口公网IP来定位
        if (isPrivateIp(ip)) {
            log.info("检测到内网IP: {}，尝试获取服务器出口IP进行定位", ip);
            return getLocationByServerOutboundIp();
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
    
    /**
     * 通过服务器出口IP获取地理位置（用于内网访问时）
     * 使用 ip-api.com 的无参数请求，会自动返回请求方（服务器）的公网IP信息
     */
    private String getLocationByServerOutboundIp() {
        try {
            // 不带IP参数，api会返回请求者的公网IP地理信息
            String url = "http://ip-api.com/json/?lang=zh-CN&fields=status,message,country,regionName,city,query";
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
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
                    String outboundIp = jsonNode.has("query") ? jsonNode.get("query").asText() : "";
                    String country = jsonNode.has("country") ? jsonNode.get("country").asText() : "";
                    String region = jsonNode.has("regionName") ? jsonNode.get("regionName").asText() : "";
                    String city = jsonNode.has("city") ? jsonNode.get("city").asText() : "";
                    
                    log.info("服务器出口IP: {}, 位置: {} {} {}", outboundIp, country, region, city);
                    
                    // 如果是中国，返回"省市"格式
                    if ("中国".equals(country) || "China".equals(country)) {
                        StringBuilder location = new StringBuilder();
                        if (region != null && !region.isEmpty()) {
                            location.append(region);
                        }
                        if (city != null && !city.isEmpty()) {
                            location.append(city);
                        }
                        return location.length() > 0 ? location.toString() : country;
                    } else {
                        // 非中国，返回"国家 省/州 城市"格式
                        StringBuilder location = new StringBuilder();
                        if (country != null && !country.isEmpty()) {
                            location.append(country);
                        }
                        if (region != null && !region.isEmpty()) {
                            location.append(" ").append(region);
                        }
                        if (city != null && !city.isEmpty()) {
                            location.append(" ").append(city);
                        }
                        return location.length() > 0 ? location.toString() : null;
                    }
                }
            }
            log.warn("获取服务器出口IP地理位置失败");
            return null;
        } catch (Exception e) {
            log.error("获取服务器出口IP地理位置失败: {}", e.getMessage());
            return null;
        }
    }
}

