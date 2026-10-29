/**
 * AI 出题与试题解析
 *
 * 这两个接口都要等大模型返回，耗时以十秒计，因此单独放宽超时，
 * 并交由页面自行提示进度与失败原因。
 */
import http from '@/utils/http'

const AI_TIMEOUT = 120000

/** 从文档纯文本中识别试题；AI 不可用时调用方应回退到本地正则解析 */
export const parseQuestionsByAi = (text) =>
  http.post('/ai/parseQuestions', { text }, { timeout: AI_TIMEOUT, suppressMessage: true })

/** data 需包含 subject、difficulty 与各题型数量 */
export const generateQuestionsByAi = (data) =>
  http.post('/ai/generateQuestions', data, { timeout: AI_TIMEOUT, suppressMessage: true })

/** form 为包含 PPT 文件与各题型数量的 FormData */
export const generateQuestionsFromPpt = (form) =>
  http.post('/ai/generateQuestionsFromPPT', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: AI_TIMEOUT,
    suppressMessage: true
  })

/* ---------- 错题讲解 ---------- */

/** 逐题讲解；首次生成要跑大模型，命中缓存则很快 */
export const getHomeworkExplain = (studentHomeworkId) =>
  http.get(`/ai/explain/${studentHomeworkId}`, { timeout: AI_TIMEOUT })

/** 收藏讲解，便于学生回到错题本复习 */
export const favoriteExplain = (data) => http.post('/ai/favorite', data)

/** 对 AI 内容的点赞/纠错反馈；提示文案由调用方按 code 决定 */
export const sendAiFeedback = (data) => http.post('/ai-feedback', data, { suppressMessage: true })

/** 错题的针对性分析；失败时页面回退到通用文案 */
export const analyzeWrongAnswer = (data) =>
  http.post('/ai/analyzeWrongAnswer', data, { suppressMessage: true })

/**
 * 助手对话。走 fetch 才能流式读取，这里的非流式分支用于降级，
 * config 里可带 signal 以便用户中断。
 */
export const chatWithAssistant = (data, config = {}) => http.post('/ai/chat', data, config)

/**
 * 作业辅导对话：**必须**携带 homeworkId。
 *
 * 与通用助手的区别是安全模型不同——通用接口的策略由客户端传入的内容决定不了，
 * 而这里的加固完全由服务端查库判定（是否已提交）。缺 homeworkId 会被服务端拒绝(400)，
 * 不会静默降级到无加固的通用模式。
 */
export const chatWithHomeworkAssistant = (data, config = {}) =>
  http.post('/ai/homework-chat', data, config)
