import request from '@/utils/request';

/**
 * 获取基因组可视化数据
 * @param {number} taskId - 任务ID
 * @returns {Promise} - 包含基因组信息和原噬菌体区域列表
 */
export function getGenomeVisualization(taskId) {
  return request({
    url: `/visualization/genome/${taskId}`,
    method: 'get'
  });
}

/**
 * 获取原噬菌体详情
 * @param {number} taskId - 任务ID
 * @param {number} regionId - 区域ID
 * @returns {Promise} - 包含原噬菌体详细信息和基因列表
 */
export function getProphageDetail(taskId, regionId) {
  return request({
    url: `/visualization/prophage/${taskId}/${regionId}`,
    method: 'get'
  });
}

/**
 * 获取统计数据
 * @param {number} taskId - 任务ID
 * @returns {Promise} - 统计信息（图表用）
 */
export function getStatistics(taskId) {
  return request({
    url: `/visualization/statistics/${taskId}`,
    method: 'get'
  });
}

/**
 * 导出可视化数据
 * @param {number} taskId - 任务ID
 * @returns {Promise} - JSON格式的完整数据
 */
export function exportVisualizationData(taskId) {
  return request({
    url: `/visualization/export/${taskId}`,
    method: 'get',
    responseType: 'blob'
  });
}

