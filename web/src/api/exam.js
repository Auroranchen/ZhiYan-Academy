/** 在线考试 */
import http from '@/utils/http'

export const listTeacherExams = (params) => http.get('/exam/teacher/list', { params })
export const listStudentExams = (params) => http.get('/exam/student/list', { params })

/* ---------- 试卷管理 ---------- */

export const createExam = (data) => http.post('/exam/create', data)
export const updateExam = (data) => http.put('/exam/update', data)
export const deleteExam = (examid) => http.delete(`/exam/${examid}`)

/** status：0 草稿、1 已发布、2 已结束 */
export const changeExamStatus = (examid, status) =>
  http.post(`/exam/status/${examid}`, null, { params: { status } })

/* ---------- 作答 ---------- */

/** 没有作答记录属正常情况，不弹错误提示 */
export const getExamRecord = (examid) =>
  http.get(`/exam/record/${examid}`, { suppressMessage: true })

/** 开考或续答；错误文案由页面自行处理 */
export const startExam = (examid) =>
  http.post(`/exam/start/${examid}`, null, { suppressMessage: true })

/** 定时暂存，失败不打扰考生 */
export const saveExamProgress = (examid, data) =>
  http.post(`/exam/save/${examid}`, data, { suppressMessage: true })

export const submitExam = (examid, data) =>
  http.post(`/exam/submit/${examid}`, data, { suppressMessage: true })

/* ---------- 教师评阅 ---------- */

export const getExam = (examid) => http.get(`/exam/${examid}`)
export const getExamResults = (examid) => http.get(`/exam/results/${examid}`)
export const listExamUnsubmitted = (examid) => http.get(`/exam/unsubmitted/${examid}`)

/** 对单道主观题请求 AI 辅助评阅意见 */
export const aiReviewQuestion = (data) => http.post('/exam/ai-review', data)

export const reviewExamRecord = (recordId, data) => http.post(`/exam/review/${recordId}`, data)
