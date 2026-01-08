<template>
  <div class="history-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>分析任务历史</span>
          <div style="display: flex; align-items: center; gap: 10px;">
            <el-input
              v-model="searchKeyword"
              placeholder="搜索任务ID、文件ID或文件名"
              style="width: 300px;"
              clearable
              @keyup.enter="handleSearchTasks"
              @clear="refreshTasks"
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
            <el-button type="primary" @click="handleSearchTasks" :loading="loading">
              <el-icon><Search /></el-icon>
              搜索
            </el-button>
            <el-button type="primary" link @click="refreshTasks" :loading="loading">
              <el-icon><Refresh /></el-icon>
              刷新
            </el-button>
          </div>
        </div>
      </template>
      
      <el-table :data="paginatedTasks" v-loading="loading">
        <el-table-column type="expand">
          <template #default="{ row, $index }">
            <div class="expand-content">
              <el-descriptions :column="2" border>
                <el-descriptions-item label="任务">{{ pagination.total - ((pagination.current - 1) * pagination.pageSize + $index) }}</el-descriptions-item>
                <el-descriptions-item label="文件ID">{{ row.fileId }}</el-descriptions-item>
                <el-descriptions-item label="创建时间">{{ formatDate(row.createdAt) }}</el-descriptions-item>
                <el-descriptions-item label="开始时间">{{ formatDate(row.startedAt) }}</el-descriptions-item>
                <el-descriptions-item label="完成时间">{{ formatDate(row.completedAt) }}</el-descriptions-item>
                <el-descriptions-item label="运行时长">{{ formatDuration(row.startedAt, row.completedAt) }}</el-descriptions-item>
                <el-descriptions-item label="错误信息" :span="2" v-if="row.errorMessage">
                  <el-text>文件格式错误：无法解析基因组序列</el-text>
                </el-descriptions-item>
              </el-descriptions>
            </div>
          </template>
        </el-table-column>
        
        <el-table-column label="任务" width="100">
          <template #default="{ $index }">
            {{ pagination.total - ((pagination.current - 1) * pagination.pageSize + $index) }}
          </template>
        </el-table-column>
        <el-table-column prop="fileName" label="文件名" min-width="200" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="进度" width="150">
          <template #default="{ row }">
            <el-progress
              v-if="row.status === 'RUNNING'"
              :percentage="row.progress || 0"
              :status="row.progress === 100 ? 'success' : ''"
            />
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'COMPLETED'"
              type="primary"
              link
              size="small"
              @click="handleViewResult(row)"
            >
              查看结果
            </el-button>
            <el-button
              v-if="row.status === 'RUNNING'"
              type="warning"
              link
              size="small"
              @click="handleCancelTask(row)"
            >
              取消
            </el-button>
            <el-button
              v-if="row.status === 'FAILED'"
              type="success"
              link
              size="small"
              @click="handleRetryTask(row)"
            >
              重试
            </el-button>
            <el-button
              type="danger"
              link
              size="small"
              @click="handleDeleteTask(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      
      <!-- 分页 -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="pagination.current"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Refresh, Search } from '@element-plus/icons-vue';
import { getUserTasks, cancelTask, deleteTask, createTask } from '@/api/task';

const router = useRouter();

const loading = ref(false);
const tasks = ref([]);
const searchKeyword = ref('');

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
});

// 计算当前页的数据
const paginatedTasks = computed(() => {
  const start = (pagination.current - 1) * pagination.pageSize;
  const end = start + pagination.pageSize;
  return tasks.value.slice(start, end);
});

