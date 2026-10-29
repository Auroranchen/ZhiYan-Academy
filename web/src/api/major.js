/** 专业 */
import http from '@/utils/http'

export const listMajors = (params) => http.get('/major/list', { params })
export const listAllMajors = () => http.get('/major/all')
export const createMajor = (data) => http.post('/major/add', data)
export const updateMajor = (data) => http.put('/major/update', data)
export const deleteMajor = (majorid) => http.delete(`/major/delete/${majorid}`)
