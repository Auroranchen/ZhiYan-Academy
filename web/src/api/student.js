/** 学生 */
import http from '@/utils/http'

export const listStudents = (params) => http.get('/student/list', { params })
export const createStudent = (data) => http.post('/student/add', data)
export const updateStudent = (data) => http.put('/student/update', data)
export const deleteStudent = (studentid) => http.delete(`/student/delete/${studentid}`)
export const getStudentInfo = () => http.get('/student/info')
export const updateStudentProfile = (data) => http.put('/student/profile', data)
export const changeStudentPassword = (data) => http.put('/student/password', data)

/** 退出登录；后端清会话失败不应挡住前端登出 */
export const studentLogout = () => http.post('/student/logout', null, { suppressMessage: true })

/** 微信绑定：绑定与登录在小程序侧完成，Web 端只查状态与解绑 */
export const getWechatBindStatus = () => http.get('/wechat/auth/status')
export const unbindWechat = (password) => http.post('/wechat/auth/unbind', { password })
