<template>
  <div class="visualization-container">
    <el-card v-if="!taskId || !argData">
      <el-empty description="请先选择一个已完成的 ARG 分析任务">
        <el-button type="primary" @click="router.push('/history')">
          查看历史记录
        </el-button>
      </el-empty>
    </el-card>
    
    <template v-else>
      <!-- 顶部信息栏 -->
      <el-card class="info-header">
        <el-descriptions :column="3" border>
          <el-descriptions-item label="任务ID">{{ taskId }}</el-descriptions-item>
          <el-descriptions-item label="任务名称">{{ argData.genomeInfo?.taskName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="分析类型">
            <el-tag type="warning">抗性基因检测 (ARG)</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="识别结果" :span="3">
            <el-tag type="success" size="large">
              <el-icon><Document /></el-icon>
              共 {{ argResults.length }} 条序列，其中 {{ argPositiveCount }} 条预测为抗性基因
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>
      </el-card>
      
      <!-- 标签页 -->
      <el-card class="tabs-card">
        <el-tabs v-model="activeTab" @tab-click="handleTabClick">
          <!-- ARG 预测结果详情 -->
          <el-tab-pane label="预测结果详情" name="detail">
            <div class="detail-content" v-loading="loading">
              <div class="detail-header">
                <div>
                  <h3> 抗性基因预测结果</h3>
                  <p class="summary-desc">
                    共分析 <strong>{{ argResults.length }}</strong> 条序列，
                    其中 <strong style="color: #67C23A;">{{ argPositiveCount }}</strong> 条预测为抗性基因，
                    <strong style="color: #909399;">{{ argNegativeCount }}</strong> 条预测为非抗性基因
                  </p>
                </div>
                <el-button type="primary" :icon="Download" @click="downloadArgResults">
                  下载 ARG 预测结果
                </el-button>
              </div>
              
              <!-- 颜色图例 -->
              <el-alert 
                v-if="argResults.length > 0"
                type="info" 
                :closable="false" 
                style="margin-bottom: 16px;"
              >
                <template #title>
                  <div class="legend-container">
                    <span style="font-weight: bold; margin-right: 20px;">表格颜色说明：</span>
                    <span class="legend-item">
                      <span class="legend-box arg-positive-box"></span>
                      绿色 = 预测为抗性基因
                    </span>
                    <span class="legend-item">
                      <span class="legend-box arg-negative-box"></span>
                      灰色 = 预测为非抗性基因
                    </span>
                  </div>
                </template>
              </el-alert>
              
              <el-empty v-if="argResults.length === 0" description="没有 ARG 预测结果" />
              
              <template v-else>
                <!-- 筛选和搜索 -->
                <div class="table-toolbar">
                  <el-input
                    v-model="searchKeyword"
                    placeholder="搜索序列 ID..."
                    clearable
                    style="width: 300px;"
                    @input="handleSearch"
                  >
                    <template #prefix>
                      <el-icon><Search /></el-icon>
                    </template>
                  </el-input>
                  <el-select v-model="filterArgType" placeholder="筛选类型" style="width: 150px;" @change="handleFilter">
                    <el-option label="全部" value="all" />
                    <el-option label="仅 ARG" value="arg" />
                    <el-option label="仅非 ARG" value="non-arg" />
                  </el-select>
                  <span class="filter-info">
                    显示 {{ filteredResults.length }} / {{ argResults.length }} 条
                  </span>
                </div>
                
                <el-table 
                  :data="paginatedResults" 
                  border
                  style="margin-top: 16px;"
                  :row-class-name="getArgRowClassName"
                  max-height="500"
                >
                  <el-table-column type="index" label="索引" width="70" align="center">
                    <template #default="scope">
                      {{ (pagination.currentPage - 1) * pagination.pageSize + scope.$index + 1 }}
                    </template>
                  </el-table-column>
                  <el-table-column prop="id" label="序列 ID" min-width="300" show-overflow-tooltip />
                  <el-table-column label="是否为 ARG" width="120" align="center">
                    <template #default="{ row }">
                      <el-tag :type="row.isArg ? 'success' : 'danger'" size="small">
                        {{ row.isArg ? '是' : '否' }}
                      </el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column label="预测概率" width="120" align="center">
                    <template #default="{ row }">
                      <span v-if="row.predProb !== null && row.predProb !== undefined">
                        {{ (row.predProb * 100).toFixed(2) }}%
                      </span>
                      <span v-else style="color: #999;">-</span>
                    </template>
                  </el-table-column>
                  <el-table-column prop="argClass" label="ARG 分类" width="180" align="center">
                    <template #default="{ row }">
                      <!-- 有 Top-5 数据时显示悬停弹窗 -->
                      <el-popover
                        v-if="row.argClass && row.topClasses && row.topClasses.length > 0"
                        placement="top"
                        :width="300"
                        trigger="hover"
                        popper-class="top-classes-popover"
                      >
                        <template #reference>
                          <el-tag type="info" size="small" class="arg-class-tag">
                            {{ row.argClass }}
                            <el-icon class="info-icon"><InfoFilled /></el-icon>
                          </el-tag>
                        </template>
                        
                        <!-- 弹窗内容：Top-5 分类 -->
                        <div class="top-classes-content">
                          <div class="popover-title">分类概率分布 (Top 5)</div>
                          <div 
                            v-for="(item, idx) in row.topClasses" 
                            :key="idx"
                            :class="['top-class-item', { 'top-class-first': idx === 0 }]"
                          >
                            <span class="rank">{{ idx + 1 }}.</span>
                            <span class="class-name">{{ item.class }}</span>
                            <el-progress 
                              :percentage="item.prob * 100" 
                              :stroke-width="10"
                              :show-text="false"
                              :color="getProgressColor(item.prob * 100)"
                              style="width: 80px; margin: 0 8px;"
                            />
                            <span class="prob-value">{{ (item.prob * 100).toFixed(1) }}%</span>
                          </div>
                        </div>
                      </el-popover>
                      
                      <!-- 没有 Top-5 数据时普通显示 -->
                      <el-tag v-else-if="row.argClass" type="info" size="small">
                        {{ row.argClass }}
                      </el-tag>
                      <span v-else style="color: #999;">-</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="分类概率" width="120" align="center">
                    <template #default="{ row }">
                      <span v-if="row.classProb !== null && row.classProb !== undefined">
                        {{ (row.classProb * 100).toFixed(2) }}%
                      </span>
                      <span v-else style="color: #999;">-</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="BLAST" width="100" align="center" fixed="right">
                    <template #default="{ row }">
                      <el-button
                        v-if="row.isArg"
                        type="primary"
                        size="small"
                        :icon="Search"
                        @click="handleBlast(row)"
                        class="blast-btn"
                      >
                        BLAST
                      </el-button>
                      <span v-else style="color: #ccc;">-</span>
                    </template>
                  </el-table-column>
                </el-table>
                
                <!-- 分页 -->
                <div class="pagination-wrapper">
                  <el-pagination
                    v-model:current-page="pagination.currentPage"
                    v-model:page-size="pagination.pageSize"
                    :page-sizes="[50, 100, 200, 500]"
                    :total="filteredResults.length"
                    layout="total, sizes, prev, pager, next, jumper"
                    @size-change="handlePageSizeChange"
                    @current-change="handlePageChange"
                  />
                </div>
              </template>
            </div>
          </el-tab-pane>
          
          <!-- 可视化图表标签页 -->
          <el-tab-pane label="可视化图表" name="charts">
            <div class="charts-content" v-loading="loading">
              <div class="charts-header">
                <h3> ARG 预测结果可视化</h3>
                <el-button type="primary" :icon="Download" @click="downloadChartImages">
                  下载图表图片
                </el-button>
              </div>
              
              <el-empty v-if="argResults.length === 0" description="没有 ARG 预测结果可供可视化" />
              
              <div v-else class="charts-grid">
                <!-- 饼图：ARG 与非 ARG 数量分布 -->
                <div class="chart-container">
                  <h4> ARG 与非 ARG 序列分布</h4>
                  <p class="chart-desc">抗性基因与非抗性基因的数量占比</p>
                  <div ref="pieChartRef" class="chart" style="height: 400px;"></div>
                </div>
                
                <!-- 柱状图：各 ARG 类别分布 -->
                <div class="chart-container">
                  <h4> ARG 分类统计</h4>
                  <p class="chart-desc">抗性基因的序列中，各个 ARG 类别的数量分布</p>
                  <div ref="barChartRef" class="chart" style="height: 400px;"></div>
                  <el-empty v-if="argClassStats.length === 0 && argPositiveCount > 0" description="暂无分类信息" />
                </div>
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </el-card>
      
      <!-- 下载结果区域 -->
      <el-card class="download-card">
        <template #header>
          <div class="card-header">
            <span><el-icon><Download /></el-icon> 下载分析结果</span>
          </div>
        </template>
        
        <div class="download-content" v-loading="downloadLoading">
          <el-alert 
            v-if="isMagTask" 
            type="info" 
            :closable="false" 
            style="margin-bottom: 16px;"
          >
            <template #title>
              <span>这是一个 MAG 分析任务，可下载 Prodigal 预处理结果</span>
            </template>
          </el-alert>
          
          <div class="download-items">
            <!-- ARG 预测结果 - 所有任务都有 -->
            <div class="download-item">
              <div class="download-info">
                <el-icon class="file-icon"><Document /></el-icon>
                <div class="file-details">
                  <span class="file-name">ARG 预测结果</span>
                  <span class="file-desc">包含所有序列的抗性基因预测结果 (TSV 格式)</span>
                  <span class="file-size" v-if="downloadFiles.arg">{{ downloadFiles.arg.sizeFormatted }}</span>
                </div>
              </div>
              <el-button type="primary" size="small" @click="handleDownload('arg')" :loading="downloading.arg">
                <el-icon><Download /></el-icon> 下载
              </el-button>
            </div>
            
            <!-- MAG 任务特有的下载选项 -->
            <template v-if="isMagTask">
              <!-- 合并后的蛋白质序列 -->
              <div class="download-item" v-if="downloadFiles.merged">
                <div class="download-info">
                  <el-icon class="file-icon" style="color: #67C23A;"><Document /></el-icon>
                  <div class="file-details">
                    <span class="file-name">合并后的蛋白质序列</span>
                    <span class="file-desc">Prodigal 预测后合并的所有蛋白质序列 (FAA 格式)</span>
                    <span class="file-size">{{ downloadFiles.merged.sizeFormatted }}</span>
                  </div>
                </div>
                <el-button type="success" size="small" @click="handleDownload('merged')" :loading="downloading.merged">
                  <el-icon><Download /></el-icon> 下载
                </el-button>
              </div>
              
              <!-- Prodigal 结果打包 -->
              <div class="download-item" v-if="downloadFiles.prodigal">
                <div class="download-info">
                  <el-icon class="file-icon" style="color: #E6A23C;"><FolderOpened /></el-icon>
                  <div class="file-details">
                    <span class="file-name">Prodigal 预测结果</span>
                    <span class="file-desc">{{ downloadFiles.prodigal.name }} (ZIP 打包)</span>
                    <span class="file-size">{{ downloadFiles.prodigal.sizeFormatted }}</span>
                  </div>
                </div>
                <el-button type="warning" size="small" @click="handleDownload('prodigal')" :loading="downloading.prodigal">
                  <el-icon><Download /></el-icon> 下载
                </el-button>
              </div>
            </template>
            
            <!-- 一键下载全部 -->
            <div class="download-item download-all">
              <div class="download-info">
                <el-icon class="file-icon" style="color: #409EFF;"><Files /></el-icon>
                <div class="file-details">
                  <span class="file-name">下载全部结果</span>
                  <span class="file-desc">打包下载所有分析结果文件 (ZIP 格式)</span>
                </div>
              </div>
              <el-button type="primary" @click="handleDownload('all')" :loading="downloading.all">
                <el-icon><Download /></el-icon> 一键下载全部
              </el-button>
            </div>
          </div>
        </div>
      </el-card>
    </template>
    
    <!-- BLAST 结果抽屉 -->
    <BlastDrawer
      v-model="blastDrawerVisible"
      :task-id="taskId"
      :sequence-id="currentBlastSequenceId"
      :arg-class="currentBlastArgClass"
    />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch, nextTick, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Download, Document, Search, FolderOpened, Files, InfoFilled } from '@element-plus/icons-vue'
import { getGenomeVisualization } from '@/api/visualization'
import { getDownloadableFiles, downloadFile } from '@/api/download'
import { useUserStore } from '@/stores/user'
import * as echarts from 'echarts'
import BlastDrawer from '@/components/BlastDrawer.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// 状态管理
const taskId = ref(null)
const activeTab = ref('detail')
const loading = ref(false)
const argData = ref(null)

// 下载相关状态
const downloadLoading = ref(false)
const downloadFiles = ref({})
const isMagTask = ref(false)
const downloading = reactive({
  arg: false,
  merged: false,
  prodigal: false,
  all: false
})

// BLAST 相关状态
const blastDrawerVisible = ref(false)
const currentBlastSequenceId = ref('')
const currentBlastArgClass = ref('')

// 图表 ref
const pieChartRef = ref(null)
const barChartRef = ref(null)
let pieChartInstance = null
let barChartInstance = null

// 分页和筛选状态
const pagination = reactive({
  currentPage: 1,
  pageSize: 100
})
const searchKeyword = ref('')
const filterArgType = ref('all')

// 计算属性
const argResults = computed(() => argData.value?.argResults || [])
const argPositiveCount = computed(() => argResults.value.filter(r => r.isArg).length)
const argNegativeCount = computed(() => argResults.value.filter(r => !r.isArg).length)

// 筛选后的结果
const filteredResults = computed(() => {
  let results = argResults.value
  
  // 按类型筛选
  if (filterArgType.value === 'arg') {
    results = results.filter(r => r.isArg)
  } else if (filterArgType.value === 'non-arg') {
    results = results.filter(r => !r.isArg)
  }
  
  // 按关键词搜索
  if (searchKeyword.value.trim()) {
    const keyword = searchKeyword.value.trim().toLowerCase()
    results = results.filter(r => 
      r.id?.toLowerCase().includes(keyword) ||
      r.argClass?.toLowerCase().includes(keyword)
    )
  }
  
  return results
})

// 当前页的数据
const paginatedResults = computed(() => {
  const start = (pagination.currentPage - 1) * pagination.pageSize
  const end = start + pagination.pageSize
  return filteredResults.value.slice(start, end)
})

// 统计各 ARG 类别的数量
const argClassStats = computed(() => {
  const stats = {}
  argResults.value.forEach(r => {
    if (r.isArg && r.argClass) {
      stats[r.argClass] = (stats[r.argClass] || 0) + 1
    }
  })
  // 转换为数组并按数量降序排序
  return Object.entries(stats)
    .map(([name, value]) => ({ name, value }))
    .sort((a, b) => b.value - a.value)
})

// 初始化
onMounted(async () => {
  taskId.value = route.query.taskId ? parseInt(route.query.taskId) : null
  
  if (taskId.value) {
    await loadData()
  }
  
  // 监听窗口大小变化
  window.addEventListener('resize', handleResize)
})

// 组件销毁时清理
onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  pieChartInstance?.dispose()
  barChartInstance?.dispose()
})

