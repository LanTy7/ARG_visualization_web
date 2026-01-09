<template>
  <div class="upload-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>基因序列上传</span>
        </div>
      </template>
      
      <el-form :model="uploadForm" label-width="120px" class="upload-form">
        <!-- 输入类型选择 -->
        <el-form-item label="输入类型">
          <el-radio-group v-model="inputType" @change="handleInputTypeChange">
            <el-radio-button value="sequence">蛋白质序列</el-radio-button>
            <el-radio-button value="file">FASTA 文件</el-radio-button>
            <el-radio-button value="mag">MAG 文件夹</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <!-- MAG 名称（仅 MAG 模式） -->
        <el-form-item v-if="inputType === 'mag'" label="MAG 名称">
          <el-input
            v-model="uploadForm.magName"
            placeholder="可选，不填则自动生成"
          />
        </el-form-item>
        
        <!-- 文件类型（非 MAG 模式） -->
        <el-form-item v-if="inputType !== 'mag'" label="文件类型">
          <el-select v-model="uploadForm.fileType" placeholder="请选择文件类型" style="width: 100%">
            <el-option label="自动检测" value="auto-detect" />
            <el-option label="FASTA" value="fasta" />
            <el-option label="FAA (蛋白质序列)" value="faa" />
          </el-select>
        </el-form-item>
        
        <el-form-item label="文件描述">
          <el-input
            v-model="uploadForm.description"
            type="textarea"
            :rows="2"
            placeholder="请输入文件描述（可选）"
          />
        </el-form-item>
        
        <el-form-item label="上传内容">
          <div class="upload-content-wrapper">
            <!-- 序列输入模式 -->
            <div v-if="inputType === 'sequence'">
              <el-input
                v-model="uploadForm.textContent"
                type="textarea"
                :rows="8"
                placeholder="在此粘贴或输入蛋白质序列（FASTA格式）"
              />
              <div class="el-upload__tip text-upload-tip">
                文本内容将直接被上传到服务器进行分析
              </div>
            </div>

            <!-- 单文件上传模式 -->
            <div v-else-if="inputType === 'file'">
              <el-upload
                ref="uploadRef"
                :auto-upload="false"
                :limit="1"
                :on-change="handleFileChange"
                :on-exceed="handleExceed"
                drag
                class="upload-demo"
              >
                <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
                <div class="el-upload__text">
                  将文件拖到此处，或<em>点击上传</em>
                </div>
                <template #tip>
                  <div class="el-upload__tip">
                    支持 .fasta / .fa / .faa 格式，文件大小不超过 500MB
                  </div>
                </template>
              </el-upload>
            </div>

            <!-- MAG 文件夹上传模式 -->
            <div v-else-if="inputType === 'mag'">
              <!-- 上传方式选择 -->
              <div class="mag-upload-mode">
                <el-radio-group v-model="magUploadMode" size="small">
                  <el-radio-button value="folder">选择文件夹</el-radio-button>
                  <el-radio-button value="files">选择多个文件</el-radio-button>
                </el-radio-group>
              </div>

              <!-- 文件夹上传区域 -->
              <div 
                class="folder-upload-area"
                @click="triggerFolderInput"
                @dragover.prevent="onDragOver"
                @dragleave.prevent="onDragLeave"
                @drop.prevent="onDropFolder"
                :class="{ 'drag-over': isDragOver }"
              >
                <el-icon class="folder-icon"><FolderOpened /></el-icon>
                <div class="upload-text">
                  <span v-if="magUploadMode === 'folder'">
                    点击选择 MAG 文件夹，或将文件夹拖到此处
                  </span>
                  <span v-else>
                    点击选择多个文件，或将文件拖到此处
                  </span>
                </div>
                <div class="upload-tip">
                  支持 .fa / .fasta / .fna 格式
                </div>
              </div>
              
              <!-- 隐藏的文件夹选择器 -->
              <input
                ref="folderInputRef"
                type="file"
                webkitdirectory
                directory
                multiple
                style="display: none"
                @change="handleFolderSelect"
                accept=".fa,.fasta,.fna"
              />
              
              <!-- 隐藏的多文件选择器 -->
              <input
                ref="filesInputRef"
                type="file"
                multiple
                style="display: none"
                @change="handleFilesSelect"
                accept=".fa,.fasta,.fna"
              />
              
              <!-- MAG 文件列表预览 -->
              <div v-if="magFileList.length > 0" class="mag-file-preview">
                <div class="mag-file-header">
                  <span>
                    <el-icon><FolderOpened /></el-icon>
                    {{ magFolderName || '已选择' }} - {{ magFileList.length }} 个文件
                  </span>
                  <el-button type="danger" link size="small" @click="clearMagFiles">
                    清空
                  </el-button>
                </div>
                <div class="mag-file-list">
                  <el-tag 
                    v-for="(file, index) in magFileList.slice(0, 10)" 
                    :key="index"
                    closable
                    @close="removeMagFile(index)"
                    class="mag-file-tag"
                  >
                    {{ file.name }}
                  </el-tag>
                  <el-tag v-if="magFileList.length > 10" type="info">
                    还有 {{ magFileList.length - 10 }} 个文件...
                  </el-tag>
                </div>
              </div>

              <!-- MAG 说明 -->
              <el-alert
                type="info"
                :closable="false"
                class="mag-info-alert"
              >
                <template #title>
                  <div class="mag-info-content">
                    <strong>MAG 分析说明：</strong>
                    <p>1. 上传包含原始核酸序列文件（.fa/.fasta/.fna）的文件夹</p>
                    <p>2. 系统将使用 Prodigal 进行基因预测，转换为蛋白质序列</p>
                    <p>3. 然后进行抗性基因（ARG）识别和分类</p>
                  </div>
                </template>
              </el-alert>
            </div>
          </div>
        </el-form-item>
        
        <el-form-item>
          <el-button
            type="primary"
            :loading="uploading"
            :disabled="!canUpload"
            @click="handleUpload"
          >
            <el-icon><Upload /></el-icon>
            {{ uploading ? '上传中...' : (inputType === 'mag' ? '上传并分析 MAG' : '开始上传') }}
          </el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
    
    <!-- 上传进度 -->
    <el-card v-if="uploadProgress.show" class="progress-card">
      <template #header>
        <div class="card-header">
          <span>{{ inputType === 'mag' ? 'MAG 分析进度' : '上传进度' }}</span>
        </div>
      </template>
      
      <div class="progress-content">
        <el-progress
          :percentage="uploadProgress.percentage"
          :status="uploadProgress.status"
        />
        <p class="progress-text">{{ uploadProgress.text }}</p>
        <!-- MAG 两阶段进度显示 -->
        <div v-if="inputType === 'mag' && uploadProgress.stage" class="stage-info">
          <el-steps :active="uploadProgress.stage - 1" simple>
            <el-step title="Prodigal 预处理" />
            <el-step title="ARG 分析" />
          </el-steps>
        </div>
      </div>
    </el-card>
    
    <!-- 我的文件列表 -->
    <el-card class="files-card">
      <template #header>
        <div class="card-header">
          <span>我的文件</span>
          <el-button type="primary" link @click="refreshFiles">
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button>
        </div>
      </template>
      
      <el-table :data="files" v-loading="loadingFiles">
        <el-table-column prop="originalFilename" label="文件名" min-width="200" />
        <el-table-column prop="fileType" label="文件类型" width="120" />
        <el-table-column label="文件大小" width="120">
          <template #default="{ row }">
            {{ formatFileSize(row.fileSize) }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="上传时间" width="180">
          <template #default="{ row }">
            {{ formatDate(row.uploadTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleAnalyze(row)">
              分析
            </el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled, Upload, Refresh, FolderOpened } from '@element-plus/icons-vue'
import { uploadGenomeFile, getUserFiles, deleteFile } from '@/api/file'
import { createTask } from '@/api/task'
import { uploadMag } from '@/api/mag'

const router = useRouter()

const uploadRef = ref()
const magUploadRef = ref()
const folderInputRef = ref()
const filesInputRef = ref()
const uploading = ref(false)
const loadingFiles = ref(false)
const files = ref([])
const inputType = ref('sequence')  // 'sequence' | 'file' | 'mag'
const magUploadMode = ref('folder')  // 'folder' | 'files'
const magFileList = ref([])
const magFolderName = ref('')  // 文件夹名称
const isDragOver = ref(false)

const uploadForm = reactive({
  file: null,
  fileType: 'auto-detect',
  description: '',
  textContent: '',
  magName: ''
})

const uploadProgress = reactive({
  show: false,
  percentage: 0,
  status: '',
  text: '',
  stage: 0  // MAG 分析阶段：0=未开始，1=预处理，2=分析
})

const canUpload = computed(() => {
  if (inputType.value === 'sequence') {
    return !!uploadForm.textContent && uploadForm.textContent.trim().length > 0
  } else if (inputType.value === 'file') {
    return !!uploadForm.file
  } else if (inputType.value === 'mag') {
    return magFileList.value.length > 0
  }
  return false
})

// 切换输入类型
const handleInputTypeChange = () => {
  uploadForm.file = null
  uploadForm.textContent = ''
  magFileList.value = []
  magFolderName.value = ''
  uploadRef.value?.clearFiles()
  magUploadRef.value?.clearFiles()
}

// 单文件改变
const handleFileChange = (file) => {
  uploadForm.file = file.raw
}

// 超出文件数量限制
const handleExceed = () => {
  ElMessage.warning('每次只能上传一个文件')
}

// 触发文件夹选择
const triggerFolderInput = () => {
  if (magUploadMode.value === 'folder') {
    folderInputRef.value?.click()
  } else {
    filesInputRef.value?.click()
  }
}

// 处理文件夹选择
const handleFolderSelect = (event) => {
  const files = event.target.files
  if (!files || files.length === 0) return
  
  processSelectedFiles(files, true)
  // 清空 input 以便再次选择同一文件夹
  event.target.value = ''
}

// 处理多文件选择
const handleFilesSelect = (event) => {
  const files = event.target.files
  if (!files || files.length === 0) return
  
  processSelectedFiles(files, false)
  event.target.value = ''
}

// 处理选中的文件
const processSelectedFiles = (files, isFolder) => {
  const validExtensions = ['fa', 'fasta', 'fna']
  const validFiles = []
  let folderName = ''
  
  for (const file of files) {
    // 获取文件扩展名
    const ext = file.name.split('.').pop()?.toLowerCase()
    
    if (validExtensions.includes(ext)) {
      validFiles.push(file)
      
      // 从 webkitRelativePath 获取文件夹名称
      if (isFolder && file.webkitRelativePath && !folderName) {
        const pathParts = file.webkitRelativePath.split('/')
        if (pathParts.length > 1) {
          folderName = pathParts[0]
        }
      }
    }
  }
  
  if (validFiles.length === 0) {
    ElMessage.warning('未找到有效的 FASTA 文件（.fa/.fasta/.fna）')
    return
  }
  
  magFileList.value = validFiles
  magFolderName.value = folderName || ''
  
  // 如果没有设置 MAG 名称，使用文件夹名称
  if (folderName && !uploadForm.magName) {
    uploadForm.magName = folderName
  }
  
  ElMessage.success(`已选择 ${validFiles.length} 个有效文件`)
}

// 拖拽相关
const onDragOver = () => {
  isDragOver.value = true
}

const onDragLeave = () => {
  isDragOver.value = false
}

const onDropFolder = async (event) => {
  isDragOver.value = false
  
  const items = event.dataTransfer.items
  if (!items || items.length === 0) return
  
  const validExtensions = ['fa', 'fasta', 'fna']
  const validFiles = []
  let folderName = ''
  
  // 处理拖入的项目
  for (const item of items) {
    if (item.kind === 'file') {
      const entry = item.webkitGetAsEntry?.()
      
      if (entry) {
        if (entry.isDirectory) {
          // 是文件夹，递归读取
          folderName = entry.name
          const files = await readDirectoryFiles(entry)
          for (const file of files) {
            const ext = file.name.split('.').pop()?.toLowerCase()
            if (validExtensions.includes(ext)) {
              validFiles.push(file)
            }
          }
        } else if (entry.isFile) {
          // 是单个文件
          const file = item.getAsFile()
          if (file) {
            const ext = file.name.split('.').pop()?.toLowerCase()
            if (validExtensions.includes(ext)) {
              validFiles.push(file)
            }
          }
        }
      } else {
        // 兼容不支持 webkitGetAsEntry 的浏览器
        const file = item.getAsFile()
        if (file) {
          const ext = file.name.split('.').pop()?.toLowerCase()
          if (validExtensions.includes(ext)) {
            validFiles.push(file)
          }
        }
      }
    }
  }
  
  if (validFiles.length === 0) {
    ElMessage.warning('未找到有效的 FASTA 文件（.fa/.fasta/.fna）')
    return
  }
  
  magFileList.value = validFiles
  magFolderName.value = folderName
  
  if (folderName && !uploadForm.magName) {
    uploadForm.magName = folderName
  }
  
  ElMessage.success(`已选择 ${validFiles.length} 个有效文件`)
}

// 递归读取文件夹中的文件
const readDirectoryFiles = (directoryEntry) => {
  return new Promise((resolve) => {
    const files = []
    const dirReader = directoryEntry.createReader()
    
    const readEntries = () => {
      dirReader.readEntries(async (entries) => {
        if (entries.length === 0) {
          resolve(files)
          return
        }
        
        for (const entry of entries) {
          if (entry.isFile) {
            const file = await getFileFromEntry(entry)
            if (file) files.push(file)
          } else if (entry.isDirectory) {
            // 递归读取子目录
            const subFiles = await readDirectoryFiles(entry)
            files.push(...subFiles)
          }
        }
        
        // 继续读取（有些浏览器分批返回）
        readEntries()
      })
    }
    
    readEntries()
  })
}

// 从 FileEntry 获取 File 对象
const getFileFromEntry = (fileEntry) => {
  return new Promise((resolve) => {
    fileEntry.file(
      (file) => resolve(file),
      () => resolve(null)
    )
  })
}

// MAG 文件改变（兼容旧逻辑）
const handleMagFileChange = (file, fileList) => {
  // 过滤有效的 FASTA 文件
  const validExtensions = ['fa', 'fasta', 'fna']
  const ext = file.name.split('.').pop()?.toLowerCase()
  
  if (!validExtensions.includes(ext)) {
    ElMessage.warning(`不支持的文件格式: ${file.name}`)
    // 从列表中移除
    const index = fileList.findIndex(f => f.uid === file.uid)
    if (index > -1) {
      fileList.splice(index, 1)
    }
    return
  }
  
  magFileList.value = fileList.map(f => f.raw || f)
}

// MAG 文件移除
const handleMagFileRemove = (file, fileList) => {
  magFileList.value = fileList.map(f => f.raw || f)
}

// 移除单个 MAG 文件
const removeMagFile = (index) => {
  magFileList.value.splice(index, 1)
  magUploadRef.value?.clearFiles()
}

// 清空所有 MAG 文件
const clearMagFiles = () => {
  magFileList.value = []
  magFolderName.value = ''
  magUploadRef.value?.clearFiles()
}

// 上传文件
const handleUpload = async () => {
  if (inputType.value === 'mag') {
    await handleMagUpload()
  } else {
    await handleNormalUpload()
  }
}

// 普通上传（序列/单文件）
const handleNormalUpload = async () => {
  if (inputType.value === 'sequence') {
    if (!uploadForm.textContent || !uploadForm.textContent.trim()) {
      ElMessage.warning('请输入要上传的文本内容')
      return
    }
    // 将文本内容打包成临时文件
    const text = uploadForm.textContent.trim()
    const blob = new Blob([text], { type: 'text/plain;charset=utf-8' })
    const virtualFileName = `pasted_sequence_${Date.now()}.fasta`
    uploadForm.file = new File([blob], virtualFileName, { type: 'text/plain;charset=utf-8' })
  } else if (inputType.value === 'file') {
    if (!uploadForm.file) {
      ElMessage.warning('请选择要上传的文件')
      return
    }
  }
  
  // 检查文件大小
  const maxFileSize = 524288000 // 500MB
  if (uploadForm.file.size > maxFileSize) {
    const fileSizeMB = (uploadForm.file.size / (1024 * 1024)).toFixed(2)
    ElMessage.error(`文件大小 ${fileSizeMB} MB 超过限制（最大 500 MB）`)
    return
  }
  
  try {
    uploading.value = true
    uploadProgress.show = true
    uploadProgress.percentage = 0
    uploadProgress.status = ''
    uploadProgress.text = '正在上传文件...'
    uploadProgress.stage = 0
    
    const formData = new FormData()
    formData.append('file', uploadForm.file)
    formData.append('fileType', uploadForm.fileType)
    if (uploadForm.description) {
      formData.append('description', uploadForm.description)
    }
    
    const res = await uploadGenomeFile(formData)
    
    uploadProgress.percentage = 100
    uploadProgress.status = 'success'
    uploadProgress.text = '上传成功！'
    
    ElMessage.success('文件上传成功')
    handleReset()
    await refreshFiles()
    
    // 询问是否立即分析
    try {
      await ElMessageBox.confirm('文件上传成功，是否立即开始抗性基因分析？', '提示', {
        confirmButtonText: '立即分析',
        cancelButtonText: '稍后分析',
        type: 'success'
      })
      await startAnalysis(res.data.fileId)
    } catch {
      // 用户选择稍后分析
    }
  } catch (error) {
    console.error('上传失败：', error)
    uploadProgress.status = 'exception'
    
    let errorMessage = '上传失败，请重试'
    if (error.response?.data?.message) {
      errorMessage = error.response.data.message
    } else if (error.response?.status === 413) {
      errorMessage = '文件大小超过限制'
    } else if (error.response?.status >= 500) {
      errorMessage = '服务器错误，请稍后重试'
    }
    
    uploadProgress.text = errorMessage
    ElMessage.error(errorMessage)
  } finally {
    uploading.value = false
    setTimeout(() => {
      uploadProgress.show = false
    }, 3000)
  }
}

// MAG 上传
const handleMagUpload = async () => {
  if (magFileList.value.length === 0) {
    ElMessage.warning('请选择要上传的 MAG 文件')
    return
  }
  
  try {
    uploading.value = true
    uploadProgress.show = true
    uploadProgress.percentage = 0
    uploadProgress.status = ''
    uploadProgress.text = '正在上传 MAG 文件...'
    uploadProgress.stage = 1
    
    const formData = new FormData()
    
    // 添加所有文件
    for (const file of magFileList.value) {
      formData.append('files', file)
    }
    
    // 添加其他参数
    if (uploadForm.magName) {
      formData.append('magName', uploadForm.magName)
    }
    if (uploadForm.description) {
      formData.append('description', uploadForm.description)
    }
    formData.append('autoAnalyze', 'true')
    
    uploadProgress.percentage = 20
    uploadProgress.text = '文件上传中...'
    
    const res = await uploadMag(formData)
    
    uploadProgress.percentage = 50
    uploadProgress.text = 'MAG 分析任务已创建，正在处理...'
    uploadProgress.stage = 1
    
    ElMessage.success(`MAG 上传成功，共 ${res.data.fileCount} 个文件`)
    
    // 如果创建了任务，显示任务信息
    if (res.data.task) {
      uploadProgress.percentage = 100
      uploadProgress.status = 'success'
      uploadProgress.text = `分析任务已创建 (ID: ${res.data.task.taskId})`
      
      handleReset()
      
      // 询问是否跳转到历史记录
      try {
        await ElMessageBox.confirm(
          'MAG 分析任务已创建，是否前往历史记录查看进度？',
          '任务已创建',
          {
            confirmButtonText: '查看进度',
            cancelButtonText: '留在此页',
            type: 'success'
          }
        )
        router.push('/history')
      } catch {
        // 用户选择留在此页
      }
    }
    
  } catch (error) {
    console.error('MAG 上传失败：', error)
    uploadProgress.status = 'exception'
    
    let errorMessage = 'MAG 上传失败，请重试'
    if (error.response?.data?.message) {
      errorMessage = error.response.data.message
    }
    
    uploadProgress.text = errorMessage
    ElMessage.error(errorMessage)
  } finally {
    uploading.value = false
    setTimeout(() => {
      if (uploadProgress.status !== 'exception') {
        uploadProgress.show = false
      }
    }, 5000)
  }
}

// 开始分析（直接使用 ARG 类型）
const startAnalysis = async (fileId) => {
  try {
    await createTask({ fileId, analysisType: 'arg' })
    ElMessage.success('抗性基因分析任务已创建')
    router.push('/history')
  } catch (error) {
    console.error('创建任务失败:', error)
    ElMessage.error('创建任务失败')
  }
}

// 重置表单
const handleReset = () => {
  uploadForm.file = null
  uploadForm.fileType = 'auto-detect'
  uploadForm.description = ''
  uploadForm.textContent = ''
  uploadForm.magName = ''
  magFileList.value = []
  magFolderName.value = ''
  magUploadMode.value = 'folder'
  inputType.value = 'sequence'
  uploadRef.value?.clearFiles()
  magUploadRef.value?.clearFiles()
}

// 刷新文件列表
const refreshFiles = async () => {
  try {
    loadingFiles.value = true
    const res = await getUserFiles()
    files.value = res.data
  } catch (error) {
    console.error('获取文件列表失败：', error)
    files.value = []
  } finally {
    loadingFiles.value = false
  }
}

// 分析文件
const handleAnalyze = async (row) => {
  try {
    await ElMessageBox.confirm('确定要对该文件进行抗性基因分析吗？', '确认', {
      confirmButtonText: '开始分析',
      cancelButtonText: '取消',
      type: 'info'
    })
    await startAnalysis(row.fileId)
  } catch {
    // 用户取消
  }
}

// 删除文件
const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定要删除该文件吗？删除后无法恢复。', '警告', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteFile(row.fileId)
    ElMessage.success('文件已删除')
    await refreshFiles()
  } catch {
    // 用户取消或删除失败
  }
}

// 格式化文件大小
const formatFileSize = (bytes) => {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return (bytes / Math.pow(k, i)).toFixed(2) + ' ' + sizes[i]
}

// 格式化日期
const formatDate = (dateString) => {
  if (!dateString) return '-'
  return new Date(dateString).toLocaleString('zh-CN')
}

// 获取状态类型
const getStatusType = (status) => {
  const types = {
    'UPLOADED': 'success',
    'PROCESSING': 'warning',
    'FAILED': 'danger',
    'DELETED': 'info'
  }
  return types[status] || 'info'
}

// 获取状态文本
const getStatusText = (status) => {
  const texts = {
    'UPLOADED': '已上传',
    'PROCESSING': '处理中',
    'FAILED': '失败',
    'DELETED': '已删除'
  }
  return texts[status] || status
}

onMounted(() => {
  refreshFiles()
})
</script>

<style scoped>
.upload-container {
  max-width: 1200px;
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

.upload-form {
  max-width: 800px;
}

.upload-content-wrapper {
  width: 100%;
}

:deep(.el-form-item__label) {
  color: rgba(255, 255, 255, 0.8);
}

:deep(.el-input__wrapper) {
  background: rgba(0, 255, 255, 0.05);
  border: 1px solid rgba(0, 255, 255, 0.2);
  box-shadow: none;
}

:deep(.el-input__wrapper:hover),
:deep(.el-input__wrapper.is-focus) {
  border-color: rgba(0, 255, 255, 0.5);
  box-shadow: 0 0 10px rgba(0, 255, 255, 0.2);
}

:deep(.el-input__inner) {
  color: rgba(255, 255, 255, 0.9);
}

:deep(.el-select__wrapper) {
  background: rgba(0, 255, 255, 0.05);
  border: 1px solid rgba(0, 255, 255, 0.2);
  box-shadow: none;
}

:deep(.el-select__wrapper:hover),
:deep(.el-select__wrapper.is-focused) {
  border-color: rgba(0, 255, 255, 0.5);
  box-shadow: 0 0 10px rgba(0, 255, 255, 0.2);
}

:deep(.el-select__placeholder) {
  color: rgba(255, 255, 255, 0.5);
}

:deep(.el-select__selected-item) {
  color: rgba(255, 255, 255, 0.9);
}

:deep(.el-button--default) {
  background: rgba(0, 255, 255, 0.1);
  border: 1px solid rgba(0, 255, 255, 0.3);
  color: rgba(255, 255, 255, 0.8);
}

:deep(.el-button--default:hover) {
  background: rgba(0, 255, 255, 0.2);
  border-color: rgba(0, 255, 255, 0.5);
  color: #00ffff;
}

:deep(.el-textarea__inner) {
  background: rgba(0, 255, 255, 0.05);
  border: 1px solid rgba(0, 255, 255, 0.2);
  color: rgba(255, 255, 255, 0.9);
}

:deep(.el-textarea__inner:hover),
:deep(.el-textarea__inner:focus) {
  border-color: rgba(0, 255, 255, 0.5);
}

/* Radio Button 样式 */
:deep(.el-radio-button__inner) {
  background: rgba(0, 255, 255, 0.05);
  border-color: rgba(0, 255, 255, 0.3);
  color: rgba(255, 255, 255, 0.8);
}

:deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  background: rgba(0, 255, 255, 0.3);
  border-color: #00ffff;
  color: #00ffff;
  box-shadow: -1px 0 0 0 #00ffff;
}

.upload-demo {
  width: 100%;
}

:deep(.el-upload-dragger) {
  padding: 40px;
  background: rgba(0, 255, 255, 0.05);
  border: 2px dashed rgba(0, 255, 255, 0.3);
}

:deep(.el-upload-dragger:hover) {
  border-color: rgba(0, 255, 255, 0.6);
  background: rgba(0, 255, 255, 0.08);
}

:deep(.el-upload__text) {
  color: rgba(255, 255, 255, 0.7);
}

:deep(.el-upload__text em) {
  color: #00ffff;
}

:deep(.el-upload__tip) {
  color: rgba(255, 255, 255, 0.6);
}

/* MAG 上传样式 */
.mag-upload :deep(.el-upload-dragger) {
  border-color: rgba(103, 194, 58, 0.4);
}

.mag-upload :deep(.el-upload-dragger:hover) {
  border-color: rgba(103, 194, 58, 0.7);
  background: rgba(103, 194, 58, 0.08);
}

/* MAG 上传模式切换 */
.mag-upload-mode {
  margin-bottom: 16px;
}

/* 文件夹上传区域 */
.folder-upload-area {
  padding: 40px 20px;
  background: rgba(0, 255, 255, 0.05);
  border: 2px dashed rgba(103, 194, 58, 0.4);
  border-radius: 8px;
  text-align: center;
  cursor: pointer;
  transition: all 0.3s ease;
}

.folder-upload-area:hover {
  border-color: rgba(103, 194, 58, 0.7);
  background: rgba(103, 194, 58, 0.08);
}

.folder-upload-area.drag-over {
  border-color: #67C23A;
  background: rgba(103, 194, 58, 0.15);
  transform: scale(1.01);
}

.folder-upload-area .folder-icon {
  font-size: 48px;
  color: rgba(103, 194, 58, 0.7);
  margin-bottom: 12px;
}

.folder-upload-area .upload-text {
  color: rgba(255, 255, 255, 0.7);
  font-size: 14px;
  margin-bottom: 8px;
}

.folder-upload-area .upload-tip {
  color: rgba(255, 255, 255, 0.5);
  font-size: 12px;
}

.mag-file-preview {
  margin-top: 16px;
  padding: 12px;
  background: rgba(0, 255, 255, 0.05);
  border-radius: 8px;
  border: 1px solid rgba(0, 255, 255, 0.2);
}

.mag-file-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  color: rgba(255, 255, 255, 0.8);
  font-size: 14px;
}

