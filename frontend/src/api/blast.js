import request from '@/utils/request';

/**
 * 对单个序列进行 BLAST 比对
 * @param {number} taskId - 任务ID
 * @param {string} sequenceId - 序列ID
 * @returns {Promise}
 */
export function blastSingleSequence(taskId, sequenceId) {
  return request({
    url: '/blast/single',
    method: 'post',
    data: {
      taskId,
      sequenceId
    }
  });
}

/**
 * 获取序列内容（调试用）
 * @param {number} taskId - 任务ID
 * @param {string} sequenceId - 序列ID
 * @returns {Promise}
 */
export function getSequence(taskId, sequenceId) {
  return request({
    url: `/blast/sequence/${taskId}/${encodeURIComponent(sequenceId)}`,
    method: 'get'
  });
}
