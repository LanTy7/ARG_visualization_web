<template>
  <div class="visualization-container">
    <el-card v-if="!taskId || !genomeData">
      <el-empty description="请先选择一个已完成的分析任务">
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
          <el-descriptions-item label="任务名称">{{ genomeData.genomeInfo?.taskName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="基因组长度">
            {{ (genomeData.genomeInfo?.genomeLength || 0).toLocaleString() }} bp
          </el-descriptions-item>
          <el-descriptions-item label="识别结果" :span="3">
            <el-tag type="success" size="large">
              <el-icon><Document /></el-icon>
              {{ prophageCount }} 个原噬菌体区域
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>
        <div class="action-buttons">
          
        </div>
      </el-card>
      
      <!-- 标签页切换 -->
      <el-card class="tabs-card">
        <el-tabs v-model="activeTab" @tab-click="handleTabClick">
          <!-- Summary 标签页 -->
          <el-tab-pane label="总览" name="summary">
            <div class="summary-content" v-loading="loading">
              <div class="summary-header-row">
                <div>
                  <h3>原噬菌体区域总览</h3>
                  <p class="summary-desc">
                    共识别到 <strong>{{ prophageCount }}</strong> 个原噬菌体区域
                  </p>
          </div>
                <el-button type="primary" :icon="Download" @click="downloadSummaryTable">
                  下载 Summary.tsv
                </el-button>
            </div>
              
              <!-- 颜色图例 -->
              <el-alert 
                v-if="prophageCount > 0"
                type="info" 
                :closable="false" 
                style="margin-bottom: 16px;"
              >
                <template #title>
                  <div class="legend-container">
                    <span style="font-weight: bold; margin-right: 20px;">表格颜色说明：</span>
                    <span class="legend-item">
                      <span class="legend-box intact-box"></span>
                      绿色 = Intact (得分 > 35)
                    </span>
                    <span class="legend-item">
                      <span class="legend-box questionable-box"></span>
                      蓝色 = Questionable (得分 25-35)
                    </span>
                    <span class="legend-item">
                      <span class="legend-box incomplete-box"></span>
                      黄色 = Incomplete (得分 < 25)
                    </span>
                  </div>
                </template>
              </el-alert>
              
              <el-table 
                :data="prophageRegions" 
                border
                style="margin-top: 16px;"
                :row-class-name="getRegionRowClassName"
              >
                <el-table-column type="index" label="区域" width="70" align="center" />
                <el-table-column prop="seqName" label="序列名称" min-width="250" show-overflow-tooltip />
                <el-table-column label="位置" width="200">
            <template #default="{ row }">
              {{ row.start?.toLocaleString() }} - {{ row.end?.toLocaleString() }}
            </template>
          </el-table-column>
                <el-table-column label="长度" width="120" align="right">
            <template #default="{ row }">
                    {{ (row.length / 1000).toFixed(1) }} Kb
            </template>
          </el-table-column>
                <el-table-column prop="nGenes" label="基因数" width="90" align="center" />
                <el-table-column label="质量评级" width="120" align="center">
            <template #default="{ row }">
                    <el-tag 
                      :type="getQualityType(row.vVsCScore)" 
                      size="small"
                    >
                      {{ getQualityLabel(row.vVsCScore) }}
              </el-tag>
            </template>
          </el-table-column>
                <el-table-column label="得分" width="100" align="center">
            <template #default="{ row }">
                    {{ row.vVsCScore?.toFixed(2) }}
            </template>
          </el-table-column>
                <el-table-column label="整合酶" width="80" align="center">
            <template #default="{ row }">
                    <el-tag v-if="row.integrases" type="success" size="small">有</el-tag>
                    <el-tag v-else type="info" size="small">无</el-tag>
            </template>
          </el-table-column>
              </el-table>
            </div>
          </el-tab-pane>
          
          <!-- Detail 标签页 - 显示所有原噬菌体的基因列表 -->
          <el-tab-pane label="详情" name="detail">
            <div class="detail-content" v-loading="detailLoading">
              <div class="detail-header">
                <h3>原噬菌体基因详情</h3>
                <el-button type="primary" :icon="Download" @click="downloadGenesFile">
                  下载基因列表 (TSV)
              </el-button>
              </div>
              
              <!-- 颜色图例和字段说明 -->
              <el-alert 
                v-if="prophageCount > 0"
                type="info" 
                :closable="false" 
                style="margin-bottom: 20px;"
              >
                <template #title>
                  <div>
                    <div class="legend-container" style="margin-bottom: 12px;">
                      <span style="font-weight: bold; margin-right: 20px;">基因表格颜色说明：</span>
                      <span class="legend-item">
                        <span class="legend-box phage-box"></span>
                        粉红色 = 匹配到病毒/噬菌体数据库
                      </span>
                      <span class="legend-item">
                        <span class="legend-box bacteria-box"></span>
                        紫色 = 未匹配或可能是细菌基因
                      </span>
                    </div>
                    
                  </div>
            </template>
              </el-alert>
              
              <el-empty v-if="prophageCount === 0" description="没有识别到原噬菌体区域" />
              
              <!-- 为每个原噬菌体显示一个基因列表 -->
              <div 
                v-for="(region, index) in allProphageDetails" 
                :key="region.regionId"
                class="prophage-detail-section"
              >
                <el-divider v-if="index > 0" />
                
                <div class="region-header-card">
                  <h4>
                    <el-tag type="primary" size="large">区域 {{ region.regionId }}</el-tag>
                    {{ region.seqName }}
                  </h4>
                  
                  <el-descriptions :column="3" border size="small" class="region-info-compact">
                    <el-descriptions-item label="位置">
                      {{ region.start?.toLocaleString() }} - {{ region.end?.toLocaleString() }}
                    </el-descriptions-item>
                    <el-descriptions-item label="长度">
                      {{ (region.length / 1000).toFixed(2) }} Kb
                    </el-descriptions-item>
                    <el-descriptions-item label="基因数">
                      {{ region.nGenes }}
                    </el-descriptions-item>
                    <el-descriptions-item label="得分">
                      {{ region.vVsCScore?.toFixed(3) }}
                    </el-descriptions-item>
                    <el-descriptions-item label="来源序列" :span="2">
                      {{ region.sourceSeq }}
                    </el-descriptions-item>
                  </el-descriptions>
                </div>
                
                <!-- 基因列表 -->
                <div class="genes-table-wrapper">
                  <h5>基因列表 ({{ region.genes?.length || 0 }} 个)</h5>
                  <el-table 
                    :data="region.genes" 
                    border
                    size="small"
                    max-height="400"
                    style="margin-top: 12px"
                    :row-class-name="getGeneRowClassName"
                  >
                    <el-table-column type="index" label="#" width="60" align="center" />
                    <el-table-column prop="gene" label="基因ID" min-width="200" show-overflow-tooltip />
          <el-table-column label="位置" width="180">
            <template #default="{ row }">
                        {{ row.start?.toLocaleString() }} - {{ row.end?.toLocaleString() }}
            </template>
          </el-table-column>
                    <el-table-column label="长度" width="100" align="right">
            <template #default="{ row }">
              {{ row.length }} bp
            </template>
          </el-table-column>
                    <el-table-column label="链" width="60" align="center">
            <template #default="{ row }">
                        <span :style="{ color: row.strand === 1 ? '#0088cc' : '#ff7043', fontWeight: 'bold' }">
                {{ row.strand === 1 ? '+' : '-' }}
                        </span>
                      </template>
          </el-table-column>
                    <el-table-column prop="marker" label="标记" width="150" show-overflow-tooltip>
                      <template #default="{ row }">
                        <el-tag v-if="row.marker && row.marker !== 'NA'" type="danger" size="small">
                          {{ row.marker }}
              </el-tag>
                        <span v-else style="color: #999999;">NA</span>
            </template>
          </el-table-column>
                    <el-table-column prop="taxname" label="分类" width="150" show-overflow-tooltip />
                    <el-table-column prop="annotationDescription" label="功能注释" min-width="250" show-overflow-tooltip />
        </el-table>
                </div>
              </div>
            </div>
          </el-tab-pane>
          
          <!-- Genome Viewer 标签页 -->
          <el-tab-pane label="基因组视图" name="genome">
            <div class="genome-viewer-content">
              <!-- 控制面板 -->
              <div class="viewer-controls">
                <el-space wrap>
                  <el-button 
                    :type="showLabels ? 'primary' : 'default'" 
                    @click="showLabels = !showLabels"
                    size="small"
                  >
                    {{ showLabels ? '隐藏标签' : '显示标签' }}
                  </el-button>
                  <el-button @click="resetZoom" size="small">重置缩放</el-button>
                  <el-button type="primary" :icon="Download" @click="downloadGenomeViewerImages" size="small">
                    下载图表图片
                  </el-button>
                  <el-divider direction="vertical" />
                  <span class="control-label">已选择: </span>
                  <el-tag v-if="selectedRegionForViewer" type="success" closable @close="clearSelection">
                    区域 {{ selectedRegionForViewer.regionId }}
                  </el-tag>
                  <el-tag v-else type="info">点击原噬菌体区域查看详情</el-tag>
                </el-space>
              </div>
              
              <!-- 基因组总览图 - 矩形基因组 -->
              <div class="chart-container">
                <h4>基因组总览 - 原噬菌体分布</h4>
                <p class="chart-desc">点击原噬菌体区域（彩色矩形）查看详细基因分布</p>
                <div ref="genomeOverviewChart" class="chart" style="height: 250px;"></div>
              </div>
              
              <!-- 详细基因图 -->
              <div v-show="selectedRegionForViewer" class="chart-container">
                <h4>
                  原噬菌体区域详细视图 - 区域 {{ selectedRegionForViewer?.regionId || '-' }}
                  <el-tag v-if="selectedRegionForViewer" type="info" size="small" style="margin-left: 12px">
                    {{ selectedRegionForViewer.start?.toLocaleString() }} - {{ selectedRegionForViewer.end?.toLocaleString() }}
                  </el-tag>
                </h4>
                
                <!-- 基因功能颜色图例 -->
                <div class="gene-color-legend">
                  <h5>基因功能颜色图例</h5>
                  <div class="legend-grid">
                    <div class="legend-item"><span class="color-box" style="background: #D32F2F;"></span>function 1</div>
                    <div class="legend-item"><span class="color-box" style="background: #26C6DA;"></span>function 2</div>
                    <div class="legend-item"><span class="color-box" style="background: #E91E63;"></span>function 3</div>
                    <div class="legend-item"><span class="color-box" style="background: #5C6BC0;"></span>function 4</div>
                    <div class="legend-item"><span class="color-box" style="background: #FF6B6B;"></span>function 5</div>
                    <div class="legend-item"><span class="color-box" style="background: #AB47BC;"></span>function 6</div>
                    <div class="legend-item"><span class="color-box" style="background: #66BB6A;"></span>Other functions</div>
                  </div>
                </div>
                
                <div ref="geneDetailChart" class="chart" style="height: 400px;"></div>
              </div>
              
              <el-empty 
                v-show="!selectedRegionForViewer" 
                description="点击基因组总览图中的原噬菌体区域，即可查看详细基因分布"
                style="margin-top: 40px;"
              />
            </div>
          </el-tab-pane>
        </el-tabs>
      </el-card>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download, Document } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { 
  getGenomeVisualization, 
  getProphageDetail,
  exportVisualizationData 
} from '@/api/visualization'

const route = useRoute()
const router = useRouter()

// 状态管理
const taskId = ref(null)
const activeTab = ref('summary')
const loading = ref(false)
const detailLoading = ref(false)
const genomeData = ref(null)
const allProphageDetails = ref([])
const selectedRegionForViewer = ref(null)
const showLabels = ref(true)

// ECharts 实例
const genomeOverviewChart = ref(null)
const geneDetailChart = ref(null)
let overviewChartInstance = null
let detailChartInstance = null

// 计算属性
const prophageRegions = computed(() => genomeData.value?.prophageRegions || [])
const prophageCount = computed(() => prophageRegions.value.length)

// 初始化
onMounted(async () => {
  taskId.value = route.query.taskId ? parseInt(route.query.taskId) : null
  
  if (taskId.value) {
    await loadData()
  }
})

// 监听标签页切换
watch(activeTab, async (newTab) => {
  if (newTab === 'detail' && allProphageDetails.value.length === 0) {
    await loadAllProphageDetails()
  } else if (newTab === 'genome') {
    await nextTick()
    initGenomeViewer()
  }
})

// 监听 showLabels 变化
watch(showLabels, () => {
  updateCharts()
})

// 监听选中区域变化
watch(selectedRegionForViewer, (newRegion, oldRegion) => {
  console.log('selectedRegionForViewer 变化:', {
    old: oldRegion?.regionId,
    new: newRegion?.regionId,
    hasGenes: newRegion?.genes?.length || 0
  })
  
  // 如果在 genome 标签页且有选中区域，更新详细图
  if (activeTab.value === 'genome' && newRegion) {
    // 使用多个 nextTick 确保 DOM 完全准备好
    nextTick(() => {
      nextTick(() => {
        if (geneDetailChart.value) {
          console.log('DOM已准备，开始更新图表')
          updateDetailChart()
        } else {
          console.warn('DOM未准备好，等待...')
          setTimeout(() => {
            updateDetailChart()
          }, 100)
        }
      })
    })
  }
})

// 加载数据
async function loadData() {
  loading.value = true
  try {
    // 加载基因组可视化数据
    const response = await getGenomeVisualization(taskId.value)
    genomeData.value = response.data
    
    ElMessage.success('数据加载成功')
  } catch (error) {
    console.error('加载数据失败:', error)
    ElMessage.error('加载数据失败: ' + (error.response?.data?.message || error.message))
  } finally {
    loading.value = false
  }
}

// 加载所有原噬菌体详情
async function loadAllProphageDetails() {
  if (prophageCount.value === 0) return
  
  detailLoading.value = true
  try {
    const details = []
    
    // 并行加载所有原噬菌体的详情
    const promises = prophageRegions.value.map(region => 
      getProphageDetail(taskId.value, region.regionId)
    )
    
    const responses = await Promise.all(promises)
    responses.forEach(response => {
      details.push(response.data)
    })
    
    allProphageDetails.value = details
    console.log('加载了', details.length, '个原噬菌体详情')
  } catch (error) {
    console.error('加载详情失败:', error)
    ElMessage.error('加载详情失败: ' + (error.response?.data?.message || error.message))
  } finally {
    detailLoading.value = false
  }
}

// 选择某个原噬菌体显示在Genome Viewer中
async function selectRegionForViewer(region) {
  try {
    console.log('选择区域:', region.regionId)
    
    // 如果已经加载过详情，直接使用
    let detail = allProphageDetails.value.find(d => d.regionId === region.regionId)
    
    if (!detail) {
      // 如果没有加载过，现在加载
      console.log('加载区域详情...')
      const response = await getProphageDetail(taskId.value, region.regionId)
      detail = response.data
      console.log('区域详情加载完成:', detail)
    }
    
    // 设置选中区域（watch 会自动触发 updateDetailChart）
    selectedRegionForViewer.value = detail
  } catch (error) {
    console.error('加载区域详情失败:', error)
    ElMessage.error('加载区域详情失败')
  }
}

// 处理标签页点击
function handleTabClick(tab) {
  // 标签页切换由 watch 处理
}

// 初始化基因组查看器
async function initGenomeViewer() {
  await nextTick()
  
  if (!genomeOverviewChart.value) {
    console.warn('genomeOverviewChart ref 未准备好')
    return
  }
  
  // 初始化总览图
  if (!overviewChartInstance) {
    overviewChartInstance = echarts.init(genomeOverviewChart.value)
    
    // 添加点击事件
    overviewChartInstance.on('click', async (params) => {
      if (params.componentType === 'series' && params.dataIndex !== undefined) {
        const region = prophageRegions.value[params.dataIndex]
        if (region) {
          await selectRegionForViewer(region)
        }
      }
    })
  }
  
  updateOverviewChart()
}

// 更新总览图 - 改为矩形基因组视图
function updateOverviewChart() {
  if (!overviewChartInstance || !genomeData.value) return
  
  const genomeLength = genomeData.value.genomeInfo?.genomeLength || 100000
  
  // 准备原噬菌体区域数据（使用与 Summary 表格相同的颜色方案）
  const prophageData = prophageRegions.value.map(region => ({
    name: showLabels.value ? `区域 ${region.regionId}` : '',
    value: [region.start, region.end],
    itemStyle: {
      // 与 Summary 表格颜色保持一致
      color: region.vVsCScore > 35 ? '#67C23A' : region.vVsCScore > 25 ? '#409EFF' : '#E6A23C'
    },
    regionId: region.regionId,
    ...region
  }))
  
  const option = {
    backgroundColor: 'transparent',
    title: {
      text: `细菌基因组长度: ${genomeLength.toLocaleString()} bp`,
      left: 'center',
      textStyle: { 
        fontSize: 14, 
        fontWeight: 'bold',
        color: '#0088cc'
      },
      top: 10
    },
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(255, 255, 255, 0.98)',
      borderColor: 'rgba(0, 136, 204, 0.5)',
      borderWidth: 2,
      textStyle: {
        color: '#2c3e50'
      },
      formatter: (params) => {
        const region = prophageData[params.dataIndex]
        return `
          <strong style="color: #0088cc;">原噬菌体区域 ${region.regionId}</strong><br/>
          位置: ${region.start?.toLocaleString()} - ${region.end?.toLocaleString()}<br/>
          长度: ${(region.length / 1000).toFixed(1) } Kb<br/>
          基因数: ${region.nGenes}<br/>
          得分: ${region.vVsCScore?.toFixed(2)}<br/>
          <span style="color: #999;">点击查看详细基因分布</span>
        `
      }
    },
    grid: {
      left: 100,
      right: 100,
      top: 80,
      bottom: 80
    },
    xAxis: {
      type: 'value',
      min: 0,
      max: genomeLength,
      name: '基因组位置 (bp)',
      nameLocation: 'center',
      nameGap: 50,
      nameTextStyle: {
        fontSize: 13,
        fontWeight: 'bold',
        color: '#2c3e50'
      },
      axisLabel: {
        color: '#606266',
        formatter: (value) => {
          if (value >= 1000000) {
            return (value / 1000000).toFixed(1) + 'M'
          }
          return (value / 1000).toFixed(0) + 'K'
        }
      },
      axisLine: {
        lineStyle: {
          color: 'rgba(0, 136, 204, 0.3)'
        }
      },
      splitLine: {
        show: true,
        lineStyle: {
          type: 'dashed',
          color: 'rgba(0, 136, 204, 0.15)'
        }
      }
    },
    yAxis: {
      type: 'category',
      data: ['细菌基因组'],
      axisLabel: {
        fontSize: 13,
        fontWeight: 'bold',
        color: '#2c3e50'
      },
      axisLine: {
        show: false
      },
      axisTick: {
        show: false
      }
    },
    series: [
      // 基因组背景矩形
      {
        type: 'custom',
        renderItem: (params, api) => {
          const categoryIndex = 0
          const start = api.coord([0, categoryIndex])
          const end = api.coord([genomeLength, categoryIndex])
          const height = api.size([0, 1])[1] * 0.3
          
          return {
            type: 'rect',
            shape: {
              x: start[0],
              y: start[1] - height / 2,
              width: end[0] - start[0],
              height: height
            },
            style: {
              fill: 'rgba(220, 240, 255, 0.5)',
              stroke: 'rgba(0, 136, 204, 0.6)',
              lineWidth: 2
            }
          }
        },
        data: [[0, 0]],
        silent: true,
        z: 1
      },
      // 原噬菌体区域
      {
        type: 'custom',
        renderItem: (params, api) => {
          const categoryIndex = 0
          const start = api.coord([api.value(0), categoryIndex])
          const end = api.coord([api.value(1), categoryIndex])
          const height = api.size([0, 1])[1] * 0.25
          
          // 从 prophageData 中获取对应的数据
          const region = prophageData[params.dataIndex]
          const color = region.itemStyle.color
          
          const rectWidth = end[0] - start[0]
          
          // 构建图形组
          const graphics = {
            type: 'group',
            children: [
              // 原噬菌体矩形
              {
                type: 'rect',
                shape: {
                  x: start[0],
                  y: start[1] - height / 2,
                  width: rectWidth,
                  height: height
                },
                style: {
                  fill: color,
                  stroke: '#fff',
                  lineWidth: 2,
                  shadowBlur: 5,
                  shadowColor: 'rgba(0,0,0,0.3)'
                }
              }
            ]
          }
          
          // 如果显示标签，添加位置标注
          if (showLabels.value) {
            // 起始位置标注
            graphics.children.push({
              type: 'text',
              style: {
                text: `${(region.start / 1000).toFixed(0)}K`,
                x: start[0],
                y: start[1] - height / 2 - 10,
                textAlign: 'center',
                fontSize: 10,
                fill: '#2c3e50',
                fontWeight: 'bold'
              }
            })
            
            // 终止位置标注
            graphics.children.push({
              type: 'text',
              style: {
                text: `${(region.end / 1000).toFixed(0)}K`,
                x: end[0],
                y: start[1] - height / 2 - 10,
                textAlign: 'center',
                fontSize: 10,
                fill: '#2c3e50',
                fontWeight: 'bold'
              }
            })
            
            // 区域标签
            graphics.children.push({
              type: 'text',
              style: {
                text: `区域 ${region.regionId}`,
                x: (start[0] + end[0]) / 2,
                y: start[1],
                textAlign: 'center',
                textVerticalAlign: 'middle',
                fontSize: 11,
                fontWeight: 'bold',
                fill: '#ffffff',
                stroke: 'rgba(0, 0, 0, 0.8)',
                lineWidth: 3
              }
            })
          }
          
          return graphics
        },
        encode: {
          x: [0, 1],
          y: 2
        },
        data: prophageData.map(d => [d.value[0], d.value[1], 0]),
        // 移除 hover 效果，保持固定颜色
        emphasis: {
          disabled: true
        },
        z: 2
      }
    ]
  }
  
  overviewChartInstance.setOption(option, true)
}

