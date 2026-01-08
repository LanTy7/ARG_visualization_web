<template>
  <div class="home-container">
    <el-card class="welcome-card">
      <div class="welcome-content">
        <div class="icon">🧬</div>
        <h1>欢迎使用抗性基因检测系统</h1>
        <p class="subtitle">基于深度学习的抗性基因识别和可视化平台</p>
        
        <div class="features">
          <el-row :gutter="24">
            <el-col :span="8">
              <div class="feature-card">
                <div class="feature-icon">🔬</div>
                <h3>高精度识别</h3>
                <p>采用先进的深度学习算法，准确识别抗性基因区域</p>
              </div>
            </el-col>
            
            <el-col :span="8">
              <div class="feature-card">
                <div class="feature-icon">📊</div>
                <h3>直观可视化</h3>
                <p>通过图表和基因组浏览器直观展示识别结果</p>
              </div>
            </el-col>
            
            <el-col :span="8">
              <div class="feature-card">
                <div class="feature-icon">⚡</div>
                <h3>快速处理</h3>
                <p>基于Docker容器化部署，快速高效处理基因数据</p>
              </div>
            </el-col>
          </el-row>
        </div>
        
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
  padding: 60px 40px;
  background: linear-gradient(135deg, rgba(10, 25, 47, 0.95) 0%, rgba(17, 34, 64, 0.95) 100%);
  position: relative;
  overflow: hidden;
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
  font-size: 100px;
  margin-bottom: 24px;
  filter: drop-shadow(0 0 15px rgba(0, 255, 255, 0.6));
  animation: float 3s ease-in-out infinite;
  position: relative;
  z-index: 1;
}

@keyframes float {
  0%, 100% { transform: translateY(0px); }
  50% { transform: translateY(-10px); }
}

.welcome-content h1 {
  font-size: 40px;
  margin: 0 0 16px 0;
  color: #00ffff;
  font-weight: 700;
  text-shadow: 0 0 20px rgba(0, 255, 255, 0.5);
  position: relative;
  z-index: 1;
}

.subtitle {
  font-size: 18px;
  color: rgba(0, 255, 255, 0.8);
  margin: 0 0 50px 0;
  text-shadow: 0 0 10px rgba(0, 255, 255, 0.3);
  position: relative;
  z-index: 1;
}

.features {
  margin-bottom: 40px;
  position: relative;
  z-index: 1;
}

.feature-card {
  padding: 32px 24px;
  background: rgba(0, 255, 255, 0.05);
  backdrop-filter: blur(10px);
  border-radius: 12px;
  border: 1px solid rgba(0, 255, 255, 0.2);
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 4px 16px rgba(0, 255, 255, 0.1);
  height: 240px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.feature-card:hover {
  background: rgba(0, 255, 255, 0.1);
  transform: translateY(-8px);
  border-color: rgba(0, 255, 255, 0.4);
  box-shadow: 0 12px 32px rgba(0, 255, 255, 0.2), 0 0 20px rgba(0, 255, 255, 0.1);
}

.feature-icon {
  font-size: 56px;
  margin-bottom: 20px;
  filter: drop-shadow(0 0 10px rgba(0, 255, 255, 0.5));
}

.feature-card h3 {
  font-size: 20px;
  margin: 0 0 12px 0;
  color: #00ffff;
  font-weight: 600;
  text-shadow: 0 0 10px rgba(0, 255, 255, 0.3);
}

.feature-card p {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.7);
  margin: 0;
  line-height: 1.8;
}

.actions {
  display: flex;
  justify-content: center;
  gap: 20px;
  position: relative;
  z-index: 1;
}

.actions :deep(.el-button) {
  padding: 14px 32px;
  font-size: 16px;
  font-weight: 500;
  border-radius: 8px;
  transition: all 0.3s;
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
  margin-top: 24px;
}

.stat-card {
  cursor: default;
  border: 1px solid rgba(0, 255, 255, 0.2);
  border-radius: 12px;
  box-shadow: 0 4px 16px rgba(0, 255, 255, 0.1);
  transition: all 0.3s;
  background: linear-gradient(135deg, rgba(10, 25, 47, 0.8) 0%, rgba(17, 34, 64, 0.8) 100%);
}

:deep(.stat-card .el-card__body) {
  background: transparent;
}

.stat-card:hover {
  transform: translateY(-4px);
  border-color: rgba(0, 255, 255, 0.4);
  box-shadow: 0 8px 24px rgba(0, 255, 255, 0.2), 0 0 30px rgba(0, 255, 255, 0.1);
}

.stat-content {
  display: flex;
  align-items: center;
  padding: 8px;
}

.stat-icon {
  font-size: 48px;
  margin-right: 20px;
  filter: drop-shadow(0 0 10px currentColor);
}

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: 36px;
  font-weight: 700;
  color: #00ffff;
  text-shadow: 0 0 15px rgba(0, 255, 255, 0.5);
  line-height: 1;
  margin-bottom: 8px;
}

.stat-label {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.6);
  font-weight: 500;
}
</style>