// 监听标签页切换
watch(activeTab, async (newTab) => {
  if (newTab === 'charts') {
    await nextTick()
    initCharts()
  }
})

// 处理窗口大小变化
function handleResize() {
  pieChartInstance?.resize()
  barChartInstance?.resize()
}

// 处理标签页点击
function handleTabClick() {
  // 标签页切换由 watch 处理
}

// 处理搜索
function handleSearch() {
  pagination.currentPage = 1 // 搜索时重置到第一页
}

// 处理筛选
function handleFilter() {
  pagination.currentPage = 1 // 筛选时重置到第一页
}

// 处理 BLAST 比对
function handleBlast(row) {
  currentBlastSequenceId.value = row.id
  currentBlastArgClass.value = row.argClass || ''
  blastDrawerVisible.value = true
}

// 格式化概率（与预测概率一致，保留两位小数）
function formatProb(prob) {
  const num = Number(prob)
  if (Number.isNaN(num)) return '-'
  return (num * 100).toFixed(2)
}

// 获取进度条颜色（根据概率值）
function getProgressColor(percentage) {
  if (percentage >= 70) return '#67C23A'  // 绿色 - 高置信度
  if (percentage >= 50) return '#409EFF'  // 蓝色 - 中置信度
  if (percentage >= 30) return '#E6A23C'  // 橙色 - 低置信度
  return '#909399'  // 灰色 - 很低置信度
}

