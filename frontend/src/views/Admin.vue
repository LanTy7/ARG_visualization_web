<template>
  <div class="admin-container">
    <!-- 系统统计 -->
    <el-card class="stats-card">
      <template #header>
        <div class="card-header">
          <el-icon><DataAnalysis /></el-icon>
          <span>系统统计</span>
        </div>
      </template>
      
      <el-row :gutter="24">
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-icon" style="color: #409eff;">
              <el-icon><User /></el-icon>
            </div>
            <div class="stat-content">
              <div class="stat-value">{{ statistics.totalUsers || 0 }}</div>
              <div class="stat-label">总用户数</div>
            </div>
          </div>
        </el-col>
        
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-icon" style="color: #67c23a;">
              <el-icon><Document /></el-icon>
            </div>
            <div class="stat-content">
              <div class="stat-value">{{ statistics.totalFiles || 0 }}</div>
              <div class="stat-label">总文件数</div>
            </div>
          </div>
        </el-col>
        
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-icon" style="color: #e6a23c;">
              <el-icon><List /></el-icon>
            </div>
            <div class="stat-content">
              <div class="stat-value">{{ statistics.totalTasks || 0 }}</div>
              <div class="stat-label">总任务数</div>
            </div>
          </div>
        </el-col>
        
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-icon" style="color: #f56c6c;">
              <el-icon><Key /></el-icon>
            </div>
            <div class="stat-content">
              <div class="stat-value">{{ statistics.totalLogins || 0 }}</div>
              <div class="stat-label">总登录次数</div>
            </div>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <!-- Tab切换 -->
    <el-card class="main-card">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <!-- 用户管理 -->
        <el-tab-pane label="用户管理" name="users">
          <div class="tab-header">
            <el-input
              v-model="searchKeyword.users"
              placeholder="搜索用户名或用户ID"
              style="width: 300px; margin-right: 10px;"
              clearable
              @keyup.enter="handleSearchUsers"
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
            <el-button type="primary" @click="handleSearchUsers" :loading="loading.users">
              <el-icon><Search /></el-icon>
              搜索
            </el-button>
            <el-button type="primary" @click="fetchUsers" :loading="loading.users">
              <el-icon><Refresh /></el-icon>
              刷新
            </el-button>
          </div>
          
          <el-table
            :data="userList"
            v-loading="loading.users"
            stripe
            border
            style="width: 100%"
            class="data-table"
          >
            <el-table-column prop="userId" label="用户ID" width="80" />
            <el-table-column prop="username" label="用户名" width="150" />
            <el-table-column prop="email" label="邮箱" width="200" />
            <el-table-column prop="role" label="角色" width="100">
              <template #default="{ row }">
                <el-tag :type="row.role === 'ADMIN' ? 'danger' : 'primary'">
                  {{ row.role === 'ADMIN' ? '管理员' : '普通用户' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'">
                  {{ row.status === 'ACTIVE' ? '正常' : '已封禁' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="fileCount" label="文件数" width="100" />
            <el-table-column prop="taskCount" label="任务数" width="100" />
            <el-table-column prop="createdAt" label="创建时间" width="180" />
            <el-table-column prop="lastLoginAt" label="最后登录" width="180" />
            <el-table-column label="操作" width="120">
              <template #default="{ row }">
                <el-button
                  v-if="row.role !== 'ADMIN'"
                  type="danger"
                  size="small"
                  @click="handleDeleteUser(row)"
                >
                  删除
                </el-button>
                <el-tag v-else type="info" size="small">管理员</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 文件管理 -->
        <el-tab-pane label="文件管理" name="files">
          <div class="tab-header">
            <el-input
              v-model="searchKeyword.files.user"
              placeholder="用户ID或用户名"
              style="width: 200px; margin-right: 10px;"
              clearable
              @keyup.enter="handleSearchFiles"
            >
              <template #prefix>
                <el-icon><User /></el-icon>
              </template>
            </el-input>
            <el-input
              v-model="searchKeyword.files.file"
              placeholder="文件ID或文件名"
              style="width: 250px; margin-right: 10px;"
              clearable
              @keyup.enter="handleSearchFiles"
            >
              <template #prefix>
                <el-icon><Document /></el-icon>
              </template>
            </el-input>
            <el-button type="primary" @click="handleSearchFiles" :loading="loading.files">
              <el-icon><Search /></el-icon>
              搜索
            </el-button>
            <el-button type="primary" @click="fetchFiles" :loading="loading.files">
              <el-icon><Refresh /></el-icon>
              刷新
            </el-button>
          </div>
          
          <el-table
            :data="fileList"
            v-loading="loading.files"
            stripe
            border
            style="width: 100%"
            class="data-table"
          >
            <el-table-column prop="fileId" label="文件ID" width="80" />
            <el-table-column prop="userId" label="用户ID" width="80" />
            <el-table-column prop="username" label="用户名" width="150" />
            <el-table-column prop="originalFilename" label="文件名" min-width="200" show-overflow-tooltip />
            <el-table-column prop="fileSize" label="文件大小" width="120">
              <template #default="{ row }">
                {{ formatFileSize(row.fileSize) }}
              </template>
            </el-table-column>
            <el-table-column prop="fileType" label="文件类型" width="100" />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="getStatusType(row.status)">
                  {{ getStatusText(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="uploadTime" label="上传时间" width="180" />
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button
                  type="danger"
                  size="small"
                  @click="handleDeleteFile(row)"
                >
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  DataAnalysis,
  User,
  Document,
  List,
  Key,
  Refresh,
  Search,
} from '@element-plus/icons-vue';
import {
  getAllUsers,
  deleteUser,
  getAllFiles,
  deleteFile,
  getStatistics,
  searchUsers,
  searchFiles,
} from '@/api/admin';

const activeTab = ref('users');
const loading = ref({
  users: false,
  files: false,
});

const statistics = ref({
  totalUsers: 0,
  totalFiles: 0,
  totalTasks: 0,
  totalLogins: 0,
});

const userList = ref([]);
const fileList = ref([]);

const searchKeyword = ref({
  users: '',
  files: {
    user: '',
    file: '',
  },
});

// 获取统计信息
const fetchStatistics = async () => {
  try {
    const res = await getStatistics();
    statistics.value = res.data;
  } catch (error) {
    console.error('获取统计信息失败:', error);
  }
};

// 获取用户列表
const fetchUsers = async () => {
  loading.value.users = true;
  try {
    const res = await getAllUsers();
    userList.value = res.data;
    searchKeyword.value.users = ''; // 清空搜索关键字
  } catch (error) {
    ElMessage.error('获取用户列表失败: ' + (error.message || '未知错误'));
  } finally {
    loading.value.users = false;
  }
};

// 搜索用户
const handleSearchUsers = async () => {
  if (!searchKeyword.value.users || !searchKeyword.value.users.trim()) {
    fetchUsers();
    return;
  }
  
  loading.value.users = true;
  try {
    const res = await searchUsers(searchKeyword.value.users.trim());
    userList.value = res.data;
    if (res.data.length === 0) {
      ElMessage.info('未找到匹配的用户');
    }
  } catch (error) {
    ElMessage.error('搜索用户失败: ' + (error.message || '未知错误'));
  } finally {
    loading.value.users = false;
  }
};

// 获取文件列表
const fetchFiles = async () => {
  loading.value.files = true;
  try {
    const res = await getAllFiles();
    fileList.value = res.data;
    searchKeyword.value.files.user = ''; // 清空搜索关键字
    searchKeyword.value.files.file = ''; // 清空搜索关键字
  } catch (error) {
    ElMessage.error('获取文件列表失败: ' + (error.message || '未知错误'));
  } finally {
    loading.value.files = false;
  }
};

// 搜索文件
const handleSearchFiles = async () => {
  const userKeyword = searchKeyword.value.files.user?.trim() || '';
  const fileKeyword = searchKeyword.value.files.file?.trim() || '';
  
  // 如果两个关键字都为空，刷新列表
  if (!userKeyword && !fileKeyword) {
    fetchFiles();
    return;
  }
  
  loading.value.files = true;
  try {
    const res = await searchFiles(
      userKeyword || null,
      fileKeyword || null
    );
    fileList.value = res.data;
    if (res.data.length === 0) {
      ElMessage.info('未找到匹配的文件');
    }
  } catch (error) {
    ElMessage.error('搜索文件失败: ' + (error.message || '未知错误'));
  } finally {
    loading.value.files = false;
  }
};

// 删除用户
const handleDeleteUser = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除用户 "${row.username}" 吗？此操作将删除该用户的所有文件、任务和相关数据，且无法恢复！`,
      '危险操作',
      {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'error',
        distinguishCancelAndClose: true,
      }
    );

    await deleteUser(row.userId);
    ElMessage.success('用户删除成功');
    fetchUsers();
    fetchFiles();
    fetchStatistics();
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error('删除用户失败: ' + (error.message || '未知错误'));
    }
  }
};

// 删除文件
const handleDeleteFile = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除文件 "${row.originalFilename}" 吗？此操作将删除该文件及其所有相关任务和分析结果，且无法恢复！`,
      '危险操作',
      {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'error',
        distinguishCancelAndClose: true,
      }
    );

    await deleteFile(row.fileId);
    ElMessage.success('文件删除成功');
    fetchFiles();
    fetchStatistics();
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error('删除文件失败: ' + (error.message || '未知错误'));
    }
  }
};

// Tab切换处理
const handleTabChange = (tabName) => {
  if (tabName === 'users' && userList.value.length === 0) {
    fetchUsers();
  } else if (tabName === 'files' && fileList.value.length === 0) {
    fetchFiles();
  }
};

// 格式化文件大小
const formatFileSize = (bytes) => {
  if (!bytes) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return (bytes / Math.pow(k, i)).toFixed(2) + ' ' + sizes[i];
};

// 获取状态类型
const getStatusType = (status) => {
  const types = {
    UPLOADED: 'success',
    ANALYZING: 'warning',
    COMPLETED: 'success',
    DELETED: 'info',
    FAILED: 'danger',
  };
  return types[status] || 'info';
};

// 获取状态文本
const getStatusText = (status) => {
  const texts = {
    UPLOADED: '已上传',
    ANALYZING: '分析中',
    COMPLETED: '已完成',
    DELETED: '已删除',
    FAILED: '失败',
  };
  return texts[status] || status;
};

onMounted(() => {
  fetchStatistics();
  fetchUsers();
});
</script>

<style scoped>
.admin-container {
  max-width: 1600px;
  margin: 0 auto;
}

.stats-card {
  margin-bottom: 24px;
  background: linear-gradient(135deg, #0a192f 0%, #112240 100%);
  border: 1px solid rgba(0, 255, 255, 0.2);
  box-shadow: 0 8px 32px rgba(0, 255, 255, 0.2);
}

:deep(.stats-card .el-card__body) {
  background: transparent;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: 600;
  color: #00ffff;
  text-shadow: 0 0 10px rgba(0, 255, 255, 0.5);
}

.stat-item {
  display: flex;
  align-items: center;
  padding: 20px;
  background: rgba(0, 255, 255, 0.05);
  backdrop-filter: blur(10px);
  border-radius: 12px;
  border: 1px solid rgba(0, 255, 255, 0.2);
  transition: all 0.3s;
}

.stat-item:hover {
  background: rgba(0, 255, 255, 0.1);
  border-color: rgba(0, 255, 255, 0.4);
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 255, 255, 0.2);
}

.stat-icon {
  font-size: 48px;
  margin-right: 16px;
  filter: drop-shadow(0 0 10px currentColor);
}

.stat-content {
  flex: 1;
}

.stat-value {
  font-size: 32px;
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

.main-card {
  background: linear-gradient(135deg, #0a192f 0%, #112240 100%);
  border: 1px solid rgba(0, 255, 255, 0.2);
  box-shadow: 0 8px 32px rgba(0, 255, 255, 0.2);
}

:deep(.main-card .el-card__body) {
  background: transparent;
  padding: 0;
}

:deep(.el-tabs__header) {
  margin: 0;
  padding: 20px 20px 0;
  background: transparent;
}

:deep(.el-tabs__nav-wrap::after) {
  background: rgba(0, 255, 255, 0.2);
}

:deep(.el-tabs__item) {
  color: rgba(255, 255, 255, 0.6);
  font-size: 16px;
  font-weight: 500;
}

:deep(.el-tabs__item:hover) {
  color: #00ffff;
}

:deep(.el-tabs__item.is-active) {
  color: #00ffff;
  text-shadow: 0 0 10px rgba(0, 255, 255, 0.5);
}

:deep(.el-tabs__active-bar) {
  background: #00ffff;
  box-shadow: 0 0 10px rgba(0, 255, 255, 0.5);
}

:deep(.el-tabs__content) {
  padding: 20px;
}

.tab-header {
  margin-bottom: 16px;
  display: flex;
  justify-content: flex-end;
}

.data-table {
  background: transparent !important;
}

/* 强制覆盖所有可能的白色背景 */
:deep(.el-table__body),
:deep(.el-table__header),
:deep(.el-table__header th),
:deep(.el-table__body td),
:deep(.el-table__row),
:deep(.el-table__cell),
:deep(.el-table__body tr),
:deep(.el-table__body tr td),
:deep(.el-table__header tr),
:deep(.el-table__header tr th) {
  background-color: transparent !important;
  background-image: none !important;
}

/* 确保所有td和th都有青色背景 */
:deep(.el-table td.el-table__cell),
:deep(.el-table th.el-table__cell) {
  background: linear-gradient(135deg, rgba(0, 180, 255, 0.05) 0%, rgba(0, 200, 255, 0.08) 100%) !important;
}

/* 确保表格容器也是青色 */
:deep(.el-table__inner-wrapper) {
  background: linear-gradient(135deg, rgba(0, 180, 255, 0.05) 0%, rgba(0, 200, 255, 0.08) 100%) !important;
}

:deep(.el-table) {
  background: linear-gradient(135deg, rgba(0, 180, 255, 0.05) 0%, rgba(0, 200, 255, 0.08) 100%) !important;
  color: #00d4ff !important;
}

:deep(.el-table th.el-table__cell) {
  background: linear-gradient(135deg, rgba(0, 180, 255, 0.2) 0%, rgba(0, 200, 255, 0.25) 100%) !important;
  color: #00ffff !important;
  border-color: rgba(0, 255, 255, 0.3) !important;
  font-weight: 600;
}

:deep(.el-table tr) {
  background: linear-gradient(135deg, rgba(0, 180, 255, 0.05) 0%, rgba(0, 200, 255, 0.08) 100%) !important;
}

:deep(.el-table td.el-table__cell) {
  background: linear-gradient(135deg, rgba(0, 180, 255, 0.05) 0%, rgba(0, 200, 255, 0.08) 100%) !important;
  border-color: rgba(0, 255, 255, 0.2) !important;
  color: #00d4ff !important;
}

:deep(.el-table__body-wrapper) {
  background: linear-gradient(135deg, rgba(0, 180, 255, 0.05) 0%, rgba(0, 200, 255, 0.08) 100%) !important;
}

:deep(.el-table__header-wrapper) {
  background: transparent !important;
}

:deep(.el-table__body tr:hover > td) {
  background: rgba(0, 255, 255, 0.05) !important;
}

:deep(.el-table__row--striped td) {
  background: linear-gradient(135deg, rgba(0, 180, 255, 0.08) 0%, rgba(0, 200, 255, 0.12) 100%) !important;
}

:deep(.el-table__body tr.el-table__row--striped:hover > td) {
  background: rgba(0, 255, 255, 0.15) !important;
}

/* 表格边框颜色 */
:deep(.el-table--border) {
  border-color: rgba(0, 255, 255, 0.3) !important;
}

:deep(.el-table--border::after) {
  background-color: rgba(0, 255, 255, 0.3) !important;
}

:deep(.el-table--border::before) {
  background-color: rgba(0, 255, 255, 0.3) !important;
}

:deep(.el-table__inner-wrapper::before) {
  background-color: rgba(0, 255, 255, 0.3) !important;
}

:deep(.el-table__inner-wrapper::after) {
  background-color: rgba(0, 255, 255, 0.3) !important;
}

/* 标签背景色 - 统一使用青色系 */
:deep(.el-tag) {
  background: linear-gradient(135deg, rgba(0, 180, 255, 0.2) 0%, rgba(0, 200, 255, 0.25) 100%) !important;
  border-color: rgba(0, 255, 255, 0.4) !important;
  color: #00d4ff !important;
}

:deep(.el-tag.el-tag--primary) {
  background: linear-gradient(135deg, rgba(0, 180, 255, 0.3) 0%, rgba(0, 200, 255, 0.35) 100%) !important;
  border-color: rgba(0, 255, 255, 0.5) !important;
  color: #00ffff !important;
}

:deep(.el-tag.el-tag--success) {
  background: linear-gradient(135deg, rgba(0, 255, 150, 0.2) 0%, rgba(0, 200, 120, 0.25) 100%) !important;
  border-color: rgba(0, 255, 150, 0.4) !important;
  color: #00ff99 !important;
}

:deep(.el-tag.el-tag--warning) {
  background: linear-gradient(135deg, rgba(255, 200, 0, 0.2) 0%, rgba(255, 150, 0, 0.25) 100%) !important;
  border-color: rgba(255, 200, 0, 0.4) !important;
  color: #ffc800 !important;
}

:deep(.el-tag.el-tag--danger) {
  background: linear-gradient(135deg, rgba(255, 50, 100, 0.2) 0%, rgba(255, 0, 50, 0.25) 100%) !important;
  border-color: rgba(255, 50, 100, 0.4) !important;
  color: #ff4466 !important;
}

:deep(.el-tag.el-tag--info) {
  background: linear-gradient(135deg, rgba(0, 180, 255, 0.15) 0%, rgba(0, 200, 255, 0.2) 100%) !important;
  border-color: rgba(0, 255, 255, 0.3) !important;
  color: #00d4ff !important;
}

/* Loading遮罩层背景 */
:deep(.el-loading-mask) {
  background-color: rgba(10, 25, 47, 0.8) !important;
}

/* 空状态背景 */
:deep(.el-empty) {
  background: transparent;
}

:deep(.el-empty__image) {
  opacity: 0.6;
}

:deep(.el-button--primary) {
  background: linear-gradient(135deg, rgba(0, 255, 255, 0.3) 0%, rgba(0, 150, 255, 0.3) 100%);
  border: 1px solid rgba(0, 255, 255, 0.5);
  color: #00ffff;
}

:deep(.el-button--primary:hover) {
  background: linear-gradient(135deg, rgba(0, 255, 255, 0.4) 0%, rgba(0, 150, 255, 0.4) 100%);
  box-shadow: 0 0 15px rgba(0, 255, 255, 0.4);
}

:deep(.el-button--danger) {
  background: linear-gradient(135deg, rgba(255, 50, 100, 0.3) 0%, rgba(255, 0, 50, 0.3) 100%);
  border: 1px solid rgba(255, 50, 100, 0.5);
  color: #ff4466;
}

:deep(.el-button--danger:hover) {
  background: linear-gradient(135deg, rgba(255, 50, 100, 0.4) 0%, rgba(255, 0, 50, 0.4) 100%);
  box-shadow: 0 0 15px rgba(255, 50, 100, 0.4);
}

:deep(.el-button--warning) {
  background: linear-gradient(135deg, rgba(255, 200, 0, 0.3) 0%, rgba(255, 150, 0, 0.3) 100%);
  border: 1px solid rgba(255, 200, 0, 0.5);
  color: #ffc800;
}

:deep(.el-button--warning:hover) {
  background: linear-gradient(135deg, rgba(255, 200, 0, 0.4) 0%, rgba(255, 150, 0, 0.4) 100%);
  box-shadow: 0 0 15px rgba(255, 200, 0, 0.4);
}

:deep(.el-button--success) {
  background: linear-gradient(135deg, rgba(0, 255, 150, 0.3) 0%, rgba(0, 200, 100, 0.3) 100%);
  border: 1px solid rgba(0, 255, 150, 0.5);
  color: #00ff99;
}

:deep(.el-button--success:hover) {
  background: linear-gradient(135deg, rgba(0, 255, 150, 0.4) 0%, rgba(0, 200, 100, 0.4) 100%);
  box-shadow: 0 0 15px rgba(0, 255, 150, 0.4);
}
</style>

