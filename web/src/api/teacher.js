/** 教师 */
import http from '@/utils/http'

export const listTeachers = (params) => http.get('/teacher/list', { params })
export const createTeacher = (data) => http.post('/teacher/add', data)
export const updateTeacher = (data) => http.put('/teacher/update', data)
export const deleteTeacher = (teacherid) => http.delete(`/teacher/delete/${teacherid}`)

export const getTeacherInfo = () => http.get('/teacher/info')
export const updateTeacherProfile = (data) => http.put('/teacher/profile', data)
export const changeTeacherPassword = (data) => http.put('/teacher/password', data)

/** 退出登录；后端清会话失败不应挡住前端登出 */
export const teacherLogout = () => http.post('/teacher/logout', null, { suppressMessage: true })
