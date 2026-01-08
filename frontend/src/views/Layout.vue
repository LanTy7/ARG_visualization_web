<template>
  <div class="layout-container">
    <!-- 左侧侧边栏 -->
    <aside class="sidebar">
      <div class="sidebar-header">
        <div class="logo">🧬</div>
        <div class="title">
          <h2>抗性基因识别</h2>
          <p>Antibiotic Identification</p>
        </div>
      </div>
      
      <el-menu
        :default-active="currentRoute"
        class="sidebar-menu"
        @select="handleMenuSelect"
      >
        <el-menu-item index="/">
          <el-icon><HomeFilled /></el-icon>
          <span>首页</span>
        </el-menu-item>
        
        <el-menu-item index="/upload">
          <el-icon><Upload /></el-icon>
          <span>文件上传</span>
        </el-menu-item>
        
        <el-menu-item index="/visualization">
          <el-icon><DataLine /></el-icon>
          <span>结果可视化</span>
        </el-menu-item>
        
        <el-menu-item index="/history">
          <el-icon><Clock /></el-icon>
          <span>历史记录</span>
        </el-menu-item>
        
        <el-menu-item index="/admin" v-if="isAdmin">
          <el-icon><Setting /></el-icon>
          <span>管理功能</span>
        </el-menu-item>
      </el-menu>
      
      <div class="sidebar-footer">
        <el-button
          v-if="!userStore.token"
          type="primary"
          size="large"
          class="auth-button"
          @click="handleLogin"
        >
          <el-icon><User /></el-icon>
          Log in
        </el-button>
        <el-button
          v-else
          type="danger"
          size="large"
          class="auth-button"
          @click="handleLogout"
        >
          <el-icon><SwitchButton /></el-icon>
          Log out
        </el-button>
      </div>
    </aside>
    
    <!-- 右侧内容区 -->
    <main class="main-content">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { ElMessageBox } from 'element-plus';
import {
  HomeFilled,
  Upload,
  DataLine,
  Clock,
  User,
  SwitchButton,
  Setting,
} from '@element-plus/icons-vue';
import { useUserStore } from '@/stores/user';
import { logout } from '@/api/auth';

const router = useRouter();
const route = useRoute();
const userStore = useUserStore();

const currentRoute = computed(() => route.path);

// 判断是否是管理员（使用store中的getter）
const isAdmin = computed(() => {
  const admin = userStore.isAdmin;
  // 调试输出
  if (userStore.isLoggedIn) {
    console.log('Layout - 用户信息:', userStore.userInfo);
    console.log('Layout - 用户角色:', userStore.role);
    console.log('Layout - 是否为管理员:', admin);
  }
  return admin;
});

// 菜单选择处理
const handleMenuSelect = (index) => {
  router.push(index);
};

// 处理登录
const handleLogin = () => {
  router.push('/login');
};

// 处理登出
const handleLogout = async () => {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    });
    
    await logout();
    userStore.logout();
    router.push('/login');
  } catch (error) {
    // 用户取消操作
  }
};

// 组件挂载时，如果已登录但没有role信息，重新获取用户信息
onMounted(async () => {
  if (userStore.isLoggedIn && (!userStore.userInfo || !userStore.userInfo.role)) {
    try {
      console.log('检测到用户信息不完整，重新获取用户信息...');
      await userStore.fetchUserInfo();
      console.log('用户信息已更新:', userStore.userInfo);
    } catch (error) {
      console.error('获取用户信息失败:', error);
    }
  }
});
</script>

