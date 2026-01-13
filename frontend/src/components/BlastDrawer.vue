<template>
  <el-drawer
    v-model="visible"
    title="BLAST 比对结果"
    direction="rtl"
    size="45%"
    :before-close="handleClose"
    class="blast-drawer"
  >
    <template #header>
      <div class="drawer-header">
        <el-icon class="header-icon"><Search /></el-icon>
        <span>BLAST 比对结果</span>
      </div>
    </template>

    <div class="blast-content" v-loading="loading" element-loading-text="正在进行 BLAST 比对...">
      <!-- 查询序列信息 -->
      <div class="query-info" v-if="blastResult">
        <div class="section-title">
          <el-icon><Document /></el-icon>
          <span>查询序列</span>
        </div>
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="序列 ID">
            <span class="sequence-id">{{ blastResult.queryInfo?.id || sequenceId }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="序列长度">
            <el-tag type="info" size="small">{{ blastResult.queryInfo?.length || '-' }} aa</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="预测分类" v-if="argClass">
            <el-tag type="warning" size="small">{{ argClass }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>
      </div>

      <!-- 比对结果统计 -->
      <div class="result-summary" v-if="blastResult && !loading">
        <div class="section-title">
          <el-icon><DataAnalysis /></el-icon>
          <span>比对结果</span>
          <el-tag type="success" size="small" style="margin-left: 10px;">
            找到 {{ blastResult.totalHits || 0 }} 个匹配
          </el-tag>
        </div>
      </div>

      <!-- 无结果提示 -->
      <el-empty 
        v-if="blastResult && blastResult.totalHits === 0 && !loading" 
        description="未找到显著匹配"
      >
        <template #image>
          <el-icon style="font-size: 60px; color: #909399;"><Warning /></el-icon>
        </template>
      </el-empty>

      <!-- 比对结果表格 -->
      <div class="hits-table" v-if="blastResult && blastResult.totalHits > 0">
        <el-table 
          :data="blastResult.hits" 
          border 
          stripe
          size="small"
          max-height="400"
          highlight-current-row
          @row-click="handleRowClick"
        >
          <el-table-column type="index" label="#" width="50" align="center" />
          <el-table-column prop="subjectId" label="匹配序列" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="subject-id">{{ row.subjectId }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="identity" label="一致性" width="90" align="center">
            <template #default="{ row }">
              <el-tag 
                :type="getIdentityTagType(row.identity)" 
                size="small"
              >
                {{ row.identity?.toFixed(1) }}%
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="evalue" label="E 值" width="100" align="center">
            <template #default="{ row }">
              <span class="evalue">{{ formatEvalue(row.evalue) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="bitScore" label="比分" width="80" align="center">
            <template #default="{ row }">
              {{ row.bitScore?.toFixed(1) }}
            </template>
          </el-table-column>
          <el-table-column prop="alignLength" label="比对长度" width="90" align="center">
            <template #default="{ row }">
              {{ row.alignLength }} / {{ row.queryLength }}
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 选中的匹配详情 -->
      <div class="hit-detail" v-if="selectedHit">
        <div class="section-title">
          <el-icon><InfoFilled /></el-icon>
          <span>匹配详情</span>
        </div>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="匹配序列 ID" :span="2">
            <span class="subject-id">{{ selectedHit.subjectId }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="描述" :span="2" v-if="selectedHit.description">
            {{ selectedHit.description }}
          </el-descriptions-item>
          <el-descriptions-item label="序列一致性">
            <el-progress 
              :percentage="selectedHit.identity" 
              :color="getProgressColor(selectedHit.identity)"
              :stroke-width="12"
              style="width: 120px;"
            />
          </el-descriptions-item>
          <el-descriptions-item label="E 值">
            <span class="evalue">{{ selectedHit.evalue }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="比分 (Bit Score)">
            {{ selectedHit.bitScore?.toFixed(1) }}
          </el-descriptions-item>
          <el-descriptions-item label="比对长度">
            {{ selectedHit.alignLength }} aa
          </el-descriptions-item>
          <el-descriptions-item label="查询序列区域">
            {{ selectedHit.queryStart }} - {{ selectedHit.queryEnd }}
          </el-descriptions-item>
          <el-descriptions-item label="目标序列区域">
            {{ selectedHit.subjectStart }} - {{ selectedHit.subjectEnd }}
          </el-descriptions-item>
          <el-descriptions-item label="查询序列长度">
            {{ selectedHit.queryLength }} aa
          </el-descriptions-item>
          <el-descriptions-item label="目标序列长度">
            {{ selectedHit.subjectLength }} aa
          </el-descriptions-item>
        </el-descriptions>
      </div>

      <!-- 错误信息 -->
      <el-alert
        v-if="error"
        :title="error"
        type="error"
        show-icon
        :closable="false"
        style="margin-top: 20px;"
      />
    </div>

    <template #footer>
      <div class="drawer-footer">
        <el-button @click="handleClose">关闭</el-button>
        <el-button type="primary" @click="handleRefresh" :loading="loading">
          <el-icon><Refresh /></el-icon>
          重新比对
        </el-button>
      </div>
    </template>
  </el-drawer>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Document, DataAnalysis, Warning, InfoFilled, Refresh } from '@element-plus/icons-vue'
import { blastSingleSequence } from '@/api/blast'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  taskId: {
    type: [Number, String],
    required: true
  },
  sequenceId: {
    type: String,
    required: true
  },
  argClass: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['update:modelValue'])

const visible = ref(false)
const loading = ref(false)
const error = ref('')
const blastResult = ref(null)
const selectedHit = ref(null)

// 监听 modelValue 变化
watch(() => props.modelValue, (newVal) => {
  visible.value = newVal
  if (newVal && props.taskId && props.sequenceId) {
    runBlast()
  }
})

// 监听 visible 变化同步到父组件
watch(visible, (newVal) => {
  emit('update:modelValue', newVal)
})

// 执行 BLAST 比对
async function runBlast() {
  loading.value = true
  error.value = ''
  blastResult.value = null
  selectedHit.value = null

  try {
    const response = await blastSingleSequence(props.taskId, props.sequenceId)
    blastResult.value = response.data
    
    // 自动选中第一个结果
    if (blastResult.value?.hits?.length > 0) {
      selectedHit.value = blastResult.value.hits[0]
    }
    
    if (blastResult.value?.totalHits === 0) {
      ElMessage.info('未找到显著匹配')
    } else {
      ElMessage.success(`找到 ${blastResult.value.totalHits} 个匹配`)
    }
  } catch (e) {
    console.error('BLAST 比对失败:', e)
    error.value = e.message || 'BLAST 比对失败'
    ElMessage.error('BLAST 比对失败: ' + error.value)
  } finally {
    loading.value = false
  }
}

// 点击表格行
function handleRowClick(row) {
  selectedHit.value = row
}

// 关闭抽屉
function handleClose() {
  visible.value = false
}

// 重新比对
function handleRefresh() {
  runBlast()
}

// 根据一致性获取标签类型
function getIdentityTagType(identity) {
  if (identity >= 90) return 'success'
  if (identity >= 70) return 'primary'
  if (identity >= 50) return 'warning'
  return 'info'
}

// 格式化 E 值
function formatEvalue(evalue) {
  if (!evalue) return '-'
  const num = parseFloat(evalue)
  if (num === 0) return '0'
  if (num < 0.0001) {
    return num.toExponential(1)
  }
  return num.toFixed(4)
}

// 获取进度条颜色
function getProgressColor(percentage) {
  if (percentage >= 90) return '#67C23A'
  if (percentage >= 70) return '#409EFF'
  if (percentage >= 50) return '#E6A23C'
  return '#909399'
}
</script>

<style scoped>
.blast-drawer {
  --el-drawer-padding-primary: 20px;
}

.drawer-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: 600;
  color: #0088cc;
}

.header-icon {
  font-size: 22px;
}

.blast-content {
  padding: 0 10px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin: 20px 0 12px;
  padding-bottom: 8px;
  border-bottom: 2px solid rgba(0, 136, 204, 0.2);
}

.section-title .el-icon {
  color: #0088cc;
  font-size: 18px;
}

.query-info {
  background: linear-gradient(135deg, rgba(0, 136, 204, 0.05) 0%, rgba(0, 180, 255, 0.08) 100%);
  border-radius: 8px;
  padding: 16px;
  border: 1px solid rgba(0, 136, 204, 0.15);
}

.sequence-id {
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 13px;
  color: #606266;
  word-break: break-all;
}

.subject-id {
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 12px;
  color: #409EFF;
  cursor: pointer;
}

.subject-id:hover {
  text-decoration: underline;
}

.evalue {
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 12px;
  color: #67C23A;
}

.hits-table {
  margin-top: 12px;
}

:deep(.el-table) {
  font-size: 13px;
}

:deep(.el-table th.el-table__cell) {
  background: linear-gradient(135deg, rgba(0, 136, 204, 0.08) 0%, rgba(0, 180, 255, 0.12) 100%);
  color: #0088cc;
  font-weight: 600;
}

:deep(.el-table .el-table__row:hover > td) {
  background-color: rgba(0, 136, 204, 0.06) !important;
}

:deep(.el-table .current-row > td) {
  background-color: rgba(0, 136, 204, 0.1) !important;
}

.hit-detail {
  margin-top: 20px;
  background: linear-gradient(135deg, rgba(103, 194, 58, 0.05) 0%, rgba(103, 194, 58, 0.08) 100%);
  border-radius: 8px;
  padding: 16px;
  border: 1px solid rgba(103, 194, 58, 0.2);
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 20px;
  border-top: 1px solid #ebeef5;
}

:deep(.el-descriptions__label) {
  width: 120px;
  font-weight: 500;
  color: #606266;
}

:deep(.el-descriptions__content) {
  color: #303133;
}

:deep(.el-progress__text) {
  font-size: 12px !important;
}
</style>
