package com.sy.service;

import com.sy.pojo.User;
import java.util.List;

public interface UserService {
    /**
     * 获取用户的关注列表
     * @param userId 用户ID
     * @return 关注用户列表
     */
    List<User> getFollowing(Long userId);

    /**
     * 获取用户的粉丝列表
     * @param userId 用户ID
     * @return 粉丝用户列表
     */
    List<User> getFollowers(Long userId);
} 