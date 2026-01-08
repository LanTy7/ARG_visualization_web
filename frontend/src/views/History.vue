<template>
  <div class="history-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>分析任务历史</span>
          <div class="header-actions">
            <el-input
              v-model="searchKeyword"
              placeholder="搜索任务ID或文件名"
              style="width: 280px;"
              clearable
              @keyup.enter="handleSearch"
              @clear="refreshTasks"
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
            <el-button type="primary" @click="handleSearch" :loading="loading">
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
          <template #default="{ row }">
            <div class="expand-content">
              <el-descriptions :column="2" border>
                <el-descriptions-item label="文件ID">{{ row.fileId }}</el-descriptions-item>
                <el-descriptions-item label="创建时间">{{ formatDate(row.createdAt) }}</el-descriptions-item>
                <el-descriptions-item label="开始时间">{{ formatDate(row.startedAt) }}</el-descriptions-item>
                <el-descriptions-item label="完成时间">{{ formatDate(row.completedAt) }}</el-descriptions-item>
                <el-descriptions-item label="运行时长">{{ formatDuration(row.startedAt, row.completedAt) }}</el-descriptions-item>
                <el-descriptions-item label="错误信息" v-if="row.errorMessage">
                  <el-text type="danger">{{ row.errorMessage }}</el-text>
                </el-descriptions-item>
              </el-descriptions>
            </div>
          </template>
        </el-table-column>
        
        <el-table-column label="任务ID" width="100" prop="taskId" />
        <el-table-column prop="fileName" label="文件名" min-width="200" show-overflow-tooltip />
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
        <el-table-column label="操作" width="240">
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
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { getUserTasks, cancelTask, deleteTask, createTask } from '@/api/task'

const router = useRouter()

const loading = ref(false)
const tasks = ref([])
const searchKeyword = ref('')
let refreshTimer = null

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0
})

// 计算当前页的数据
const paginatedTasks = computed(() => {
  const start = (pagination.current - 1) * pagination.pageSize
  const end = start + pagination.pageSize
  return tasks.value.slice(start, end)
})

// 刷新任务列表
const refreshTasks = async () => {
  try {
    loading.value = true
    const res = await getUserTasks()
    tasks.value = res.data || []
    pagination.total = tasks.value.length
    searchKeyword.value = ''
  } catch (error) {
    console.error('获取任务列表失败：', error)
    ElMessage.error('获取任务列表失败')
    tasks.value = []
    pagination.total = 0
  } finally {
    loading.value = false
  }
}

// 搜索任务
const handleSearch = async () => {
  if (!searchKeyword.value?.trim()) {
    refreshTasks()
    return
  }
  
  try {
    loading.value = true
    const res = await getUserTasks(searchKeyword.value.trim())
    tasks.value = res.data || []
    pagination.total = tasks.value.length
    pagination.current = 1
    if (tasks.value.length === 0) {
      ElMessage.info('未找到匹配的任务')
    }
  } catch (error) {
    console.error('搜索任务失败：', error)
    ElMessage.error('搜索任务失败')
  } finally {
    loading.value = false
  }
}

// 查看结果
const handleViewResult = (row) => {
  router.push({
    path: '/visualization',
    query: { taskId: row.taskId }
  })
}

// 取消任务
const handleCancelTask = async (row) => {
  try {
    await ElMessageBox.confirm('确定要取消该任务吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    await cancelTask(row.taskId)
    ElMessage.success('任务已取消')
    await refreshTasks()
  } catch {
    // 用户取消或操作失败
  }
}

// 重试任务
const handleRetryTask = async (row) => {
  try {
    await ElMessageBox.confirm('确定要重新运行该任务吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'info'
    })
    
    await createTask({ fileId: row.fileId, analysisType: 'arg' })
    ElMessage.success('抗性基因分析任务已重新创建')
    await refreshTasks()
  } catch {
    // 用户取消或操作失败
  }
}

// 删除任务
const handleDeleteTask = async (row) => {
  try {
    await ElMessageBox.confirm('确定要删除该任务吗？删除后无法恢复。', '警告', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    await deleteTask(row.taskId)
    ElMessage.success('任务已删除')
    await refreshTasks()
  } catch {
    // 用户取消或操作失败
  }
}

// 格式化日期
const formatDate = (dateString) => {
  if (!dateString) return '-'
  return new Date(dateString).toLocaleString('zh-CN')
}

// 计算运行时长
const formatDuration = (start, end) => {
  if (!start || !end) return '-'
  const startDate = new Date(start)
  const endDate = new Date(end)
  if (isNaN(startDate) || isNaN(endDate)) return '-'

  const diff = endDate.getTime() - startDate.getTime()
  if (diff < 0) return '-'

  const totalSeconds = Math.floor(diff / 1000)
  const hours = Math.floor(totalSeconds / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = totalSeconds % 60

  const parts = []
  if (hours) parts.push(`${hours}小时`)
  if (minutes) parts.push(`${minutes}分`)
  if (!hours && !minutes) {
    parts.push(`${seconds}秒`)
  } else if (seconds) {
    parts.push(`${seconds}秒`)
  }

  return parts.join('')
}

// 获取状态类型
const getStatusType = (status) => {
  const types = {
    'PENDING': 'info',
    'RUNNING': 'warning',
    'COMPLETED': 'success',
    'FAILED': 'danger',
    'CANCELLED': 'info'
  }
  return types[status] || 'info'
}

// 获取状态文本
const getStatusText = (status) => {
  const texts = {
    'PENDING': '等待中',
    'RUNNING': '运行中',
    'COMPLETED': '已完成',
    'FAILED': '失败',
    'CANCELLED': '已取消'
  }
  return texts[status] || status
}

// 分页大小改变
const handleSizeChange = (val) => {
  pagination.pageSize = val
  pagination.current = 1
}

// 当前页改变
const handleCurrentChange = (val) => {
  pagination.current = val
}

onMounted(() => {
  refreshTasks()
  
  // 定时刷新运行中的任务
  refreshTimer = setInterval(() => {
    const hasRunningTask = tasks.value.some(t => t.status === 'RUNNING')
    if (hasRunningTask) {
      refreshTasks()
    }
  }, 5000)
})

onUnmounted(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
  }
})
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

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
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
