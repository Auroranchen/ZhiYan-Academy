/** 统计报表 */
import http from '@/utils/http'

export const getHomeworkStatistics = () => http.get('/statistics/homework')
export const getTeacherDashboard = (params) => http.get('/statistics/teacherDashboard', { params })
export const getErrorRate = (homeworkid) => http.get('/statistics/errorRate', { params: { homeworkid } })
export const getScoreStatistics = (params) => http.get('/statistics/score', { params })
export const getScoreRangeStatistics = (params) => http.get('/statistics/scoreRange', { params })
export const getDashboardScreen = () => http.get('/dashboard/screen')

/**
 * 导出作业讲评 PPT。form 需包含 homeworkid、style，模板样式还需带上 template 文件。
 * 生成要走一遍大模型，耗时以分钟计。
 */
export const exportHomeworkPpt = (form) =>
  http.post('/statistics/exportHomeworkPPT', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000,
    suppressMessage: true
  })
