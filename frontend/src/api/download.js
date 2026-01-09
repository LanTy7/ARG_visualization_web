import request from '@/utils/request';

/**
 * 获取任务的可下载文件列表
 * @param {number} taskId 
 * @returns {Promise}
 */
export function getDownloadableFiles(taskId) {
  return request({
    url: `/download/files/${taskId}`,
    method: 'get'
  });
}

/**
 * 获取下载 URL
 * @param {string} type - 'arg' | 'merged' | 'prodigal' | 'all'
 * @param {number} taskId
 * @returns {string}
 */
export function getDownloadUrl(type, taskId) {
  return `/api/download/${type}/${taskId}`;
}

/**
 * 下载文件
 * @param {string} type - 'arg' | 'merged' | 'prodigal' | 'all'
 * @param {number} taskId
 * @param {string} token - JWT token
 */
export async function downloadFile(type, taskId, token) {
  const url = `/api/download/${type}/${taskId}`;
  
  try {
    const response = await fetch(url, {
      method: 'GET',
      headers: {
        'Authorization': token
      }
    });
    
    if (!response.ok) {
      throw new Error('下载失败');
    }
    
    // 获取文件名
    const contentDisposition = response.headers.get('Content-Disposition');
    let filename = `task_${taskId}_${type}`;
    if (contentDisposition) {
      const match = contentDisposition.match(/filename="(.+)"/);
      if (match) {
        filename = match[1];
      }
    }
    
    // 下载文件
    const blob = await response.blob();
    const downloadUrl = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = downloadUrl;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.URL.revokeObjectURL(downloadUrl);
    
    return true;
  } catch (error) {
    console.error('下载失败:', error);
    throw error;
  }
}