.mag-file-header .el-icon {
  margin-right: 6px;
  color: #67C23A;
}

.mag-file-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.mag-file-tag {
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.mag-info-alert {
  margin-top: 16px;
}

.mag-info-alert :deep(.el-alert__title) {
  font-size: 13px;
  line-height: 1.6;
}

.mag-info-content p {
  margin: 4px 0;
  font-weight: normal;
}

.progress-card {
  margin-top: 24px;
}

.progress-content {
  padding: 20px;
}

.progress-text {
  margin-top: 16px;
  text-align: center;
  color: rgba(255, 255, 255, 0.8);
}

.stage-info {
  margin-top: 20px;
}

:deep(.el-steps--simple) {
  background: rgba(0, 255, 255, 0.05);
  border-radius: 8px;
  padding: 12px 20px;
}

:deep(.el-step__title) {
  color: rgba(255, 255, 255, 0.7);
  font-size: 13px;
}

:deep(.el-step__title.is-process) {
  color: #00ffff;
  font-weight: 600;
}

:deep(.el-step__title.is-finish) {
  color: #67C23A;
}

.files-card {
  margin-top: 24px;
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

.text-upload-tip {
  color: rgba(255, 255, 255, 0.6);
  font-size: 12px;
  margin-top: 8px;
}
</style>
