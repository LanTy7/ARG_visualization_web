<template>
  <div class="home-container">
    <el-card class="welcome-card">
      <div class="welcome-content">
        <div class="icon">🧬</div>
        <h1>欢迎使用抗性基因检测系统</h1>
        <p class="subtitle">基于深度学习的抗性基因识别和可视化平台</p>
        
        <div class="actions">
          <el-button type="primary" size="large" @click="router.push('/upload')">
            <el-icon><Upload /></el-icon>
            开始上传文件
          </el-button>
          <el-button size="large" @click="router.push('/history')">
            <el-icon><Clock /></el-icon>
            查看历史记录
          </el-button>
        </div>
      </div>
    </el-card>
    
    <!-- 快速统计 -->
    <el-row :gutter="24" class="stats-row">
      <el-col :span="8">
        <el-card class="stat-card">
          <div class="stat-content">
            <el-icon class="stat-icon" color="#409eff"><Document /></el-icon>
            <div class="stat-info">
              <div class="stat-value">{{ stats.totalFiles }}</div>
              <div class="stat-label">上传文件数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      
      <el-col :span="8">
        <el-card class="stat-card">
          <div class="stat-content">
            <el-icon class="stat-icon" color="#67c23a"><Checked /></el-icon>
            <div class="stat-info">
              <div class="stat-value">{{ stats.completedTasks }}</div>
              <div class="stat-label">完成任务数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      
      <el-col :span="8">
        <el-card class="stat-card">
          <div class="stat-content">
            <el-icon class="stat-icon" color="#e6a23c"><Loading /></el-icon>
            <div class="stat-info">
              <div class="stat-value">{{ stats.runningTasks }}</div>
              <div class="stat-label">运行中任务</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { Upload, Clock, Document, Checked, Loading } from '@element-plus/icons-vue';
import { getUserFiles } from '@/api/file';
import { getUserTasks } from '@/api/task';

const router = useRouter();

const stats = ref({
  totalFiles: 0,
  completedTasks: 0,
  runningTasks: 0,
});

// 获取统计数据
const fetchStats = async () => {
  try {
    const [filesRes, tasksRes] = await Promise.all([
      getUserFiles(),
      getUserTasks(),
    ]);
    
    stats.value.totalFiles = filesRes.data.length;
    
    const tasks = tasksRes.data;
    stats.value.completedTasks = tasks.filter(t => t.status === 'COMPLETED').length;
    stats.value.runningTasks = tasks.filter(t => t.status === 'RUNNING').length;
  } catch (error) {
    console.error('获取统计数据失败：', error);
    // 使用模拟数据进行预览
    stats.value.totalFiles = 5;
    stats.value.completedTasks = 3;
    stats.value.runningTasks = 1;
  }
};

onMounted(() => {
  fetchStats();
});
</script>

<style scoped>
.home-container {
  max-width: 1200px;
  margin: 0 auto;
}

