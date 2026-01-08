<template>
  <div class="upload-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>基因文件上传</span>
        </div>
      </template>
      
      <el-form :model="uploadForm" label-width="120px" class="upload-form">
        <el-form-item label="文件类型">
          <el-select v-model="uploadForm.fileType" placeholder="请选择文件类型" style="width: 100%">
            <el-option label="自动检测" value="auto-detect" />
            <el-option label="FASTA" value="fasta" />
            <el-option label="GenBank" value="genbank" />
            
          </el-select>
        </el-form-item>
        
        <el-form-item label="参考基因组">
          <el-select v-model="uploadForm.reference" placeholder="请选择参考基因组（可选）" style="width: 100%" clearable>
            <el-option label="无" value="" />
            <el-option label="RefSeq" value="refseq" />
            <el-option label="GenBank" value="genbank" />
          </el-select>
        </el-form-item>
        
        <el-form-item label="文件描述">
          <el-input
            v-model="uploadForm.description"
            type="textarea"
            :rows="3"
            placeholder="请输入文件描述（可选）"
          />
        </el-form-item>
        
        <el-form-item label="上传内容">
          <div class="upload-content-wrapper">
            <div class="upload-mode-toggle">
              
              <el-button
                type="primary"
                plain
                size="small"
                @click="toggleUploadMode"
              >
                切换为{{ uploadMode === 'file' ? '文本输入' : '文件上传' }}
              </el-button>
            </div>

            <div v-if="uploadMode === 'file'">
              <el-upload
                ref="uploadRef"
                :auto-upload="false"
                :limit="1"
                :on-change="handleFileChange"
                :on-exceed="handleExceed"
                drag
                class="upload-demo"
              >
                <el-icon class="el-icon--upload"><upload-filled /></el-icon>
                <div class="el-upload__text">
                  将文件拖到此处，或<em>点击上传</em>
                </div>
                <template #tip>
                  <div class="el-upload__tip">
                    支持 文件或文本 格式，文件大小不超过 500MB
                  </div>
                </template>
              </el-upload>
            </div>

            <div v-else>
              <el-input
                v-model="uploadForm.textContent"
                type="textarea"
                :rows="8"
                placeholder="在此粘贴或输入基因序列文本"
              />
              <div class="el-upload__tip text-upload-tip">
                文本内容将直接被上传到服务器处理。
              </div>
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
            {{ uploading ? '上传中...' : '开始上传' }}
          </el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
    
    <!-- 上传进度 -->
    <el-card v-if="uploadProgress.show" class="progress-card">
      <template #header>
        <div class="card-header">
          <span>上传进度</span>
        </div>
      </template>
      
      <div class="progress-content">
        <el-progress
          :percentage="uploadProgress.percentage"
          :status="uploadProgress.status"
        />
        <p class="progress-text">{{ uploadProgress.text }}</p>
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
            <el-button
              type="primary"
              link
              size="small"
              @click="handleAnalyze(row)"
            >
              分析
            </el-button>
            <el-button
              type="danger"
              link
              size="small"
              @click="handleDelete(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 分析类型选择对话框 -->
    <el-dialog
      v-model="analysisDialogVisible"
      title="选择分析类型"
      width="450px"
      class="analysis-dialog"
    >
      <div class="analysis-type-selector">
        <el-radio-group v-model="selectedAnalysisType" class="analysis-radio-group">
          <el-radio value="genomad" size="large" class="analysis-radio-item">
            <div class="analysis-option">
              <span class="analysis-title"> 原噬菌体识别 (Genomad)</span>
            </div>
          </el-radio>
          <el-radio value="arg" size="large" class="analysis-radio-item">
            <div class="analysis-option">
              <span class="analysis-title"> 抗性基因检测 (ARG)</span>
            </div>
          </el-radio>
        </el-radio-group>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="analysisDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="confirmAnalyze">
            开始分析
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { UploadFilled, Upload, Refresh } from '@element-plus/icons-vue';
import { uploadGenomeFile, getUserFiles, deleteFile } from '@/api/file';
import { createTask } from '@/api/task';

const router = useRouter();

const uploadRef = ref();
const uploading = ref(false);
const loadingFiles = ref(false);
const files = ref([]);
const uploadMode = ref('file'); // 'file' | 'text'

const uploadForm = reactive({
  file: null,
  fileType: 'auto-detect',
  reference: '',
  description: '',
  textContent: '',
});

const uploadProgress = reactive({
  show: false,
  percentage: 0,
  status: '',
  text: '',
});

const canUpload = computed(() => {
  if (uploadMode.value === 'file') {
    return !!uploadForm.file;
  }
  return !!uploadForm.textContent && uploadForm.textContent.trim().length > 0;
});

// 切换上传模式
const toggleUploadMode = () => {
  uploadMode.value = uploadMode.value === 'file' ? 'text' : 'file';
  // 切换模式时清空当前已选内容，避免混淆
  uploadForm.file = null;
  uploadForm.textContent = '';
  uploadRef.value?.clearFiles();
};

