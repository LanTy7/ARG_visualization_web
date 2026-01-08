import { defineStore } from 'pinia';
import { getUserInfo } from '@/api/auth';

export const useUserStore = defineStore('user', {
  state: () => {
    let userInfo = null;
    try {
      const stored = localStorage.getItem('userInfo');
      if (stored && stored !== 'undefined' && stored !== 'null') {
        userInfo = JSON.parse(stored);
      }
    } catch (e) {
      console.error('解析用户信息失败:', e);
      localStorage.removeItem('userInfo');
    }
    
    return {
      token: localStorage.getItem('token') || '',
      userInfo,
    };
  },
  
  getters: {
    isLoggedIn: (state) => !!state.token,
    username: (state) => state.userInfo?.username || '',
    email: (state) => state.userInfo?.email || '',
    role: (state) => state.userInfo?.role || 'USER',
    isAdmin: (state) => state.userInfo?.role === 'ADMIN',
  },
  
  actions: {
    // 设置 token
    setToken(token) {
      this.token = token;
      localStorage.setItem('token', token);
    },
    
    // 设置用户信息
    setUserInfo(userInfo) {
      this.userInfo = userInfo;
      localStorage.setItem('userInfo', JSON.stringify(userInfo));
    },
    
    // 获取用户信息
    async fetchUserInfo() {
      try {
        const res = await getUserInfo();
        this.setUserInfo(res.data);
        return res.data;
      } catch (error) {
        console.error('获取用户信息失败：', error);
        throw error;
      }
    },
    
    // 登出
    logout() {
      this.token = '';
      this.userInfo = null;
      localStorage.removeItem('token');
      localStorage.removeItem('userInfo');
    },
  },
});

