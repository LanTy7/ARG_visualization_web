import request from '@/utils/request';

/**
 * 上传 MAG 文件夹（多个文件）
 * @param {FormData} formData - 包含 files[], magName, description, autoAnalyze 的表单数据
 * @returns {Promise}
 */
export function uploadMag(formData) {
  return request({
    url: '/mag/upload',
    method: 'post',
    data: formData,
    // 设置较长的超时时间（MAG 文件可能较大）
    timeout: 600000  // 10 分钟
  });
}

/**
 * 为已上传的 MAG 创建分析任务
 * @param {Object} data - { magDirPath, magName, description }
 * @returns {Promise}
 */
export function analyzeMag(data) {
  return request({
    url: '/mag/analyze',
    method: 'post',
    data
  });
}