// 更新详细图
function updateDetailChart() {
  if (!selectedRegionForViewer.value || !selectedRegionForViewer.value.genes) {
    console.log('没有选中区域或基因数据，跳过更新')
    return
  }
  
  if (!geneDetailChart.value) {
    console.error('geneDetailChart ref 不存在！')
    return
  }
  
  // 初始化或重新初始化图表实例
  if (!detailChartInstance) {
    console.log('首次初始化详细图表实例')
    detailChartInstance = echarts.init(geneDetailChart.value)
  }
  
  const genes = selectedRegionForViewer.value.genes
  const regionStart = selectedRegionForViewer.value.start
  const regionEnd = selectedRegionForViewer.value.end
  
  console.log('更新详细图:', {
    regionId: selectedRegionForViewer.value.regionId,
    genes: genes.length,
    start: regionStart,
    end: regionEnd
  })
  
  // 准备基因数据（根据功能分类着色）
  const geneData = genes.map((gene, index) => {
    const isForward = gene.strand === 1
    // 根据基因功能获取颜色
    const color = getGeneColor(gene)
    
    return {
      name: showLabels.value ? (gene.marker || gene.annotationDescription || `Gene ${index + 1}`) : '',
      value: [gene.start, gene.end, isForward ? 0 : 1],
      itemStyle: {
        color: color
      },
      ...gene
    }
  })
  
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
        const gene = geneData[params.dataIndex]
        return `
          <strong style="color: #0088cc;">${gene.gene}</strong><br/>
          位置: ${gene.start?.toLocaleString()} - ${gene.end?.toLocaleString()}<br/>
          长度: ${gene.length} bp<br/>
          链: ${gene.strand === 1 ? '正向 (+)' : '反向 (-)'}<br/>
          标记: ${gene.marker || 'NA'}<br/>
          分类: ${gene.taxname || 'NA'}<br/>
          注释: ${gene.annotationDescription || 'NA'}
        `
      }
    },
    grid: {
      left: 80,
      right: 80,
      top: 40,
      bottom: 60
    },
    xAxis: {
      type: 'value',
      min: regionStart,
      max: regionEnd,
      name: '基因组位置 (bp)',
      nameLocation: 'center',
      nameGap: 45,
      nameTextStyle: {
        color: '#2c3e50',
        fontWeight: 'bold'
      },
      axisLabel: {
        color: '#606266',
        formatter: (value) => (value / 1000).toFixed(1) + 'K',
        rotate: 45
      },
      axisLine: {
        lineStyle: {
          color: 'rgba(0, 136, 204, 0.3)'
        }
      },
      splitLine: {
        lineStyle: {
          color: 'rgba(0, 136, 204, 0.1)'
        }
      }
    },
    yAxis: {
      type: 'category',
      data: ['正向链 (+)', '反向链 (-)'],
      axisLabel: {
        fontSize: 12,
        color: '#2c3e50',
        fontWeight: 'bold'
      },
      axisLine: {
        lineStyle: {
          color: 'rgba(0, 136, 204, 0.3)'
        }
      }
    },
    series: [{
      type: 'custom',
      renderItem: (params, api) => {
        const categoryIndex = api.value(2)
        const start = api.coord([api.value(0), categoryIndex])
        const end = api.coord([api.value(1), categoryIndex])
        const height = api.size([0, 1])[1] * 0.5
        const isForward = categoryIndex === 0
        
        // 从 geneData 中获取对应的基因数据
        const gene = geneData[params.dataIndex]
        const color = gene.itemStyle.color
        
        // 绘制箭头形状
        const arrowWidth = Math.min((end[0] - start[0]) * 0.2, 15)
        const rectWidth = end[0] - start[0] - arrowWidth
        
        const style = {
          fill: color,
          stroke: 'rgba(0, 0, 0, 0.2)',
          lineWidth: 1
        }
        
        return {
          type: 'group',
          children: [
            // 主体矩形
            {
              type: 'rect',
              shape: {
                x: start[0],
                y: start[1] - height / 2,
                width: rectWidth,
                height: height
              },
              style: style
            },
            // 箭头
            {
              type: 'polygon',
              shape: {
                points: isForward ? [
                  [start[0] + rectWidth, start[1] - height / 2],
                  [end[0], start[1]],
                  [start[0] + rectWidth, start[1] + height / 2]
                ] : [
                  [start[0] + rectWidth, start[1] - height / 2],
                  [end[0], start[1]],
                  [start[0] + rectWidth, start[1] + height / 2]
                ]
              },
              style: style
            }
          ]
        }
      },
      encode: {
        x: [0, 1],
        y: 2
      },
      data: geneData.map(d => [d.value[0], d.value[1], d.value[2]]),
      // 移除 hover 效果，保持固定颜色
      emphasis: {
        disabled: true
      }
    }],
    dataZoom: [{
      type: 'slider',
      xAxisIndex: 0,
      start: 0,
      end: 100,
      height: 20,
      bottom: 10
    }]
  }
  
  console.log('设置详细图表配置, 基因数量:', geneData.length)
  // 使用 notMerge: true 确保完全替换配置
  detailChartInstance.setOption(option, { notMerge: true })
  console.log('详细图表已更新')
}

