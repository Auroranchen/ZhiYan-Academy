/** 作业查重与 AI 原创性检测 */
import http from '@/utils/http'

export const runPlagiarismCheck = (data) => http.post('/plagiarism/check', data)

/** AI 检测耗时较长，单独放宽超时；错误文案由调用方组织，故静默拦截器提示 */
export const runAiOriginalityDetect = (homeworkid) =>
  http.post('/plagiarism/ai-detect', { homeworkid }, { timeout: 120000, suppressMessage: true })

export const listPlagiarismReports = (homeworkid) => http.get(`/plagiarism/byHomework/${homeworkid}`)
export const getPlagiarismReport = (id) => http.get(`/plagiarism/${id}`)
