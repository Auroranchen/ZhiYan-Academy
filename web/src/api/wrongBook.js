/** 错题本 */
import http from '@/utils/http'

export const listWrongQuestions = (params) => http.get('/wrongbook/list', { params })
export const getMasteryProfile = () => http.get('/wrongbook/mastery')
/** 热力图为增强信息，接口不可用时页面照常展示 */
export const getMasteryHeatmap = () => http.get('/wrongbook/heatmap', { suppressMessage: true })

export const collectWrongQuestions = () => http.post('/wrongbook/collect')
export const markMastered = (id, options) => http.post(`/wrongbook/master/${id}`, null, options)
export const markUnmastered = (id) => http.post(`/wrongbook/unmaster/${id}`)
export const removeWrongQuestion = (id) => http.delete(`/wrongbook/${id}`)
