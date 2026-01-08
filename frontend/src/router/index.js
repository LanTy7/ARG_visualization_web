import { createRouter, createWebHistory } from 'vue-router';
import { useUserStore } from '@/stores/user';
import { ElMessage } from 'element-plus';

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { requiresAuth: false },
  },
  
  {
    path: '/',
    component: () => import('@/views/Layout.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: '',
        name: 'Home',
        component: () => import('@/views/Home.vue'),
        meta: { title: '首页' },
      },
      {
        path: '/upload',
        name: 'Upload',
        component: () => import('@/views/Upload.vue'),
        meta: { title: '文件上传' },
      },
      {
        path: '/visualization',
        name: 'Visualization',
        component: () => import('@/views/Visualization.vue'),
        meta: { title: '结果可视化' },
      },
      {
        path: '/visualization-arg',
        name: 'VisualizationArg',
        component: () => import('@/views/Visualization_arg.vue'),
        meta: { title: 'ARG 结果可视化' },
      },
      {
        path: '/history',
        name: 'History',
        component: () => import('@/views/History.vue'),
        meta: { title: '历史记录' },
      },
      {
        path: '/admin',
        name: 'Admin',
        component: () => import('@/views/Admin.vue'),
        meta: { title: '管理功能', requiresAdmin: true },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/',
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

// 路由守卫
router.beforeEach((to, from, next) => {
  const userStore = useUserStore();
  
  // 设置页面标题
  if (to.meta.title) {
    document.title = `${to.meta.title} - 抗性基因识别系统`;
  } else {
    document.title = '抗性基因识别系统';
  }
  
  // 🔧 临时禁用登录检查，方便预览页面
  // TODO: 正式使用时取消下面的注释，启用登录验证
  /*
  // 检查是否需要认证
  if (to.meta.requiresAuth !== false) {
    if (!userStore.isLoggedIn) {
      ElMessage.warning('请先登录');
      next('/login');
      return;
    }
  }
  
  // 如果已登录，不允许访问登录页
  if (to.path === '/login' && userStore.isLoggedIn) {
    next('/');
    return;
  }
  */
  
  // 检查管理员权限（这个始终生效）
  if (to.meta.requiresAdmin) {
    if (!userStore.isLoggedIn) {
      ElMessage.warning('请先登录');
      next('/login');
      return;
    }
    
    if (!userStore.isAdmin) {
      ElMessage.error('需要管理员权限才能访问');
      next('/');
      return;
    }
  }
  
  next();
});

export default router;

