/** 课程资源 */
import http from '@/utils/http'

export const listResourcesByTeacher = (teacherid, params) =>
  http.get(`/courseResource/byTeacher/${teacherid}`, { params })

export const listResourcesByCourse = (courseid, params) =>
  http.get(`/courseResource/byCourse/${courseid}`, { params })

export const uploadResource = (formData) =>
  http.post('/courseResource/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })

export const updateResource = (data) => http.put('/courseResource/update', data)
export const deleteResource = (id) => http.delete(`/courseResource/delete/${id}`)

export const resourceDownloadUrl = (id) => `/ZhiYanAcademy/courseResource/download/${id}`