// 处理页码变化
function handlePageChange(page) {
  pagination.currentPage = page
}

// 处理每页数量变化
function handlePageSizeChange(size) {
  pagination.pageSize = size
  pagination.currentPage = 1 // 重置到第一页
}

// 加载数据
async function loadData() {
  loading.value = true
  try {
    const response = await getGenomeVisualization(taskId.value)
    argData.value = response.data
    
    // 加载可下载文件列表
    await loadDownloadableFiles()
    
    ElMessage.success('数据加载成功')
  } catch (error) {
    console.error('加载数据失败:', error)
    ElMessage.error('加载数据失败: ' + (error.response?.data?.message || error.message))
  } finally {
    loading.value = false
  }
}

// 加载可下载文件列表
async function loadDownloadableFiles() {
  downloadLoading.value = true
  try {
    const response = await getDownloadableFiles(taskId.value)
    // response 是 { code: 0, data: { taskId, isMagTask, files } }
    const data = response.data || response
    isMagTask.value = data.isMagTask || false
    
    // 整理文件信息
    const files = data.files || []
    downloadFiles.value = {}
    files.forEach(file => {
      if (file.key === 'arg_predictions') {
        downloadFiles.value.arg = file
      } else if (file.key === 'merged_faa') {
        downloadFiles.value.merged = file
      } else if (file.key === 'prodigal_results') {
        downloadFiles.value.prodigal = file
      }
    })
  } catch (error) {
    console.error('加载下载文件列表失败:', error)
  } finally {
    downloadLoading.value = false
  }
}