.welcome-card {
  margin-bottom: 24px;
  background: linear-gradient(135deg, #0a192f 0%, #112240 100%);
  border: 1px solid rgba(0, 255, 255, 0.2);
  box-shadow: 0 8px 32px rgba(0, 255, 255, 0.2);
}

:deep(.welcome-card .el-card__body) {
  padding: 0;
}

.welcome-content {
  text-align: center;
  padding: 80px 40px;
  background: linear-gradient(135deg, rgba(10, 25, 47, 0.95) 0%, rgba(17, 34, 64, 0.95) 100%);
  position: relative;
  overflow: hidden;
}

.welcome-content::after {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: radial-gradient(circle at 20% 50%, rgba(0, 255, 255, 0.1) 0%, transparent 50%),
              radial-gradient(circle at 80% 80%, rgba(0, 150, 255, 0.1) 0%, transparent 50%);
  pointer-events: none;
  z-index: 0;
}

.welcome-content::before {
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

.icon {
  font-size: 120px;
  margin-bottom: 32px;
  filter: drop-shadow(0 0 20px rgba(0, 255, 255, 0.8));
  animation: float 3s ease-in-out infinite, pulse 2s ease-in-out infinite;
  position: relative;
  z-index: 1;
  display: inline-block;
}

@keyframes float {
  0%, 100% { transform: translateY(0px) rotate(0deg); }
  50% { transform: translateY(-15px) rotate(5deg); }
}

@keyframes pulse {
  0%, 100% { filter: drop-shadow(0 0 20px rgba(0, 255, 255, 0.8)); }
  50% { filter: drop-shadow(0 0 30px rgba(0, 255, 255, 1)); }
}

.welcome-content h1 {
  font-size: 48px;
  margin: 0 0 20px 0;
  color: #00ffff;
  font-weight: 700;
  text-shadow: 0 0 25px rgba(0, 255, 255, 0.6), 0 0 50px rgba(0, 255, 255, 0.3);
  position: relative;
  z-index: 1;
  letter-spacing: 2px;
  background: linear-gradient(135deg, #00ffff 0%, #00aaff 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.subtitle {
  font-size: 20px;
  color: rgba(0, 255, 255, 0.9);
  margin: 0 0 50px 0;
  text-shadow: 0 0 15px rgba(0, 255, 255, 0.4);
  position: relative;
  z-index: 1;
  font-weight: 300;
  letter-spacing: 1px;
}

.actions {
  display: flex;
  justify-content: center;
  gap: 24px;
  position: relative;
  z-index: 1;
  flex-wrap: wrap;
}

.actions :deep(.el-button) {
  padding: 16px 40px;
  font-size: 17px;
  font-weight: 600;
  border-radius: 12px;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  overflow: hidden;
}

.actions :deep(.el-button)::before {
  content: '';
  position: absolute;
  top: 50%;
  left: 50%;
  width: 0;
  height: 0;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.2);
  transform: translate(-50%, -50%);
  transition: width 0.6s, height 0.6s;
}

.actions :deep(.el-button:hover::before) {
  width: 300px;
  height: 300px;
}

.actions :deep(.el-button--primary) {
  background: linear-gradient(135deg, rgba(0, 255, 255, 0.3) 0%, rgba(0, 150, 255, 0.3) 100%);
  border: 1px solid rgba(0, 255, 255, 0.5);
  color: #00ffff;
  box-shadow: 0 4px 16px rgba(0, 255, 255, 0.3);
}

.actions :deep(.el-button--primary:hover) {
  background: linear-gradient(135deg, rgba(0, 255, 255, 0.4) 0%, rgba(0, 150, 255, 0.4) 100%);
  transform: translateY(-2px);
  box-shadow: 0 6px 24px rgba(0, 255, 255, 0.5), 0 0 30px rgba(0, 255, 255, 0.3);
}

.actions :deep(.el-button--default) {
  background: linear-gradient(135deg, rgba(0, 255, 255, 0.15) 0%, rgba(0, 180, 255, 0.15) 100%);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(0, 255, 255, 0.4);
  color: rgba(0, 255, 255, 0.9);
  box-shadow: 0 2px 12px rgba(0, 255, 255, 0.15);
}

.actions :deep(.el-button--default:hover) {
  background: linear-gradient(135deg, rgba(0, 255, 255, 0.25) 0%, rgba(0, 180, 255, 0.25) 100%);
  border-color: rgba(0, 255, 255, 0.6);
  color: #00ffff;
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(0, 255, 255, 0.3), 0 0 20px rgba(0, 255, 255, 0.2);
}

.stats-row {
  margin-top: 32px;
}

.stat-card {
  cursor: default;
  border: 1px solid rgba(0, 255, 255, 0.25);
  border-radius: 16px;
  box-shadow: 0 6px 20px rgba(0, 255, 255, 0.12), inset 0 0 20px rgba(0, 255, 255, 0.05);
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  background: linear-gradient(135deg, rgba(10, 25, 47, 0.85) 0%, rgba(17, 34, 64, 0.85) 100%);
  backdrop-filter: blur(10px);
  position: relative;
  overflow: hidden;
}

.stat-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(0, 255, 255, 0.1), transparent);
  transition: left 0.6s;
}

.stat-card:hover::before {
  left: 100%;
}

:deep(.stat-card .el-card__body) {
  background: transparent;
}

.stat-card:hover {
  transform: translateY(-8px) scale(1.02);
  border-color: rgba(0, 255, 255, 0.5);
  box-shadow: 0 12px 32px rgba(0, 255, 255, 0.25), 0 0 40px rgba(0, 255, 255, 0.15), inset 0 0 30px rgba(0, 255, 255, 0.08);
}

.stat-content {
  display: flex;
  align-items: center;
  padding: 24px 20px;
  position: relative;
  z-index: 1;
}

.stat-icon {
  font-size: 56px;
  margin-right: 24px;
  filter: drop-shadow(0 0 15px currentColor);
  transition: all 0.4s;
}

.stat-card:hover .stat-icon {
  transform: scale(1.15) rotate(5deg);
  filter: drop-shadow(0 0 20px currentColor);
}

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: 42px;
  font-weight: 700;
  color: #00ffff;
  text-shadow: 0 0 20px rgba(0, 255, 255, 0.6), 0 0 40px rgba(0, 255, 255, 0.3);
  line-height: 1;
  margin-bottom: 10px;
  transition: all 0.4s;
  background: linear-gradient(135deg, #00ffff 0%, #00aaff 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.stat-card:hover .stat-value {
  transform: scale(1.1);
}

.stat-label {
  font-size: 15px;
  color: rgba(255, 255, 255, 0.7);
  font-weight: 500;
  letter-spacing: 0.5px;
}

.home-container {
  padding: 20px;
  animation: fadeIn 0.8s ease-in;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>

