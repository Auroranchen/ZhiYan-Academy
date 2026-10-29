/** 系统监管：审计日志、AI 调用监控、入学申请、管理员账号 */
import http from '@/utils/http'

export const listAuditLogs = (params) => http.get('/audit/list', { params })
export const getAuditOverview = () => http.get('/audit/overview')
export const cleanupAuditLogs = (days = 90) => http.delete('/audit/cleanup', { params: { days } })

export const getAiStats = () => http.get('/monitor/ai-stats')

export const listEnrollApplications = () => http.get('/enroll/list')
export const updateEnrollStatus = (id, status) => http.put(`/enroll/${id}/status`, { status })

/* ---------- 官网入学申请（未登录可访问） ---------- */

/** 上传成绩单等佐证材料；失败不应阻断申请填写，由页面提示 */
export const uploadEnrollFile = (form) => http.post('/enroll/upload', form, { suppressMessage: true })

/** 失败文案由官网页面自行渲染，避免和拦截器的提示重复 */
export const submitEnrollApplication = (data) =>
  http.post('/enroll/apply', data, { suppressMessage: true })

export const changeAdminPassword = (data) => http.put('/admin/password', data)
/** 退出登录；后端清会话失败不应挡住前端登出 */
export const adminLogout = () => http.post('/admin/logout', null, { suppressMessage: true })