// 处理下载
async function handleDownload(type) {
  downloading[type] = true
  try {
    const token = userStore.token ? `Bearer ${userStore.token}` : ''
    await downloadFile(type, taskId.value, token)
    ElMessage.success('下载成功')
  } catch (error) {
    console.error('下载失败:', error)
    ElMessage.error('下载失败: ' + error.message)
  } finally {
    downloading[type] = false
  }
}

// 下载 ARG 预测结果
function downloadArgResults() {
  try {
    if (argResults.value.length === 0) {
      ElMessage.warning('没有可下载的数据')
      return
    }
    
    const fileName = `task_${taskId.value}_arg_predictions.tsv`
    
    ElMessage.info('正在准备下载...')
    
    // 构建 TSV 内容
    let tsvContent = 'id\tis_arg\tpred_prob\targ_class\tclass_prob\tprob\n'
    
    argResults.value.forEach(result => {
      tsvContent += `${result.id || ''}\t${result.isArg ? 'True' : 'False'}\t${result.predProb ?? ''}\t${result.argClass || ''}\t${result.classProb ?? ''}\t${result.prob ?? ''}\n`
    })
    
    // 创建下载
    const blob = new Blob([tsvContent], { type: 'text/tab-separated-values;charset=utf-8;' })
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = fileName
    a.click()
    window.URL.revokeObjectURL(url)
    
    ElMessage.success('下载成功')
  } catch (error) {
    console.error('下载失败:', error)
    ElMessage.error('下载失败: ' + error.message)
  }
}

