/** 课程 */
import http from '@/utils/http'

export const listCourses = (params) => http.get('/course/list', { params })
export const listAllCourses = () => http.get('/course/all')
/** params 可传 semester 以只取本学期课程 */
export const listCoursesByTeacher = (teacherid, params) =>
  http.get(`/course/byTeacher/${teacherid}`, { params })
export const listCoursesByClass = (classid, params) =>
  http.get(`/course/byClass/${classid}`, { params })
export const getCourse = (courseid) => http.get(`/course/${courseid}`)
/** 学生凭课程码加入课程，班级由后端从会话取，前端传的 classid 仅作兼容 */
export const joinCourseByCode = (coursecode, classid) =>
  http.post('/course/joinByCode', { coursecode, classid })
export const createCourse = (data) => http.post('/course/add', data)
export const updateCourse = (data) => http.put('/course/update', data)
export const deleteCourse = (courseid) => http.delete(`/course/delete/${courseid}`)

export const getCourseEvaluationStats = (courseid) => http.get(`/courseEvaluation/stats/${courseid}`)
export const listEvaluationsByStudent = (studentid) => http.get(`/courseEvaluation/byStudent/${studentid}`)
export const createEvaluation = (data) => http.post('/courseEvaluation/add', data)
export const updateEvaluation = (data) => http.put('/courseEvaluation/update', data)
export const deleteEvaluation = (id) => http.delete(`/courseEvaluation/delete/${id}`)
export const listCourseEvaluations = (courseid, params) =>
  http.get(`/courseEvaluation/byCourse/${courseid}`, { params })
