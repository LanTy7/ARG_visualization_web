<template>
  <div class="login-container">
    <div class="login-box">
      <div class="login-header">
        <div class="logo">🧬</div>
        <h1>原噬菌体识别系统</h1>
        <p>Prophage Detection System</p>
      </div>
      
      <!-- 标签页切换 -->
      <el-tabs v-model="activeTab" class="login-tabs">
        <!-- 登录标签页 -->
        <el-tab-pane label="登录" name="login">
          <el-form @submit.prevent="() => {}">
            <!-- 邮箱输入框 -->
            <el-form-item>
              <el-input
                v-model="email"
                placeholder="请输入邮箱"
                prefix-icon="Message"
                size="large"
                @keydown.enter.prevent
              />
            </el-form-item>
            
            <!-- 密码输入框 -->
            <el-form-item>
              <el-input
                v-model="password"
                type="password"
                placeholder="请输入密码"
                prefix-icon="Lock"
                size="large"
                show-password
                @keydown.enter.prevent
              />
            </el-form-item>
            
            <!-- 验证码输入框和获取验证码按钮 -->
            <el-form-item>
              <div class="code-input-group">
                <el-input
                  v-model="code"
                  placeholder="请输入验证码"
                  prefix-icon="Key"
                  size="large"
                  @keydown.enter.prevent
                />
                <el-button
                  native-type="button"
                  :disabled="countdown > 0"
                  size="large"
                  @click.prevent.stop="handleSendCode"
                >
                  {{ countdown > 0 ? `${countdown}秒后重试` : '获取验证码' }}
                </el-button>
              </div>
            </el-form-item>
            
            <!-- 登录按钮 -->
            <el-form-item>
              <el-button
                :loading="loading"
                type="primary"
                size="large"
                class="login-button"
                native-type="button"
                @click.prevent="handleLogin"
              >
                登录
              </el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
        
        <!-- 注册标签页 -->
        <el-tab-pane label="注册" name="register">
          <el-form @submit.prevent="() => {}">
            <!-- 用户名输入框 -->
            <el-form-item>
              <el-input
                v-model="registerForm.username"
                placeholder="请输入用户名"
                prefix-icon="User"
                size="large"
                @keydown.enter.prevent
              />
            </el-form-item>
            
            <!-- 邮箱输入框 -->
            <el-form-item>
              <el-input
                v-model="registerForm.email"
                placeholder="请输入邮箱"
                prefix-icon="Message"
                size="large"
                @keydown.enter.prevent
              />
            </el-form-item>
            
            <!-- 密码输入框 -->
            <el-form-item>
              <el-input
                v-model="registerForm.password"
                type="password"
                placeholder="请输入密码"
                prefix-icon="Lock"
                size="large"
                show-password
                @keydown.enter.prevent
              />
            </el-form-item>
            
            <!-- 确认密码输入框 -->
            <el-form-item>
              <el-input
                v-model="registerForm.confirmPassword"
                type="password"
                placeholder="请确认密码"
                prefix-icon="Lock"
                size="large"
                show-password
                @keydown.enter.prevent
              />
            </el-form-item>
            
            <!-- 验证码输入框和获取验证码按钮 -->
            <el-form-item>
              <div class="code-input-group">
                <el-input
                  v-model="registerForm.code"
                  placeholder="请输入验证码"
                  prefix-icon="Key"
                  size="large"
                  @keydown.enter.prevent
                />
                <el-button
                  native-type="button"
                  :disabled="registerCountdown > 0"
                  size="large"
                  @click.prevent.stop="handleSendRegisterCode"
                >
                  {{ registerCountdown > 0 ? `${registerCountdown}秒后重试` : '获取验证码' }}
                </el-button>
              </div>
            </el-form-item>
            
            <!-- 注册按钮 -->
            <el-form-item>
              <el-button
                :loading="registerLoading"
                type="primary"
                size="large"
                class="login-button"
                native-type="button"
                @click.prevent="handleRegister"
              >
                注册
              </el-button>
            </el-form-item>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { useUserStore } from '@/stores/user';
import { login, sendLoginCode, register, sendVerificationCode } from '@/api/auth';

const router = useRouter();
const userStore = useUserStore();

// 当前标签页
const activeTab = ref('login');

// 登录表单
const email = ref('');
const password = ref('');
const code = ref('');
const countdown = ref(0);
const loading = ref(false);

// 注册表单
const registerForm = reactive({
  username: '',
  email: '',
  password: '',
  confirmPassword: '',
  code: ''
});
const registerCountdown = ref(0);
const registerLoading = ref(false);

// 发送验证码
const handleSendCode = (event) => {
  if (event) {
    event.preventDefault();
    event.stopPropagation();
  }
  
  console.log('=====================================');
  console.log('=== 点击获取验证码 ===');
  console.log('邮箱:', email.value);
  console.log('当前页面路径:', window.location.pathname);
  console.log('当前页面Hash:', window.location.hash);
  console.log('localStorage token:', localStorage.getItem('token'));
  console.log('=====================================');
  
  if (!email.value) {
    ElMessage.warning('请先输入邮箱');
    return false;
  }
  
  // 验证邮箱格式
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  if (!emailRegex.test(email.value)) {
    ElMessage.warning('请输入正确的邮箱格式');
    return false;
  }
  
  console.log('✅ 准备发送验证码到:', email.value);
  
  sendLoginCode(email.value)
    .then(() => {
      console.log('✅✅✅ 验证码发送成功！');
      ElMessage.success('验证码已发送到您的邮箱');
      
      // 开始倒计时
      countdown.value = 60;
      const timer = setInterval(() => {
        countdown.value--;
        if (countdown.value <= 0) {
          clearInterval(timer);
        }
      }, 1000);
    })
    .catch((error) => {
      console.error('❌❌❌ 发送验证码失败：', error);
      console.error('错误详情:', error.response);
      // 错误信息已经在 request.js 的拦截器中显示了
    });
  
  return false;
};