// 获取 ARG 行的类名（用于设置背景色）
function getArgRowClassName({ row }) {
  return row.isArg ? 'arg-row-positive' : 'arg-row-negative'
}

// 初始化所有图表
function initCharts() {
  initPieChart()
  initBarChart()
}

// 初始化饼图 - ARG 与非 ARG 数量分布
function initPieChart() {
  if (!pieChartRef.value) return
  
  if (!pieChartInstance) {
    pieChartInstance = echarts.init(pieChartRef.value)
  }
  
  const option = {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(255, 255, 255, 0.98)',
      borderColor: 'rgba(0, 136, 204, 0.5)',
      borderWidth: 2,
      textStyle: {
        color: '#2c3e50'
      },
      formatter: (params) => {
        return `<strong style="color: #0088cc;">${params.name}</strong><br/>数量: ${params.value} 条<br/>占比: ${params.percent}%`
      }
    },
    legend: {
      orient: 'horizontal',
      bottom: 20,
      textStyle: {
        color: '#2c3e50',
        fontSize: 14,
        fontWeight: 500
      },
      itemWidth: 20,
      itemHeight: 14
    },
    series: [
      {
        name: 'ARG 分布',
        type: 'pie',
        radius: ['35%', '65%'],
        center: ['50%', '45%'],
        avoidLabelOverlap: true,
        itemStyle: {
          borderRadius: 8,
          borderColor: '#fff',
          borderWidth: 3,
          shadowBlur: 15,
          shadowColor: 'rgba(0, 0, 0, 0.15)'
        },
        label: {
          show: true,
          position: 'outside',
          formatter: '{b}\n{c} 条 ({d}%)',
          fontSize: 13,
          fontWeight: 600,
          color: '#2c3e50',
          lineHeight: 20
        },
        labelLine: {
          show: true,
          length: 20,
          length2: 30,
          lineStyle: {
            color: 'rgba(0, 136, 204, 0.5)',
            width: 2
          }
        },
        emphasis: {
          label: {
            show: true,
            fontSize: 16,
            fontWeight: 'bold'
          },
          itemStyle: {
            shadowBlur: 25,
            shadowColor: 'rgba(0, 136, 204, 0.4)'
          }
        },
        data: [
          { 
            value: argPositiveCount.value, 
            name: '抗性基因 (ARG)',
            itemStyle: { 
              color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                { offset: 0, color: '#67C23A' },
                { offset: 1, color: '#95d475' }
              ])
            }
          },
          { 
            value: argNegativeCount.value, 
            name: '非抗性基因',
            itemStyle: { 
              color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                { offset: 0, color: '#909399' },
                { offset: 1, color: '#C0C4CC' }
              ])
            }
          }
        ]
      }
    ]
  }
  
  pieChartInstance.setOption(option, true)
}

