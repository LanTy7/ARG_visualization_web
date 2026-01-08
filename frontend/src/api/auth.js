import request from '@/utils/request';

// 发送登录验证码
export function sendLoginCode(email) {
  return request({
    url: '/auth/send-login-code',
    method: 'post',
    data: { email }
  });
}

// 用户登录
export function login(data) {
  return request({
    url: '/auth/login',
    method: 'post',
    data
  });
}

// 用户注册
export function register(data) {
  return request({
    url: '/auth/register',
    method: 'post',
    data
  });
}

// 发送注册验证码
export function sendVerificationCode(email) {
  return request({
    url: '/auth/send-code',
    method: 'post',
    data: { email }
  });
}

// 发送重置密码验证码
export function sendResetCode(email) {
  return request({
    url: '/auth/send-reset-code',
    method: 'post',
    data: { email }
  });
}

// 重置密码
export function resetPassword(data) {
  return request({
    url: '/auth/reset-password',
    method: 'post',
    data
  });
}

// 用户登出
export function logout() {
  return request({
    url: '/auth/logout',
    method: 'post'
  });
}

// 获取用户信息
export function getUserInfo() {
  return request({
    url: '/auth/me',
    method: 'get'
  });
}