// 登录
const handleLogin = (event) => {
  if (event) {
    event.preventDefault();
    event.stopPropagation();
  }
  
  console.log('=== 点击登录 ===');
  
  // 验证表单
  if (!email.value) {
    ElMessage.warning('请输入邮箱');
    return false;
  }
  
  if (!password.value) {
    ElMessage.warning('请输入密码');
    return false;
  }
  
  if (!code.value) {
    ElMessage.warning('请输入验证码');
    return false;
  }
  
  loading.value = true;
  
  login({
    identifier: email.value,
    password: password.value,
    code: code.value
  })
    .then((res) => {
      console.log('登录成功，返回数据:', res);
      
      // 保存 token 和用户信息
      userStore.setToken(res.data.token);
      userStore.setUserInfo(res.data.userInfo);
      
      ElMessage.success('登录成功');
      router.push('/');
    })
    .catch((error) => {
      console.error('登录失败：', error);
      // 错误信息已经在 request.js 的拦截器中显示了
    })
    .finally(() => {
      loading.value = false;
    });
  
  return false;
};

// 发送注册验证码
const handleSendRegisterCode = (event) => {
  if (event) {
    event.preventDefault();
    event.stopPropagation();
  }
  
  if (!registerForm.email) {
    ElMessage.warning('请先输入邮箱');
    return false;
  }
  
  // 验证邮箱格式
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  if (!emailRegex.test(registerForm.email)) {
    ElMessage.warning('请输入正确的邮箱格式');
    return false;
  }
  
  sendVerificationCode(registerForm.email)
    .then(() => {
      ElMessage.success('验证码已发送到您的邮箱');
      
      // 开始倒计时
      registerCountdown.value = 60;
      const timer = setInterval(() => {
        registerCountdown.value--;
        if (registerCountdown.value <= 0) {
          clearInterval(timer);
        }
      }, 1000);
    })
    .catch((error) => {
      console.error('发送验证码失败：', error);
    });
  
  return false;
};

// 注册
const handleRegister = (event) => {
  if (event) {
    event.preventDefault();
    event.stopPropagation();
  }
  
  console.log('=== 点击注册 ===');
  
  // 验证表单
  if (!registerForm.username) {
    ElMessage.warning('请输入用户名');
    return false;
  }
  
  // 验证用户名长度
  if (registerForm.username.length < 3 || registerForm.username.length > 20) {
    ElMessage.warning('用户名长度必须在3-20个字符之间');
    return false;
  }
  
  // 验证用户名格式（只能包含字母、数字、下划线和连字符）
  const usernameRegex = /^[a-zA-Z0-9_-]+$/;
  if (!usernameRegex.test(registerForm.username)) {
    ElMessage.warning('用户名只能包含字母、数字、下划线和连字符');
    return false;
  }
  
  if (!registerForm.email) {
    ElMessage.warning('请输入邮箱');
    return false;
  }
  
  // 验证邮箱格式
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  if (!emailRegex.test(registerForm.email)) {
    ElMessage.warning('请输入正确的邮箱格式');
    return false;
  }
  
  if (!registerForm.password) {
    ElMessage.warning('请输入密码');
    return false;
  }
  
  // 验证密码长度
  if (registerForm.password.length < 6 || registerForm.password.length > 20) {
    ElMessage.warning('密码长度必须在6-20个字符之间');
    return false;
  }
  
  if (!registerForm.confirmPassword) {
    ElMessage.warning('请确认密码');
    return false;
  }
  
  if (registerForm.password !== registerForm.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致');
    return false;
  }
  
  if (!registerForm.code) {
    ElMessage.warning('请输入验证码');
    return false;
  }
  
  // 验证验证码格式（6位数字）
  const codeRegex = /^\d{6}$/;
  if (!codeRegex.test(registerForm.code)) {
    ElMessage.warning('验证码必须是6位数字');
    return false;
  }
  
  registerLoading.value = true;
  
  register({
    username: registerForm.username,
    email: registerForm.email,
    password: registerForm.password,
    confirmPassword: registerForm.confirmPassword,
    verificationCode: registerForm.code
  })
    .then(() => {
      ElMessage.success('注册成功，请登录');
      // 切换到登录标签页
      activeTab.value = 'login';
      // 清空注册表单
      registerForm.username = '';
      registerForm.email = '';
      registerForm.password = '';
      registerForm.confirmPassword = '';
      registerForm.code = '';
    })
    .catch((error) => {
      console.error('注册失败：', error);
    })
    .finally(() => {
      registerLoading.value = false;
    });
  
  return false;
};
</script>

<style scoped>
.login-container {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.login-box {
  width: 450px;
  padding: 40px;
  background: white;
  border-radius: 16px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
}

.login-header {
  text-align: center;
  margin-bottom: 30px;
}

.logo {
  font-size: 60px;
  margin-bottom: 10px;
}

.login-header h1 {
  margin: 0 0 8px 0;
  font-size: 28px;
  color: #333;
}

.login-header p {
  margin: 0;
  font-size: 14px;
  color: #999;
}

.login-tabs {
  margin-bottom: 20px;
}

.code-input-group {
  display: flex;
  gap: 10px;
}

.code-input-group .el-input {
  flex: 1;
}

.login-button {
  width: 100%;
}

:deep(.el-form-item) {
  margin-bottom: 20px;
}

:deep(.el-tabs__nav-wrap::after) {
  display: none;
}
</style>