// 初始化柱状图 - 各 ARG 类别分布
function initBarChart() {
  if (!barChartRef.value) return
  
  if (!barChartInstance) {
    barChartInstance = echarts.init(barChartRef.value)
  }
  
  const stats = argClassStats.value
  
  if (stats.length === 0) {
    barChartInstance.clear()
    return
  }
  
  // 生成渐变色
  const colors = [
    ['#409EFF', '#79bbff'],
    ['#67C23A', '#95d475'],
    ['#E6A23C', '#eebe77'],
    ['#F56C6C', '#fab6b6'],
    ['#9C27B0', '#ce93d8'],
    ['#00BCD4', '#4dd0e1'],
    ['#FF5722', '#ff8a65'],
    ['#795548', '#a1887f'],
    ['#607D8B', '#90a4ae'],
    ['#3F51B5', '#7986cb']
  ]
  
  const option = {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(255, 255, 255, 0.98)',
      borderColor: 'rgba(0, 136, 204, 0.5)',
      borderWidth: 2,
      textStyle: {
        color: '#2c3e50'
      },
      axisPointer: {
        type: 'shadow',
        shadowStyle: {
          color: 'rgba(0, 136, 204, 0.1)'
        }
      },
      formatter: (params) => {
        const data = params[0]
        return `<strong style="color: #0088cc;">${data.name}</strong><br/>数量: ${data.value} 条`
      }
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: stats.length > 5 ? '25%' : '15%',
      top: '10%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: stats.map(s => s.name),
      axisLabel: {
        color: '#2c3e50',
        fontSize: 12,
        fontWeight: 500,
        rotate: stats.length > 5 ? 45 : 0,
        interval: 0
      },
      axisLine: {
        lineStyle: {
          color: 'rgba(0, 136, 204, 0.3)'
        }
      },
      axisTick: {
        alignWithLabel: true,
        lineStyle: {
          color: 'rgba(0, 136, 204, 0.3)'
        }
      }
    },
    yAxis: {
      type: 'value',
      name: '数量',
      nameTextStyle: {
        color: '#0088cc',
        fontSize: 13,
        fontWeight: 600
      },
      axisLabel: {
        color: '#606266',
        fontSize: 12
      },
      axisLine: {
        show: true,
        lineStyle: {
          color: 'rgba(0, 136, 204, 0.3)'
        }
      },
      splitLine: {
        lineStyle: {
          type: 'dashed',
          color: 'rgba(0, 136, 204, 0.15)'
        }
      }
    },
    series: [
      {
        name: 'ARG 类别数量',
        type: 'bar',
        barWidth: stats.length > 8 ? '50%' : '40%',
        data: stats.map((s, index) => ({
          value: s.value,
          itemStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: colors[index % colors.length][0] },
              { offset: 1, color: colors[index % colors.length][1] }
            ]),
            borderRadius: [6, 6, 0, 0],
            shadowBlur: 8,
            shadowColor: 'rgba(0, 0, 0, 0.15)',
            shadowOffsetY: 4
          }
        })),
        label: {
          show: true,
          position: 'top',
          color: '#0088cc',
          fontSize: 12,
          fontWeight: 600
        },
        emphasis: {
          itemStyle: {
            shadowBlur: 15,
            shadowColor: 'rgba(0, 136, 204, 0.4)'
          }
        }
      }
    ],
    dataZoom: stats.length > 10 ? [{
      type: 'slider',
      xAxisIndex: 0,
      start: 0,
      end: Math.min(100, (10 / stats.length) * 100),
      height: 20,
      bottom: 5
    }] : []
  }
  
  barChartInstance.setOption(option, true)
}

// 下载图表图片
function downloadChartImages() {
  try {
    ElMessage.info('正在生成图片...')
    
    let downloadCount = 0
    
    // 下载饼图
    if (pieChartInstance) {
      const pieUrl = pieChartInstance.getDataURL({
        type: 'png',
        pixelRatio: 2,
        backgroundColor: '#ffffff'
      })
      const a1 = document.createElement('a')
      a1.href = pieUrl
      a1.download = `task_${taskId.value}_arg_distribution_pie.png`
      a1.click()
      downloadCount++
    }
    
    // 下载柱状图
    if (barChartInstance && argClassStats.value.length > 0) {
      setTimeout(() => {
        const barUrl = barChartInstance.getDataURL({
          type: 'png',
          pixelRatio: 2,
          backgroundColor: '#ffffff'
        })
        const a2 = document.createElement('a')
        a2.href = barUrl
        a2.download = `task_${taskId.value}_arg_class_distribution_bar.png`
        a2.click()
        
        ElMessage.success('图表图片下载成功')
      }, 500)
    } else {
      if (downloadCount > 0) {
        ElMessage.success('饼图下载成功')
      } else {
        ElMessage.warning('没有可下载的图表')
      }
    }
  } catch (error) {
    console.error('下载失败:', error)
    ElMessage.error('下载失败: ' + error.message)
  }
}
</script>

<style scoped>
.visualization-container {
  padding: 20px;
}

