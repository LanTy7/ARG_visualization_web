package com.sy.service.impl;

import com.sy.pojo.User;
import com.sy.service.UserService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户服务实现
 * 注：原噬菌体识别系统不需要用户关注功能，已移除
 */
@Service
public class UserServiceImpl implements UserService {

    @Override
    public List<User> getFollowing(Long userId) {
        // 原噬菌体识别系统不需要用户关注功能
        return List.of();
    }

    @Override
    public List<User> getFollowers(Long userId) {
        // 原噬菌体识别系统不需要用户关注功能
        return List.of();
    }
} 