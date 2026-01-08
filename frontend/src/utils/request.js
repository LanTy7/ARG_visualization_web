import axios from 'axios';
import { ElMessage } from 'element-plus';

const API_BASE_URL = import.meta.env?.VITE_API_BASE_URL || '/api';

// 创建 axios 实例
const request = axios.create({
  baseURL: API_BASE_URL,
  timeout: 300000, // 5分钟超时，用于大文件上传
  // 自定义 transformRequest，确保 FormData 不被转换
  transformRequest: [(data, headers) => {
    // 如果是 FormData，不进行任何转换，让浏览器自动处理
    if (data instanceof FormData) {
      // 删除 Content-Type，让浏览器自动设置 multipart/form-data 和 boundary
      delete headers['Content-Type'];
      delete headers['content-type'];
      return data;
    }
    // 其他数据使用默认的 JSON 序列化
    if (typeof data === 'object' && data !== null) {
      headers['Content-Type'] = 'application/json';
      return JSON.stringify(data);
    }
    return data;
  }]
});

// 请求拦截器
request.interceptors.request.use(
  (config) => {
    // 处理 FormData：确保 Content-Type 被删除，让浏览器自动设置
    if (config.data instanceof FormData) {
      // 删除 Content-Type，让浏览器自动设置 multipart/form-data 和 boundary
      delete config.headers['Content-Type'];
      delete config.headers['content-type'];
    }
    
    // 从 localStorage 获取 token
    const token = localStorage.getItem('token');
    
    // 登录相关接口（发送验证码、登录）不需要 token
    const publicUrls = [
      '/auth/send-login-code', 
      '/auth/send-code', 
      '/auth/login', 
      '/auth/register',
      '/auth/send-reset-code',
      '/auth/reset-password'
    ];
    const isPublicUrl = publicUrls.some(url => config.url.includes(url));
    
    if (!isPublicUrl && token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    
    // 调试：检查请求配置
    console.log('=== 请求拦截器 ===');
    console.log('完整URL:', config.baseURL + config.url);
    console.log('请求URL路径:', config.url);
    console.log('是否为公共接口:', isPublicUrl);
    console.log('Token存在:', !!token);
    console.log('是否添加Authorization:', !isPublicUrl && !!token);
    console.log('Authorization header:', config.headers.Authorization);
    console.log('Content-Type:', config.headers['Content-Type'] || config.headers['content-type'] || '未设置（FormData）');
    console.log('Is FormData:', config.data instanceof FormData);
    if (config.data instanceof FormData) {
      console.log('FormData entries:');
      for (let pair of config.data.entries()) {
        console.log('  ', pair[0], ':', pair[1] instanceof File ? `File(${pair[1].name}, ${pair[1].size} bytes)` : pair[1]);
      }
    }
    
    return config;
  },
  (error) => {
    console.error('请求错误：', error);
    return Promise.reject(error);
  }
);

// 响应拦截器
request.interceptors.response.use(
  (response) => {
    const res = response.data;
    
    // 如果返回的状态码不是 0，说明接口请求失败
    if (res.code !== 0) {
      // 401 特殊处理：如果在登录页，不显示"登录已过期"
      const isLoginPage = window.location.pathname.includes('/login') || window.location.hash.includes('/login');
      
      if (res.code === 401 && !isLoginPage) {
        // 不在登录页，说明是 token 过期
        ElMessage.error('登录已过期，请重新登录');
        localStorage.removeItem('token');
        localStorage.removeItem('userInfo');
        window.location.href = '/#/login';
      } else {
        // 显示后端返回的具体错误信息
        ElMessage.error(res.message || '请求失败');
      }
      
      return Promise.reject(new Error(res.message || '请求失败'));
    }
    
    return res;
  },
  (error) => {
    console.error('=== 响应错误详情 ===');
    console.error('错误对象:', error);
    console.error('响应状态:', error.response?.status);
    console.error('响应数据:', error.response?.data);
    console.error('请求配置:', error.config);
    
    if (error.response) {
      const { status, data } = error.response;
      
      // 优先显示后端返回的错误消息
      let errorMsg = data?.msg || data?.message || data?.error || '';
      
      console.log('后端错误消息:', errorMsg);
      
      if (status === 400) {
        // 显示后端返回的具体验证错误
        ElMessage.error(errorMsg || '请求参数错误');
      } else if (status === 401) {
        // 如果在登录页，显示后端返回的具体错误（比如"邮箱不存在"）
        // 如果不在登录页，才显示"登录已过期"
        const isLoginPage = window.location.pathname.includes('/login') || window.location.hash.includes('/login');
        
        console.log('是否在登录页:', isLoginPage);
        console.log('401错误消息:', errorMsg);
        
        if (isLoginPage) {
          // 在登录页，显示具体错误信息
          ElMessage.error(errorMsg || '认证失败，请检查邮箱是否正确');
        } else {
          // 不在登录页，说明是 token 过期
          ElMessage.error('登录已过期，请重新登录');
          localStorage.removeItem('token');
          localStorage.removeItem('userInfo');
          window.location.href = '/#/login';
        }
      } else if (status === 403) {
        ElMessage.error('没有权限访问');
      } else if (status === 404) {
        ElMessage.error('请求的资源不存在');
      } else if (status === 413) {
        // 文件大小超限
        ElMessage.error(errorMsg || '文件大小超过限制，请选择较小的文件');
      } else if (status >= 500) {
        ElMessage.error('服务器错误，请稍后再试');
      } else {
        ElMessage.error(errorMsg || '请求失败');
      }
    } else if (error.request) {
      ElMessage.error('网络错误，请检查网络连接');
    } else {
      ElMessage.error('请求配置错误');
    }
    
    return Promise.reject(error);
  }
);

export default request;

