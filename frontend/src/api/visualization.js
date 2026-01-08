import request from '@/utils/request'

/**
 * 获取 ARG 可视化数据
 */
export function getGenomeVisualization(taskId) {
  return request({
    url: `/visualization/genome/${taskId}`,
    method: 'get'
  })
}

/**
 * 获取统计数据
 */
export function getStatistics(taskId) {
  return request({
    url: `/visualization/statistics/${taskId}`,
    method: 'get'
  })
}

/**
 * 导出可视化数据
 */
export function exportVisualizationData(taskId) {
  return request({
    url: `/visualization/export/${taskId}`,
    method: 'get',
    responseType: 'blob'
  })
}
