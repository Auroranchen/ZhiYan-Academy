/** 考勤与课堂签到 */
import http from '@/utils/http'

/* ---------- 考勤记录 ---------- */

export const listAttendance = (params) => http.get('/attendance/list', { params })

export const listAttendanceByStudent = (studentid, params) =>
  http.get(`/attendance/byStudent/${studentid}`, { params })

export const batchAddAttendance = (data) => http.post('/attendance/batchAdd', data)
export const updateAttendance = (data) => http.put('/attendance/update', data)
export const deleteAttendance = (id) => http.delete(`/attendance/delete/${id}`)

/* ---------- 课堂签到（学生端） ---------- */

/** 进行中的签到；没有签到时属正常情况，不弹错误提示 */
export const listActiveCheckinSessions = (classid) =>
  http.get('/attend-session/student/active', { params: { classid }, suppressMessage: true })

/** 签到码、一键签到、手势签到走同一个接口，差别只在 code 的来源 */
export const submitCheckin = (code, studentid) =>
  http.post('/attend-session/checkin', { code, studentid }, { suppressMessage: true })

/* ---------- 课堂签到（教师端） ---------- */

/** 轮询调用，静默失败以免反复弹窗 */
export const listCheckinSessions = (teacherid) =>
  http.get('/attend-session/teacher/list', { params: { teacherid }, suppressMessage: true })

export const createCheckinSession = (teacherid, data) =>
  http.post(`/attend-session/create?teacherid=${teacherid}`, data)

export const closeCheckinSession = (id, teacherid) =>
  http.post(`/attend-session/close/${id}?teacherid=${teacherid}`)

/** silent 用于实时名单轮询 */
export const getCheckinSessionDetail = (id, silent = false) =>
  http.get(`/attend-session/detail/${id}`, { suppressMessage: silent })

export const proxyCheckin = (sessionId, studentid, teacherid) =>
  http.post('/attend-session/proxy-checkin', { sessionId, studentid, teacherid })
