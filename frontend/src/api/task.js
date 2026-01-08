import request from '@/utils/request';

// 创建分析任务
export function createTask(data) {
  return request({
    url: '/analysis/create',
    method: 'post',
    data
  });
}

// 获取任务状态
export function getTaskStatus(taskId) {
  return request({
    url: `/analysis/${taskId}/status`,
    method: 'get'
  });
}

// 获取任务结果
export function getTaskResult(taskId) {
  return request({
    url: `/analysis/${taskId}/result`,
    method: 'get'
  });
}

// 获取用户的所有任务
export function getUserTasks(keyword) {
  return request({
    url: '/analysis/list',
    method: 'get',
    params: keyword ? { keyword } : {}
  });
}

// 取消任务
export function cancelTask(taskId) {
  return request({
    url: `/analysis/${taskId}/cancel`,
    method: 'post'
  });
}

// 删除任务
export function deleteTask(taskId) {
  return request({
    url: `/analysis/${taskId}`,
    method: 'delete'
  });
}

