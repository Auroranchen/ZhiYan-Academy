/** 学年学期 */
import http from '@/utils/http'

export const listTerms = (params) => http.get('/academic-term/list', { params })
export const getCurrentTerm = () => http.get('/academic-term/current')
/** 下拉选项；部分角色无权访问，调用方自行降级，故静默错误提示 */
export const listTermOptions = () => http.get('/academic-term/options', { suppressMessage: true })
export const createTerm = (data) => http.post('/academic-term', data)
export const updateTerm = (termId, data) => http.put(`/academic-term/${termId}`, data)
export const activateTerm = (termId) => http.post(`/academic-term/${termId}/activate`)
export const archiveTerm = (termId) => http.post(`/academic-term/${termId}/archive`)
export const deleteTerm = (termId) => http.delete(`/academic-term/${termId}`)
