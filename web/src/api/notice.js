/** 通知公告 */
import http from '@/utils/http'

export const listNotices = (params) => http.get('/notice/list', { params })
export const getNotice = (noticeid) => http.get(`/notice/${noticeid}`)
export const createNotice = (data) => http.post('/notice/add', data)
export const updateNotice = (data) => http.put('/notice/update', data)
export const deleteNotice = (noticeid) => http.delete(`/notice/delete/${noticeid}`)

export const getNoticeReadStats = (noticeid) => http.get(`/noticeRead/notice/stats/${noticeid}`)
export const getNoticeUnreadList = (noticeid) => http.get(`/noticeRead/notice/unread/${noticeid}`)
/** role 取 'teacher' 或 'student'；已读回执失败不应打断阅读，故静默 */
export const markNoticeRead = (role, data) =>
  http.post(`/noticeRead/notice/${role}`, data, { suppressMessage: true })
