/** 作业与学生作业 */
import http from '@/utils/http'

export const listHomework = (params) => http.get('/homework/list', { params })
export const getHomework = (homeworkid) => http.get(`/homework/${homeworkid}`)
export const createHomework = (data) => http.post('/homework/add', data)
export const updateHomework = (data) => http.put('/homework/update', data)
export const deleteHomework = (homeworkid) => http.delete(`/homework/delete/${homeworkid}`)

export const listHomeworkByCourse = (courseid, params) =>
  http.get(`/homework/byCourse/${courseid}`, { params })
export const listHomeworkByClass = (classid, params) =>
  http.get(`/homework/byClass/${classid}`, { params })
export const listHomeworkByTeacher = (teacherid, params) =>
  http.get(`/homework/byTeacher/${teacherid}`, { params })

export const listStudentHomework = (params) => http.get('/studenthomework/list', { params })
export const listStudentHomeworkByTeacher = (teacherid, params) =>
  http.get(`/studenthomework/byTeacher/${teacherid}`, { params })
export const listStudentHomeworkByStudent = (studentid, params) =>
  http.get(`/studenthomework/byStudent/${studentid}`, { params })
export const getStudentHomework = (id) => http.get(`/studenthomework/${id}`)
export const deleteStudentHomework = (id) => http.delete(`/studenthomework/delete/${id}`)

/** 学生提交作业；返回体可能直接带上 AI 评分结果 */
export const submitStudentHomework = (data) => http.post('/studenthomework/submit', data)

/** AI 评分失败后由学生或教师手动重试 */
export const retryGrade = (id) => http.post(`/studenthomework/retryGrade/${id}`)

export const gradeStudentHomework = (data) => http.put('/studenthomework/grade', data)

/** 打回后学生需要重新提交 */
export const returnStudentHomework = (id) => http.post(`/studenthomework/return/${id}`)

/* ---------- 催交与阅读情况 ---------- */

export const listUnsubmitted = (homeworkid) =>
  http.get(`/studenthomework/unsubmitted/${homeworkid}`)

/** 向未提交学生群发站内消息 */
export const remindUnsubmitted = (data) => http.post('/studenthomework/remindUnsubmitted', data)

export const getHomeworkReadStats = (homeworkid) =>
  http.get(`/noticeRead/homework/stats/${homeworkid}`)

export const listHomeworkReadStatus = (homeworkid) =>
  http.get(`/noticeRead/homework/allStatus/${homeworkid}`)

/* ---------- 附件 ---------- */

/** form 为包含 file 字段的 FormData */
export const uploadAttachment = (form) =>
  http.post('/homework/uploadAttachment', form, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })

/**
 * 抽取 Word / PDF 的纯文本，用于试题识别与作业正文展示。
 * 失败不影响主流程，交由调用方静默处理。
 *
 * @param {string} path 附件相对路径
 * @param {number} [homeworkid] 学生端传入后服务端可复用已抽取的缓存
 */
export const readAttachmentContent = (path, homeworkid) =>
  http.get('/homework/readAttachmentContent', {
    params: { path, homeworkId: homeworkid },
    suppressMessage: true
  })

/** 下载作业说明文档，返回 blob 响应体 */
export const downloadHomeworkDoc = (homeworkid) =>
  http.get(`/homework/download-doc/${homeworkid}`, { responseType: 'blob' })

/* ---------- 作业答疑 ---------- */

export const listHomeworkComments = (homeworkid) =>
  http.get(`/homeworkComment/byHomework/${homeworkid}`)

export const createHomeworkComment = (data) => http.post('/homeworkComment/add', data)
export const deleteHomeworkComment = (id) => http.delete(`/homeworkComment/delete/${id}`)

/** 标记作业已读，失败不影响主流程 */
export const markHomeworkRead = (homeworkid, studentid) =>
  http.post('/noticeRead/homework', { homeworkid, studentid }, { suppressMessage: true })