// 更新所有图表
function updateCharts() {
  updateOverviewChart()
  if (selectedRegionForViewer.value) {
    updateDetailChart()
  }
}

// 重置缩放
function resetZoom() {
  if (detailChartInstance) {
    detailChartInstance.dispatchAction({
      type: 'dataZoom',
      start: 0,
      end: 100
    })
  }
}

// 清除选择
function clearSelection() {
  selectedRegionForViewer.value = null
  // 不清除图表实例，只是隐藏，这样再次点击时可以正常显示
}

// 下载基因文件
async function downloadGenesFile() {
  try {
    // 检查是否已加载数据
    if (allProphageDetails.value.length === 0) {
      ElMessage.warning('请先加载数据')
      return
    }
    
    const baseName = `task_${taskId.value}`
    const fileName = `${baseName}_provirus_genes.tsv`
    
    ElMessage.info('正在准备下载...')
    
    // 构建TSV内容（直接从已加载的数据）
    let tsvContent = 'gene\tstart\tend\tlength\tstrand\tgc_content\tgenetic_code\trbs_motif\tmarker\tevalue\tbitscore\tuscg\ttaxid\ttaxname\tannotation_accessions\tannotation_description\n'
    
    allProphageDetails.value.forEach(region => {
      if (region.genes) {
        region.genes.forEach(gene => {
          tsvContent += `${gene.gene || ''}\t${gene.start || ''}\t${gene.end || ''}\t${gene.length || ''}\t${gene.strand || ''}\t${gene.gcContent || ''}\t${gene.geneticCode || ''}\t${gene.rbsMotif || ''}\t${gene.marker || ''}\t${gene.evalue || ''}\t${gene.bitscore || ''}\t${gene.uscg || ''}\t${gene.taxid || ''}\t${gene.taxname || ''}\t${gene.annotationAccessions || ''}\t${gene.annotationDescription || ''}\n`
        })
      }
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

// 下载 Summary 表格
function downloadSummaryTable() {
  try {
    if (prophageRegions.value.length === 0) {
      ElMessage.warning('没有可下载的数据')
      return
    }
    
    const baseName = `task_${taskId.value}`
    const fileName = `${baseName}_summary.tsv`
    
    ElMessage.info('正在准备下载...')
    
    // 构建TSV内容
    let tsvContent = '区域\t序列名称\t起始位置\t终止位置\t长度(bp)\t基因数\t质量评级\t得分\t完整性\t整合酶\n'
    
    prophageRegions.value.forEach((region, index) => {
      const quality = getQualityLabel(region.vVsCScore)
      tsvContent += `${index + 1}\t${region.seqName || ''}\t${region.start || ''}\t${region.end || ''}\t${region.length || ''}\t${region.nGenes || ''}\t${quality}\t${region.vVsCScore || ''}\t${region.completeness || ''}\t${region.integrases || ''}\n`
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

// 下载 Genome Viewer 图表图片
function downloadGenomeViewerImages() {
  try {
    ElMessage.info('正在生成图片...')
    
    // 下载基因组总览图
    if (overviewChartInstance) {
      const overviewUrl = overviewChartInstance.getDataURL({
        type: 'png',
        pixelRatio: 2,
        backgroundColor: 'transparent'
      })
      const a1 = document.createElement('a')
      a1.href = overviewUrl
      a1.download = `task_${taskId.value}_genome_overview.png`
      a1.click()
    }
    
    // 下载详细基因图（如果已选择区域）
    if (selectedRegionForViewer.value && detailChartInstance) {
      setTimeout(() => {
        const detailUrl = detailChartInstance.getDataURL({
          type: 'png',
          pixelRatio: 2,
          backgroundColor: 'transparent'
        })
        const a2 = document.createElement('a')
        a2.href = detailUrl
        a2.download = `task_${taskId.value}_region_${selectedRegionForViewer.value.regionId}_detail.png`
        a2.click()
        
        ElMessage.success('图片下载成功')
      }, 500)
    } else {
      ElMessage.success('总览图下载成功')
      ElMessage.info('提示：选择一个原噬菌体区域后，可同时下载详细基因图')
    }
  } catch (error) {
    console.error('下载失败:', error)
    ElMessage.error('下载失败: ' + error.message)
  }
}

// 导出所有数据
async function handleExport() {
  try {
    await ElMessageBox.confirm('确定要导出所有可视化数据吗？', '确认导出', {
      type: 'info'
    })
    
    const response = await exportVisualizationData(taskId.value)
    
    // 创建下载链接
    const blob = new Blob([JSON.stringify(response.data, null, 2)], { type: 'application/json' })
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `visualization_task_${taskId.value}_${Date.now()}.json`
    a.click()
    window.URL.revokeObjectURL(url)
    
    ElMessage.success('导出成功')
  } catch (error) {
    if (error !== 'cancel') {
      console.error('导出失败:', error)
      ElMessage.error('导出失败: ' + (error.response?.data?.message || error.message))
    }
  }
}

// 根据得分获取质量评级类型
function getQualityType(score) {
  if (score > 35) return 'success'  // 绿色 - 高质量
  if (score > 25) return 'primary'  // 蓝色 - 中等质量
  return 'warning'                  // 橙色 - 低质量
}

// 根据得分获取质量评级标签
function getQualityLabel(score) {
  if (score > 35) return 'Intact'
  if (score > 25) return 'Questionable'
  return 'Incomplete'
}

// 获取区域行的类名（用于设置背景色）
function getRegionRowClassName({ row }) {
  const score = row.vVsCScore
  if (score > 35) return 'region-row-intact'
  if (score > 25) return 'region-row-questionable'
  return 'region-row-incomplete'
}

// 获取基因行的类名（用于设置背景色）
function getGeneRowClassName({ row }) {
  // 根据是否有marker（病毒/噬菌体数据库匹配）来分类
  if (row.marker && row.marker !== 'NA') {
    return 'gene-row-phage'  // 粉红色 - 匹配到噬菌体数据库
  }
  return 'gene-row-bacteria'  // 紫色 - 可能是细菌基因
}

// 根据基因功能获取颜色（七大类分类）
function getGeneColor(gene) {
  // 优先使用 annotation_description，如果为空则使用 marker
  const annotation = (gene.annotationDescription || gene.annotation_description || '').toLowerCase()
  const marker = (gene.marker || '').toLowerCase()
  const text = annotation || marker
  
  // === 1. 特殊标记类（优先级最高）===
  if (gene.virus_hallmark === 1 || gene.virusHallmark === 1 || 
      gene.plasmid_hallmark === 1 || gene.plasmidHallmark === 1 ||
      gene.annotation_amr || gene.annotationAmr ||
      gene.annotation_conjscan || gene.annotationConjscan) {
    return '#D32F2F'  // 红色 - Special Markers
  }
  
  // === 2. 整合与重组类 ===
  if (text.includes('integrase') || text.includes('recombinase') || text.includes('recombination') || 
      text.includes('transposase') || text.includes('transposon')) {
    return '#26C6DA'  // 青色 - Integration & Recombination
  }
  
  // === 3. 包装与组装类 ===
  if (text.includes('terminase') || text.includes('protease') || text.includes('peptidase')) {
    return '#E91E63'  // 粉红色 - Packaging & Assembly
  }
  
  // === 4. 结构蛋白类 ===
  if (text.includes('portal') || text.includes('coat') || text.includes('capsid') || 
      text.includes('tail') || text.includes('fiber') || text.includes('fibre') || 
      text.includes('baseplate') || text.includes('attachment')) {
    return '#5C6BC0'  // 深蓝色 - Structural Proteins
  }
  
  // === 5. 裂解类 ===
  if (text.includes('lys') || text.includes('holin')) {
    return '#FF6B6B'  // 红色 - Lysis
  }
  
  // === 6. 复制与转录类 ===
  if (text.includes('replication') || text.includes('replic') || 
      (text.includes('dna') && text.includes('methylase')) || 
      text.includes('trna') || text.includes('t-rna')) {
    return '#AB47BC'  // 紫色 - Replication & Transcription
  }
  
  // === 7. 其他功能类 ===
  if (text.includes('phage') || text.includes('phage-like') ||
      text.includes('hypothetical') || marker === 'na' || !marker || !text) {
    return '#66BB6A'  // 绿色 - Other Functions
  }
  
  // 未分类
  return '#66BB6A'  // 绿色 - Other Functions
}

// 窗口大小变化时重新渲染图表
  window.addEventListener('resize', () => {
  overviewChartInstance?.resize()
  detailChartInstance?.resize()
})
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

/* 只对没有特殊颜色的行应用 hover 效果 */
:deep(.el-table tr:hover:not(.region-row-intact):not(.region-row-questionable):not(.region-row-incomplete):not(.gene-row-phage):not(.gene-row-bacteria)) {
  background: rgba(0, 180, 255, 0.05) !important;
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

:deep(.el-collapse) {
  background: #ffffff;
  border: 1px solid rgba(0, 180, 255, 0.2);
}

:deep(.el-collapse-item__header) {
  background: linear-gradient(135deg, rgba(0, 180, 255, 0.05) 0%, rgba(0, 200, 255, 0.08) 100%);
  color: #2c3e50;
  border-bottom: 1px solid rgba(0, 180, 255, 0.15);
  font-weight: 500;
}

:deep(.el-collapse-item__content) {
  background: #ffffff;
  color: #2c3e50;
}

h3, h4, h5 {
  color: #0088cc;
  text-shadow: 0 2px 4px rgba(0, 136, 204, 0.15);
  font-weight: 600;
}

.summary-desc, .chart-desc {
  color: #606266;
}

.summary-header-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16px;
}

/* 图表容器背景 */
.chart-container {
  background: linear-gradient(135deg, rgba(0, 180, 255, 0.05) 0%, rgba(0, 200, 255, 0.08) 100%);
  padding: 16px;
  border-radius: 8px;
  border: 2px solid rgba(0, 180, 255, 0.25);
  margin-bottom: 20px;
  box-shadow: 0 2px 12px rgba(0, 180, 255, 0.15);
}

.chart {
  background: linear-gradient(135deg, rgba(0, 180, 255, 0.03) 0%, rgba(0, 200, 255, 0.05) 100%);
  border-radius: 4px;
  border: 1px solid rgba(0, 180, 255, 0.2);
}

.info-header {
  margin-bottom: 20px;
}

.action-buttons {
  margin-top: 16px;
  text-align: right;
}

.tabs-card {
  margin-top: 20px;
}

.summary-content,
.detail-content,
.genome-viewer-content {
  padding: 20px;
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

h4 {
  margin: 16px 0 12px;
  font-size: 16px;
  color: #606266;
}

h5 {
  margin: 12px 0 8px;
  font-size: 14px;
  color: #909399;
  font-weight: 600;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.prophage-detail-section {
  margin-bottom: 32px;
}

.region-header-card {
  margin-bottom: 16px;
}

.region-header-card h4 {
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.region-info-compact {
  margin-top: 8px;
}

.genes-table-wrapper {
  margin-top: 16px;
}

.viewer-controls {
  padding: 16px;
  background: #f5f7fa;
  border-radius: 4px;
  margin-bottom: 20px;
}

.control-label {
  font-size: 14px;
  color: #606266;
  font-weight: 500;
}

.chart-container {
  margin-bottom: 32px;
  padding: 16px;
  background: #fff;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
}

.chart-desc {
  font-size: 13px;
  color: #909399;
  margin: 4px 0 12px;
}

.chart {
  width: 100%;
}
/* Summary表格行颜色 - 根据质量评级，直接作用在单元格上 */
:deep(.region-row-intact > td.el-table__cell) {
  background-color: #d4edda !important;  /* 绿色 - Intact (高质量) */
}

:deep(.region-row-questionable > td.el-table__cell) {
  background-color: #cce5ff !important;  /* 蓝色 - Questionable (中等质量) */
}

:deep(.region-row-incomplete > td.el-table__cell) {
  background-color: #fff3cd !important;  /* 黄色 - Incomplete (低质量) */
}

/* Detail基因表格行颜色 - 根据数据库匹配，同样作用在单元格上 */
:deep(.gene-row-phage > td.el-table__cell) {
  background-color: #f8d7da !important;  /* 粉红色 - 匹配到病毒/噬菌体数据库 */
}

:deep(.gene-row-bacteria > td.el-table__cell) {
  background-color: #e2d5f3 !important;  /* 紫色 - 可能是细菌基因或未匹配 */
}
/* 图例样式 */
.legend-container {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 15px;
}

.legend-item {
  display: flex;
  align-items: center;
  font-size: 13px;
  color: #606266;
}

.legend-box {
  display: inline-block;
  width: 20px;
  height: 14px;
  margin-right: 6px;
  border: 1px solid #dcdfe6;
  border-radius: 2px;
}

.phage-box {
  background-color: #f8d7da;
}

.bacteria-box {
  background-color: #e2d5f3;
}

.intact-box {
  background-color: #d4edda;
}

.questionable-box {
  background-color: #cce5ff;
}

.incomplete-box {
  background-color: #fff3cd;
}

/* 基因颜色图例样式 */
.gene-color-legend {
  margin: 16px 0;
  padding: 12px;
  background: linear-gradient(135deg, rgba(0, 180, 255, 0.05) 0%, rgba(0, 200, 255, 0.08) 100%);
  border-radius: 4px;
  border: 1px solid rgba(0, 180, 255, 0.2);
}

.gene-color-legend h5 {
  margin: 0 0 10px 0;
  font-size: 13px;
  color: #0088cc;
  font-weight: 600;
}

.legend-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 8px;
}

.legend-grid .legend-item {
  display: flex;
  align-items: center;
  font-size: 12px;
  color: #2c3e50;
  font-weight: 500;
}

.legend-grid .color-box {
  display: inline-block;
  width: 16px;
  height: 16px;
  margin-right: 6px;
  border-radius: 2px;
  border: 1px solid rgba(0, 0, 0, 0.2);
}

/* 图例容器样式 */
.legend-container {
  color: #2c3e50;
}

.legend-item {
  color: #2c3e50;
  font-weight: 500;
}

.legend-box {
  display: inline-block;
  width: 16px;
  height: 16px;
  margin-right: 6px;
  border-radius: 2px;
  border: 1px solid rgba(0, 0, 0, 0.2);
}

/* 控制面板样式 */
.viewer-controls {
  background: linear-gradient(135deg, rgba(0, 180, 255, 0.05) 0%, rgba(0, 200, 255, 0.08) 100%);
  padding: 12px;
  border-radius: 8px;
  border: 1px solid rgba(0, 180, 255, 0.2);
  margin-bottom: 16px;
}

.control-label {
  color: #2c3e50;
  font-weight: 500;
}

/* 结果可视化初始页面的空状态样式 - 改为青色主题 */
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
</style>
