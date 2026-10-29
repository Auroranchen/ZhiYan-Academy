/**
 * 对话式学习画像
 *
 * 画像由与「小枢」的对话增量构建，评估会把结果动态回写到画像维度上。
 */
import http from '@/utils/http'

export const getLearningProfile = () => http.get('/profile')

/** history 为除当前这条外的全部历史消息，服务端据此保持上下文 */
export const chatWithProfileAssistant = (history, message) =>
  http.post('/profile/chat', { history, message })

export const resetLearningProfile = () => http.post('/profile/reset')

/* ---------- 学习效果评估 ---------- */

export const evaluateLearning = (courseid) => http.post('/profile/evaluate', { courseid })

export const listEvaluations = (courseid) =>
  http.get('/profile/evaluations', { params: { courseid: courseid || undefined } })

export const listPracticeAttempts = (params) =>
  http.get('/profile/practice-attempts', { params })

/** 提交智能体资源包里的练习，评估结果会回写到画像 */
export const evaluateResourcePractice = (resourceId, answers) =>
  http.post('/profile/resource-evaluate', { resourceId, answers })
