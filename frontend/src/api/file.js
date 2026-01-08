import request from '@/utils/request';

// 上传基因文件
export function uploadGenomeFile(formData) {
  return request({
    url: '/genome/upload',
    method: 'post',
    data: formData,
    // 不设置 Content-Type，让浏览器自动设置 multipart/form-data 和 boundary
    // transformRequest 和请求拦截器会自动处理 FormData
  });
}

// 获取用户的文件列表
export function getUserFiles() {
  return request({
    url: '/genome/list',
    method: 'get'
  });
}

// 获取文件详情
export function getFileDetail(fileId) {
  return request({
    url: `/genome/${fileId}`,
    method: 'get'
  });
}

// 删除文件
export function deleteFile(fileId) {
  return request({
    url: `/genome/${fileId}`,
    method: 'delete'
  });
}

// 获取文件类型选项
export function getFileTypes() {
  return request({
    url: '/genome/file-types',
    method: 'get'
  });
}

// 获取参考基因组选项
export function getReferences() {
  return request({
    url: '/genome/references',
    method: 'get'
  });
}

