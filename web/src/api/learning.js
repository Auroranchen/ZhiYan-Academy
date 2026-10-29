/**
 * 学习画像、学习路径与智能体资源库
 *
 * 这几个接口用于首页的学习建议，任一失败都只应降级而不打断首屏，
 * 因此统一静默错误提示，由调用方决定如何回退。
 */
import http from '@/utils/http'

export const getLearningProfile = () => http.get('/profile', { suppressMessage: true })
export const listLearningPaths = () => http.get('/learnpath/list', { suppressMessage: true })
export const listAgentLibrary = () => http.get('/agent/library', { suppressMessage: true })

/* ---------- 学习路径管理 ---------- */

/** 生成耗时可达数十秒，页面自行提示进度，这里不弹默认错误 */
export const generateLearningPath = () =>
  http.post('/learnpath/generate', null, { suppressMessage: true })

export const archiveLearningPath = (id) => http.post(`/learnpath/archive/${id}`)
export const deleteLearningPath = (id) => http.delete(`/learnpath/${id}`)

/** 任务勾选进度，前端做了防抖，失败不打扰用户 */
export const saveLearningPathProgress = (id, progress) =>
  http.post(`/learnpath/progress/${id}`, { progress }, { suppressMessage: true })

/** AI 讲解收藏 */
export const listAiFavorites = () => http.get('/ai/favorites')
