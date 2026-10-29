/**
 * AI 洞察：班级报告、学生周报、思维导图
 *
 * 生成类接口调用大模型，耗时以十秒计，页面需自行提供加载态。
 */
import http from '@/utils/http'

/* ---------- 教师端班级报告 ---------- */

export const getCachedClassReport = (courseid) =>
  http.get('/ai-insight/class-report/cached', { params: { courseid }, suppressMessage: true })

export const generateClassReport = (courseid) =>
  http.post('/ai-insight/class-report', { courseid })

/* ---------- 学生端学习周报 ---------- */

export const listStudentInsightCourses = () => http.get('/ai-insight/student-courses')

export const getCachedStudentReport = () =>
  http.get('/ai-insight/student-report/cached', { suppressMessage: true })

export const generateStudentReport = (courseid) =>
  http.post('/ai-insight/student-report', { courseid })

/* ---------- 思维导图 ---------- */

export const generateMindMap = (topic) => http.post('/ai-insight/mindmap', { topic })

/** 点击导图节点后的二级讲解，path 是从根节点到该节点的路径 */
export const explainMindMapNode = (topic, node, path) =>
  http.post('/ai-insight/mindmap-detail', { topic, node, path })