// 文件改变
const handleFileChange = (file) => {
  uploadForm.file = file.raw;
};

// 超出文件数量限制
const handleExceed = () => {
  ElMessage.warning('每次只能上传一个文件');
};

// 上传文件 / 文本
const handleUpload = async () => {
  if (uploadMode.value === 'file') {
    if (!uploadForm.file) {
      ElMessage.warning('请选择要上传的文件');
      return;
    }
  } else {
    if (!uploadForm.textContent || !uploadForm.textContent.trim()) {
      ElMessage.warning('请输入要上传的文本内容');
      return;
    }

    // 将文本内容打包成一个临时 FASTA 文件，复用原有上传逻辑
    const text = uploadForm.textContent.trim();
    const blob = new Blob([text], { type: 'text/plain;charset=utf-8' });
    const virtualFileName = `pasted_genome_${Date.now()}.fasta`;
    uploadForm.file = new File([blob], virtualFileName, {
      type: 'text/plain;charset=utf-8',
    });
  }
  
  // 检查文件大小（500MB = 524288000 字节）
  const maxFileSize = 524288000; // 500MB
  if (uploadForm.file.size > maxFileSize) {
    const fileSizeMB = (uploadForm.file.size / (1024 * 1024)).toFixed(2);
    const maxSizeMB = (maxFileSize / (1024 * 1024)).toFixed(0);
    ElMessage.error(`文件大小 ${fileSizeMB} MB 超过限制（最大 ${maxSizeMB} MB），无法上传`);
    return;
  }
  
  try {
    uploading.value = true;
    uploadProgress.show = true;
    uploadProgress.percentage = 0;
    uploadProgress.status = '';
    uploadProgress.text = '正在上传文件...';
    
    const formData = new FormData();
    formData.append('file', uploadForm.file);
    formData.append('fileType', uploadForm.fileType);
    
    if (uploadForm.reference) {
      formData.append('reference', uploadForm.reference);
    }
    if (uploadForm.description) {
      formData.append('description', uploadForm.description);
    }
    
    const res = await uploadGenomeFile(formData);
    
    uploadProgress.percentage = 100;
    uploadProgress.status = 'success';
    uploadProgress.text = '上传成功！';
    
    ElMessage.success('文件上传成功');
    
    // 重置表单
    handleReset();
    
    // 刷新文件列表
    await refreshFiles();
    
    // 询问是否立即分析
    try {
      await ElMessageBox.confirm('文件上传成功，是否立即开始分析？', '提示', {
        confirmButtonText: '立即分析',
        cancelButtonText: '稍后分析',
        type: 'success',
      });
      
      // 弹出分析类型选择对话框
      handleImmediateAnalyze(res.data.fileId);
    } catch (error) {
      // 用户选择稍后分析
    }
  } catch (error) {
    console.error('上传失败：', error);
    uploadProgress.status = 'exception';
    
    // 提取错误信息
    let errorMessage = '上传失败，请重试';
    if (error.response) {
      // 从响应中获取错误信息
      const responseData = error.response.data;
      if (responseData && responseData.message) {
        errorMessage = responseData.message;
      } else if (responseData && responseData.msg) {
        errorMessage = responseData.msg;
      } else if (error.response.status === 413) {
        errorMessage = '文件大小超过限制，请选择较小的文件';
      } else if (error.response.status >= 500) {
        errorMessage = '服务器错误，请稍后重试';
      } else if (error.response.status >= 400) {
        errorMessage = '上传失败，请检查文件格式和大小';
      }
    } else if (error.message) {
      errorMessage = error.message;
    }
    
    uploadProgress.text = errorMessage;
    ElMessage.error(errorMessage);
  } finally {
    uploading.value = false;
    setTimeout(() => {
      uploadProgress.show = false;
    }, 3000);
  }
};

// 重置表单
const handleReset = () => {
  uploadForm.file = null;
  uploadForm.fileType = 'auto-detect';
  uploadForm.reference = '';
  uploadForm.description = '';
  uploadForm.textContent = '';
  uploadMode.value = 'file';
  uploadRef.value?.clearFiles();
};

// 刷新文件列表
const refreshFiles = async () => {
  try {
    loadingFiles.value = true;
    const res = await getUserFiles();
    files.value = res.data;
  } catch (error) {
    console.error('获取文件列表失败：', error);
    // 使用模拟数据进行预览
    files.value = [
      {
        fileId: 1,
        originalFilename: 'sample_genome_1.fasta',
        fileType: 'FASTA',
        fileSize: 2048576,
        status: 'UPLOADED',
        uploadTime: '2024-11-13T10:30:00',
      },
      {
        fileId: 2,
        originalFilename: 'ecoli_k12.gb',
        fileType: 'GenBank',
        fileSize: 5242880,
        status: 'UPLOADED',
        uploadTime: '2024-11-12T15:20:00',
      },
      {
        fileId: 3,
        originalFilename: 'bacteriophage_genome.fastq',
        fileType: 'FASTQ',
        fileSize: 10485760,
        status: 'PROCESSING',
        uploadTime: '2024-11-11T09:15:00',
      },
    ];
  } finally {
    loadingFiles.value = false;
  }
};