/* 亮色主题 - 与科技青色主题搭配 */
:deep(.el-card) {
  background: linear-gradient(135deg, #ffffff 0%, #f8feff 100%);
  border: 2px solid rgba(0, 180, 255, 0.2);
  box-shadow: 0 4px 20px rgba(0, 180, 255, 0.1);
  color: #2c3e50;
}

:deep(.el-card__header) {
  background: linear-gradient(135deg, rgba(0, 180, 255, 0.08) 0%, rgba(0, 200, 255, 0.12) 100%);
  border-bottom: 2px solid rgba(0, 180, 255, 0.25);
  color: #0088cc;
  font-weight: 600;
}

:deep(.el-descriptions__label) {
  color: #0088cc;
  background: rgba(0, 180, 255, 0.08);
  font-weight: 600;
}

:deep(.el-descriptions__content) {
  color: #2c3e50;
  background: #ffffff;
}

:deep(.el-table) {
  background: #ffffff;
  color: #2c3e50;
}

:deep(.el-table th.el-table__cell) {
  background: linear-gradient(135deg, rgba(0, 180, 255, 0.12) 0%, rgba(0, 200, 255, 0.18) 100%);
  color: #0088cc;
  border-bottom: 2px solid rgba(0, 180, 255, 0.3);
  font-weight: 600;
}

:deep(.el-table tr) {
  background: #ffffff;
}

:deep(.el-table td.el-table__cell) {
  border-bottom: 1px solid rgba(0, 180, 255, 0.1);
  color: #2c3e50;
}

:deep(.el-tabs__item) {
  color: #606266;
  font-weight: 500;
}

:deep(.el-tabs__item.is-active) {
  color: #0088cc;
  font-weight: 600;
}

:deep(.el-tabs__active-bar) {
  background-color: #0088cc;
}

h3, h4, h5 {
  color: #0088cc;
  text-shadow: 0 2px 4px rgba(0, 136, 204, 0.15);
  font-weight: 600;
}

.info-header {
  margin-bottom: 20px;
}

.tabs-card {
  margin-top: 20px;
}

/* 下载卡片样式 */
.download-card {
  margin-top: 20px;
}

.download-card .card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  color: #0088cc;
}

.download-card .card-header .el-icon {
  font-size: 18px;
}

.download-content {
  padding: 10px 0;
}

.download-items {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.download-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  background: rgba(0, 136, 204, 0.03);
  border: 1px solid rgba(0, 136, 204, 0.15);
  border-radius: 8px;
  transition: all 0.3s ease;
}

.download-item:hover {
  background: rgba(0, 136, 204, 0.08);
  border-color: rgba(0, 136, 204, 0.25);
}

.download-item.download-all {
  background: rgba(64, 158, 255, 0.05);
  border-color: rgba(64, 158, 255, 0.2);
}

.download-item.download-all:hover {
  background: rgba(64, 158, 255, 0.1);
  border-color: rgba(64, 158, 255, 0.3);
}

.download-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.download-info .file-icon {
  font-size: 32px;
  color: #0088cc;
}