<style scoped>
.layout-container {
  display: flex;
  height: 100vh;
  background: linear-gradient(135deg, #0f0f1e 0%, #1a1a2e 100%);
}

.sidebar {
  width: 260px;
  background: linear-gradient(180deg, #16213e 0%, #0f3460 100%);
  border-right: 1px solid rgba(0, 255, 255, 0.1);
  display: flex;
  flex-direction: column;
  box-shadow: 2px 0 16px rgba(0, 255, 255, 0.1);
}

.sidebar-header {
  padding: 24px 20px;
  display: flex;
  align-items: center;
  border-bottom: 1px solid rgba(0, 255, 255, 0.2);
  background: linear-gradient(135deg, #0a192f 0%, #112240 100%);
  position: relative;
  overflow: hidden;
}

.sidebar-header::before {
  content: '';
  position: absolute;
  top: -50%;
  left: -50%;
  width: 200%;
  height: 200%;
  background: repeating-linear-gradient(
    45deg,
    transparent,
    transparent 10px,
    rgba(0, 255, 255, 0.03) 10px,
    rgba(0, 255, 255, 0.03) 20px
  );
  animation: slide 20s linear infinite;
}

@keyframes slide {
  0% { transform: translate(0, 0); }
  100% { transform: translate(50px, 50px); }
}

.logo {
  font-size: 48px;
  margin-right: 12px;
  filter: drop-shadow(0 0 8px rgba(0, 255, 255, 0.5));
  position: relative;
  z-index: 1;
}

.title {
  position: relative;
  z-index: 1;
}

.title h2 {
  margin: 0;
  font-size: 18px;
  color: #00ffff;
  font-weight: 600;
  text-shadow: 0 0 10px rgba(0, 255, 255, 0.5);
}

.title p {
  margin: 4px 0 0 0;
  font-size: 12px;
  color: rgba(0, 255, 255, 0.7);
  text-shadow: 0 0 5px rgba(0, 255, 255, 0.3);
}

.sidebar-menu {
  flex: 1;
  border: none;
  padding: 16px 12px;
  background: transparent;
}

:deep(.el-menu) {
  background: transparent;
}

:deep(.el-menu-item) {
  border-radius: 8px;
  margin-bottom: 4px;
  height: 48px;
  line-height: 48px;
  transition: all 0.3s;
  color: rgba(255, 255, 255, 0.7);
  border: 1px solid transparent;
}

:deep(.el-menu-item:hover) {
  background: rgba(0, 255, 255, 0.1);
  color: #00ffff;
  border-color: rgba(0, 255, 255, 0.3);
  transform: translateX(4px);
  box-shadow: 0 0 10px rgba(0, 255, 255, 0.2);
}

:deep(.el-menu-item.is-active) {
  background: linear-gradient(135deg, rgba(0, 255, 255, 0.2) 0%, rgba(0, 150, 255, 0.2) 100%);
  color: #00ffff;
  border-color: rgba(0, 255, 255, 0.5);
  box-shadow: 0 0 15px rgba(0, 255, 255, 0.3), inset 0 0 10px rgba(0, 255, 255, 0.1);
}

:deep(.el-menu-item.is-active .el-icon) {
  color: #00ffff;
  filter: drop-shadow(0 0 5px rgba(0, 255, 255, 0.5));
}

.sidebar-footer {
  padding: 16px;
  border-top: 1px solid rgba(0, 255, 255, 0.2);
  background: linear-gradient(180deg, transparent 0%, rgba(0, 255, 255, 0.05) 100%);
}

.auth-button {
  width: 100%;
  height: 48px;
  font-size: 16px;
  font-weight: 600;
  border-radius: 8px;
  transition: all 0.3s;
  border: 1px solid rgba(0, 255, 255, 0.3);
}

.auth-button.el-button--primary {
  background: linear-gradient(135deg, rgba(0, 255, 255, 0.2) 0%, rgba(0, 150, 255, 0.2) 100%);
  color: #00ffff;
  border-color: rgba(0, 255, 255, 0.5);
}

.auth-button.el-button--primary:hover {
  background: linear-gradient(135deg, rgba(0, 255, 255, 0.3) 0%, rgba(0, 150, 255, 0.3) 100%);
  box-shadow: 0 0 20px rgba(0, 255, 255, 0.4);
  transform: translateY(-2px);
}

.auth-button.el-button--danger {
  background: linear-gradient(135deg, rgba(255, 50, 100, 0.2) 0%, rgba(255, 0, 50, 0.2) 100%);
  color: #ff4466;
  border-color: rgba(255, 50, 100, 0.5);
}

.auth-button.el-button--danger:hover {
  background: linear-gradient(135deg, rgba(255, 50, 100, 0.3) 0%, rgba(255, 0, 50, 0.3) 100%);
  box-shadow: 0 0 20px rgba(255, 50, 100, 0.4);
  transform: translateY(-2px);
}

.main-content {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  background: linear-gradient(135deg, #0f0f1e 0%, #1a1a2e 100%);
}
</style>