// 分析文件 - 显示分析类型选择对话框
const analysisDialogVisible = ref(false);
const currentAnalyzeFile = ref(null);
const selectedAnalysisType = ref('genomad');
const pendingUploadFileId = ref(null); // 用于存储上传后待分析的文件ID

const handleAnalyze = (row) => {
  currentAnalyzeFile.value = row;
  pendingUploadFileId.value = null; // 清空，表示这是从文件列表点击的分析
  selectedAnalysisType.value = 'genomad';
  analysisDialogVisible.value = true;
};

// 上传成功后立即分析 - 弹出选择对话框
const handleImmediateAnalyze = (fileId) => {
  currentAnalyzeFile.value = null; // 清空，表示这是上传后的立即分析
  pendingUploadFileId.value = fileId;
  selectedAnalysisType.value = 'genomad';
  analysisDialogVisible.value = true;
};

// 确认分析
const confirmAnalyze = async () => {
  // 获取要分析的文件ID（可能是从文件列表点击的，也可能是上传后立即分析的）
  const fileId = currentAnalyzeFile.value?.fileId || pendingUploadFileId.value;
  
  if (!fileId) return;
  
  try {
    const taskName = selectedAnalysisType.value === 'arg' ? '抗性基因检测' : '原噬菌体识别';
    await createTask({ 
      fileId: fileId, 
      analysisType: selectedAnalysisType.value 
    });
    ElMessage.success(`${taskName}任务已创建`);
    analysisDialogVisible.value = false;
    
    // 清空状态
    currentAnalyzeFile.value = null;
    pendingUploadFileId.value = null;
    
    router.push('/history');
  } catch (error) {
    console.error('创建任务失败:', error);
    ElMessage.error('创建任务失败');
  }
};

// 删除文件
const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定要删除该文件吗？删除后无法恢复。', '警告', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    });
    
    await deleteFile(row.fileId);
    ElMessage.success('文件已删除');
    await refreshFiles();
  } catch (error) {
    // 用户取消或删除失败
  }
};

// 格式化文件大小
const formatFileSize = (bytes) => {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return (bytes / Math.pow(k, i)).toFixed(2) + ' ' + sizes[i];
};

// 格式化日期
const formatDate = (dateString) => {
  if (!dateString) return '-';
  const date = new Date(dateString);
  return date.toLocaleString('zh-CN');
};

// 获取状态类型
const getStatusType = (status) => {
  const types = {
    'UPLOADED': 'success',
    'PROCESSING': 'warning',
    'FAILED': 'danger',
    'DELETED': 'info',
  };
  return types[status] || 'info';
};

// 获取状态文本
const getStatusText = (status) => {
  const texts = {
    'UPLOADED': '已上传',
    'PROCESSING': '处理中',
    'FAILED': '失败',
    'DELETED': '已删除',
  };
  return texts[status] || status;
};

onMounted(() => {
  refreshFiles();
});
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

.upload-mode-toggle {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 8px;
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

:deep(.el-select) {
  --el-select-input-focus-border-color: rgba(0, 255, 255, 0.5);
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

.tip {
  margin-left: 10px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.6);
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

/* 分析类型选择对话框样式 */
.analysis-dialog :deep(.el-dialog) {
  background: linear-gradient(135deg, rgba(10, 25, 47, 0.95) 0%, rgba(17, 34, 64, 0.95) 100%);
  border: 1px solid rgba(0, 255, 255, 0.3);
}

.analysis-dialog :deep(.el-dialog__header) {
  border-bottom: 1px solid rgba(0, 255, 255, 0.2);
}

.analysis-dialog :deep(.el-dialog__title) {
  color: #00ffff;
}

.analysis-type-selector {
  padding: 10px 0;
}

.analysis-radio-group {
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 100%;
}

.analysis-radio-item {
  width: 100%;
  padding: 16px;
  border: 1px solid rgba(0, 255, 255, 0.2);
  border-radius: 8px;
  background: rgba(0, 255, 255, 0.05);
  transition: all 0.3s ease;
  margin-right: 0 !important;
}

.analysis-radio-item:hover {
  border-color: rgba(0, 255, 255, 0.5);
  background: rgba(0, 255, 255, 0.1);
}

.analysis-radio-item.is-checked {
  border-color: #00ffff;
  background: rgba(0, 255, 255, 0.15);
}

.analysis-option {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-left: 8px;
}

.analysis-title {
  font-size: 16px;
  font-weight: 500;
  color: rgb(0, 0, 0);
}

.analysis-desc {
  font-size: 12px;
  color: rgb(0, 0, 0);
}

.analysis-dialog :deep(.el-radio__label) {
  width: 100%;
}

.analysis-dialog :deep(.el-dialog__footer) {
  border-top: 1px solid rgba(0, 255, 255, 0.2);
}
</style>