// 刷新任务列表
const refreshTasks = async () => {
  try {
    loading.value = true;
    const res = await getUserTasks();
    tasks.value = res.data;
    pagination.total = res.data.length;
    searchKeyword.value = ''; // 清空搜索关键字
  } catch (error) {
    console.error('获取任务列表失败：', error);
    ElMessage.error('获取任务列表失败: ' + (error.message || '未知错误'));
    // 使用模拟数据进行预览
    tasks.value = [
      {
        taskId: 1,
        fileId: 1,
        fileName: 'sample_genome_1.fasta',
        status: 'COMPLETED',
        progress: 100,
        createdAt: '2024-11-13T10:35:00',
        startedAt: '2024-11-13T10:35:05',
        completedAt: '2024-11-13T10:45:30',
        duration: '10分25秒',
      },
      {
        taskId: 2,
        fileId: 2,
        fileName: 'ecoli_k12.gb',
        status: 'RUNNING',
        progress: 65,
        createdAt: '2024-11-13T11:00:00',
        startedAt: '2024-11-13T11:00:05',
        completedAt: null,
        duration: null,
      },
      {
        taskId: 3,
        fileId: 3,
        fileName: 'bacteriophage_genome.fastq',
        status: 'PENDING',
        progress: 0,
        createdAt: '2024-11-13T11:15:00',
        startedAt: null,
        completedAt: null,
        duration: null,
      },
      {
        taskId: 4,
        fileId: 4,
        fileName: 'old_sample.fasta',
        status: 'FAILED',
        progress: 0,
        createdAt: '2024-11-12T09:00:00',
        startedAt: '2024-11-12T09:00:05',
        completedAt: '2024-11-12T09:05:00',
        duration: null,
        errorMessage: '文件格式错误：无法解析基因组序列',
      },
    ];
    pagination.total = tasks.value.length;
  } finally {
    loading.value = false;
  }
};

// 搜索任务
const handleSearchTasks = async () => {
  if (!searchKeyword.value || !searchKeyword.value.trim()) {
    refreshTasks();
    return;
  }
  
  try {
    loading.value = true;
    const res = await getUserTasks(searchKeyword.value.trim());
    tasks.value = res.data;
    pagination.total = res.data.length;
    pagination.current = 1; // 重置到第一页
    if (res.data.length === 0) {
      ElMessage.info('未找到匹配的任务');
    }
  } catch (error) {
    console.error('搜索任务失败：', error);
    ElMessage.error('搜索任务失败: ' + (error.message || '未知错误'));
  } finally {
    loading.value = false;
  }
};

// 查看结果
const handleViewResult = (row) => {
  // 调试：打印完整的 row 数据
  console.log('handleViewResult - row:', row);
  console.log('handleViewResult - isArg:', row.isArg, typeof row.isArg);
  
  // 根据 isArg 字段判断：1=ARG任务, 0=Genomad任务
  const path = row.isArg === 1 ? '/visualization-arg' : '/visualization';
  console.log('handleViewResult - 跳转路径:', path);
  
  router.push({
    path: path,
    query: { taskId: row.taskId },
  });
};

// 取消任务
const handleCancelTask = async (row) => {
  try {
    await ElMessageBox.confirm('确定要取消该任务吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    });
    
    await cancelTask(row.taskId);
    ElMessage.success('任务已取消');
    await refreshTasks();
  } catch (error) {
    // 用户取消或操作失败
  }
};

// 重试任务
const handleRetryTask = async (row) => {
  try {
    // 根据原任务类型确定分析类型：isArg=1 为 ARG，否则为 genomad
    const analysisType = row.isArg === 1 ? 'arg' : 'genomad';
    const taskTypeName = row.isArg === 1 ? '抗性基因检测 (ARG)' : '原噬菌体识别 (Genomad)';
    
    await ElMessageBox.confirm(
      `确定要重新运行该任务吗？\n任务类型：${taskTypeName}`, 
      '提示', 
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'info',
      }
    );
    
    await createTask({ 
      fileId: row.fileId,
      analysisType: analysisType
    });
    ElMessage.success(`${taskTypeName}任务已重新创建`);
    await refreshTasks();
  } catch (error) {
    // 用户取消或操作失败
  }
};

// 删除任务
const handleDeleteTask = async (row) => {
  try {
    await ElMessageBox.confirm('确定要删除该任务吗？删除后无法恢复。', '警告', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    });
    
    await deleteTask(row.taskId);
    ElMessage.success('任务已删除');
    await refreshTasks();
  } catch (error) {
    // 用户取消或操作失败
  }
};

// 格式化日期
const formatDate = (dateString) => {
  if (!dateString) return '-';
  const date = new Date(dateString);
  return date.toLocaleString('zh-CN');
};

