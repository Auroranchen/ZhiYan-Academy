/** 班级 */
import http from '@/utils/http'

export const listClasses = (params) => http.get('/class/list', { params })
export const listAllClasses = () => http.get('/class/all')
export const listClassesByMajor = (majorid) => http.get(`/class/byMajor/${majorid}`)
export const createClass = (data) => http.post('/class/add', data)
export const updateClass = (data) => http.put('/class/update', data)
export const deleteClass = (classid) => http.delete(`/class/delete/${classid}`)