.file-details {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.file-details .file-name {
  font-weight: 600;
  color: #2c3e50;
  font-size: 14px;
}

.file-details .file-desc {
  color: #606266;
  font-size: 12px;
}

.file-details .file-size {
  color: #909399;
  font-size: 11px;
}

.detail-content {
  padding: 20px;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20px;
}

.summary-desc {
  color: #606266;
  font-size: 14px;
  margin: 8px 0 16px;
}

h3 {
  margin: 0 0 16px;
  font-size: 18px;
  color: #303133;
}

/* 图例样式 */
.legend-container {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 15px;
  color: #2c3e50;
}

.legend-item {
  display: flex;
  align-items: center;
  font-size: 13px;
  color: #2c3e50;
  font-weight: 500;
}

.legend-box {
  display: inline-block;
  width: 20px;
  height: 14px;
  margin-right: 6px;
  border: 1px solid #dcdfe6;
  border-radius: 2px;
}

/* ARG 结果表格颜色 */
.arg-positive-box {
  background-color: #d4edda;  /* 绿色 - 是抗性基因 */
}

.arg-negative-box {
  background-color: #E4E7ED;  /* 灰色 - 不是抗性基因 */
}

/* ARG 表格行颜色 */
:deep(.arg-row-positive > td.el-table__cell) {
  background-color: #d4edda !important;  /* 绿色 - 是抗性基因 */
}

:deep(.arg-row-negative > td.el-table__cell) {
  background-color: #E4E7ED !important;  /* 灰色 - 不是抗性基因 */
}

/* BLAST 按钮样式 */
.blast-btn {
  background: linear-gradient(135deg, #0088cc 0%, #00b4ff 100%);
  border: none;
  font-weight: 500;
  padding: 4px 10px;
  transition: all 0.3s ease;
}

.blast-btn:hover {
  background: linear-gradient(135deg, #0077b3 0%, #0099dd 100%);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(0, 136, 204, 0.3);
}

.blast-btn:active {
  transform: translateY(0);
}

/* ARG 分类标签样式 */
.arg-class-tag {
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  transition: all 0.2s ease;
}

.arg-class-tag:hover {
  background-color: rgba(0, 136, 204, 0.15);
  border-color: #0088cc;
}

.arg-class-tag .info-icon {
  font-size: 12px;
  color: #909399;
  transition: color 0.2s ease;
}

.arg-class-tag:hover .info-icon {
  color: #0088cc;
}

/* Top-5 分类弹窗样式 */
.top-classes-content {
  padding: 4px 0;
}

.top-classes-content .popover-title {
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid #ebeef5;
  font-size: 14px;
}

.top-classes-content .top-class-item {
  display: flex;
  align-items: center;
  padding: 6px 4px;
  border-radius: 4px;
  transition: background-color 0.2s ease;
}

.top-classes-content .top-class-item:hover {
  background-color: rgba(0, 136, 204, 0.05);
}

.top-classes-content .top-class-first {
  background: linear-gradient(135deg, rgba(103, 194, 58, 0.1) 0%, rgba(103, 194, 58, 0.15) 100%);
  border-left: 3px solid #67C23A;
  margin-left: -4px;
  padding-left: 8px;
}

.top-classes-content .rank {
  width: 24px;
  color: #909399;
  font-weight: 500;
  font-size: 13px;
}

.top-classes-content .class-name {
  flex: 1;
  font-size: 13px;
  color: #606266;
  font-weight: 500;
}

.top-classes-content .top-class-first .class-name {
  color: #67C23A;
  font-weight: 600;
}

.top-classes-content .prob-value {
  width: 50px;
  text-align: right;
  font-family: 'Monaco', 'Menlo', monospace;
  color: #409EFF;
  font-weight: 500;
  font-size: 12px;
}

.top-classes-content .top-class-first .prob-value {
  color: #67C23A;
}

/* 分类概率文本样式 */
.class-prob-text {
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 13px;
  font-weight: 600;
  color: #409EFF;
}

/* 表格工具栏样式 */
.table-toolbar {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
  padding: 12px 16px;
  background: linear-gradient(135deg, rgba(0, 180, 255, 0.05) 0%, rgba(0, 200, 255, 0.08) 100%);
  border-radius: 8px;
  border: 1px solid rgba(0, 180, 255, 0.15);
}

.filter-info {
  margin-left: auto;
  font-size: 13px;
  color: #606266;
  font-weight: 500;
}

/* 分页样式 */
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid rgba(0, 180, 255, 0.15);
}

:deep(.el-pagination) {
  --el-pagination-bg-color: transparent;
  --el-pagination-hover-color: #0088cc;
}

:deep(.el-pagination .el-pager li) {
  background: rgba(0, 180, 255, 0.05);
  color: #2c3e50;
  border: 1px solid rgba(0, 180, 255, 0.2);
  border-radius: 4px;
  margin: 0 2px;
}

:deep(.el-pagination .el-pager li.is-active) {
  background: linear-gradient(135deg, #0088cc 0%, #00b4ff 100%);
  color: #ffffff;
  border-color: #0088cc;
}

:deep(.el-pagination .el-pager li:hover:not(.is-active)) {
  color: #0088cc;
  background: rgba(0, 180, 255, 0.1);
}

/* 结果可视化初始页面的空状态样式 */
.visualization-container :deep(.el-empty) {
  background: transparent;
}

.visualization-container :deep(.el-empty__image svg) {
  fill: rgba(0, 180, 255, 0.4) !important;
}

.visualization-container :deep(.el-empty__image) {
  filter: none;
  opacity: 1;
}

.visualization-container :deep(.el-empty__description) {
  color: #00d4ff;
}

.visualization-container :deep(.el-empty__image path) {
  fill: rgba(0, 180, 255, 0.4) !important;
  stroke: rgba(0, 255, 255, 0.3) !important;
}

/* 可视化图表标签页样式 */
.charts-content {
  padding: 20px;
}

.charts-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 24px;
}

.charts-header h3 {
  margin: 0;
  font-size: 20px;
  color: #0088cc;
  font-weight: 600;
}

.charts-desc {
  color: #606266;
  font-size: 14px;
  margin: 8px 0 0;
}

.charts-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(500px, 1fr));
  gap: 24px;
}

@media (max-width: 1100px) {
  .charts-grid {
    grid-template-columns: 1fr;
  }
}

.chart-container {
  background: linear-gradient(135deg, rgba(0, 180, 255, 0.03) 0%, rgba(0, 200, 255, 0.06) 100%);
  padding: 20px;
  border-radius: 12px;
  border: 2px solid rgba(0, 180, 255, 0.2);
  box-shadow: 0 4px 16px rgba(0, 180, 255, 0.1);
  transition: all 0.3s ease;
}

.chart-container:hover {
  border-color: rgba(0, 180, 255, 0.4);
  box-shadow: 0 6px 24px rgba(0, 180, 255, 0.15);
}

.chart-container h4 {
  margin: 0 0 8px;
  font-size: 16px;
  color: #0088cc;
  font-weight: 600;
}

.chart-container .chart-desc {
  color: #909399;
  font-size: 13px;
  margin: 0 0 16px;
  line-height: 1.5;
}

.chart {
  width: 100%;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.9) 0%, rgba(248, 254, 255, 0.95) 100%);
  border-radius: 8px;
  border: 1px solid rgba(0, 180, 255, 0.15);
}
</style>