// 计算运行时长
const formatDuration = (start, end) => {
  if (!start || !end) return '-';
  const startDate = new Date(start);
  const endDate = new Date(end);
  if (isNaN(startDate) || isNaN(endDate)) return '-';

  let diff = endDate.getTime() - startDate.getTime();
  if (diff < 0) return '-';

  const totalSeconds = Math.floor(diff / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;

  const parts = [];
  if (hours) parts.push(`${hours}小时`);
  if (minutes) parts.push(`${minutes}分`);
  // 如果没有小时和分钟，就展示秒；否则有秒就补上
  if (!hours && !minutes) {
    parts.push(`${seconds}秒`);
  } else if (seconds) {
    parts.push(`${seconds}秒`);
  }

  return parts.join('');
};

// 获取状态类型
const getStatusType = (status) => {
  const types = {
    'PENDING': 'info',
    'RUNNING': 'warning',
    'COMPLETED': 'success',
    'FAILED': 'danger',
    'CANCELLED': 'info',
  };
  return types[status] || 'info';
};

// 获取状态文本
const getStatusText = (status) => {
  const texts = {
    'PENDING': '等待中',
    'RUNNING': '运行中',
    'COMPLETED': '已完成',
    'FAILED': '失败',
    'CANCELLED': '已取消',
  };
  return texts[status] || status;
};

// 分页大小改变
const handleSizeChange = (val) => {
  pagination.pageSize = val;
  pagination.current = 1; // 切换每页数量时重置到第一页
};

// 当前页改变
const handleCurrentChange = (val) => {
  pagination.current = val;
};

onMounted(() => {
  refreshTasks();
  
  // 定时刷新运行中的任务
  const timer = setInterval(() => {
    const hasRunningTask = tasks.value.some(t => t.status === 'RUNNING');
    if (hasRunningTask) {
      refreshTasks();
    }
  }, 5000);
  
  // 组件卸载时清除定时器
  return () => clearInterval(timer);
});
</script>

<style scoped>
.history-container {
  max-width: 1400px;
  margin: 0 auto;
}

:deep(.el-card) {
  background: linear-gradient(135deg, rgba(10, 25, 47, 0.8) 0%, rgba(17, 34, 64, 0.8) 100%);
  border: 1px solid rgba(0, 255, 255, 0.2);
  box-shadow: 0 4px 16px rgba(0, 255, 255, 0.1);
}

:deep(.el-card__header) {
  background: rgba(0, 255, 255, 0.05);
  border-bottom: 1px solid rgba(0, 255, 255, 0.2);
  color: #00ffff;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
}

:deep(.el-table) {
  background: transparent;
  color: rgba(255, 255, 255, 0.9);
}

:deep(.el-table th.el-table__cell) {
  background: rgba(0, 255, 255, 0.1);
  color: #00ffff;
  border-bottom: 1px solid rgba(0, 255, 255, 0.3);
}

:deep(.el-table tr) {
  background: transparent;
}

:deep(.el-table td.el-table__cell) {
  border-bottom: 1px solid rgba(0, 255, 255, 0.1);
}

:deep(.el-table__body tr:hover > td) {
  background: rgba(0, 255, 255, 0.05) !important;
}

:deep(.el-table__expanded-cell) {
  background: rgba(0, 255, 255, 0.03);
}

.expand-content {
  padding: 20px 60px;
}

:deep(.el-descriptions) {
  background: transparent;
}

:deep(.el-descriptions__label) {
  color: #00ffff;
  background: rgba(0, 255, 255, 0.1);
}

:deep(.el-descriptions__content) {
  color: rgba(255, 255, 255, 0.9);
  background: rgba(0, 255, 255, 0.03);
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

:deep(.el-pagination) {
  --el-pagination-bg-color: transparent;
  --el-pagination-text-color: rgba(255, 255, 255, 0.8);
  --el-pagination-button-bg-color: rgba(0, 255, 255, 0.1);
  --el-pagination-hover-color: #00ffff;
}

:deep(.el-pagination .el-pager li) {
  background: rgba(0, 255, 255, 0.05);
  color: rgba(255, 255, 255, 0.8);
  border: 1px solid rgba(0, 255, 255, 0.2);
}

:deep(.el-pagination .el-pager li.is-active) {
  background: rgba(0, 255, 255, 0.3);
  color: #00ffff;
  border-color: rgba(0, 255, 255, 0.5);
}

:deep(.el-pagination .el-pager li:hover) {
  color: #00ffff;
  background: rgba(0, 255, 255, 0.15);
}
</style>

