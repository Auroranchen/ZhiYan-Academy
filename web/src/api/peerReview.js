/** 作业互评 */
import http from '@/utils/http'

export const getPeerTask = (homeworkid) => http.get(`/peer/task/${homeworkid}`)
export const getPeerSummary = (homeworkid) => http.get(`/peer/summary/${homeworkid}`)
export const assignPeerReview = (data) => http.post('/peer/assign', data)
/** 把同伴均分回填为作业成绩 */
export const applyPeerScores = (homeworkid) => http.post(`/peer/apply/${homeworkid}`)
export const closePeerReview = (homeworkid) => http.post(`/peer/close/${homeworkid}`)

/* ---------- 学生端 ---------- */

export const listMyPeerTasks = () => http.get('/peer/mine')

/** 顶栏红点用；未登录或非学生时静默失败即可 */
export const listPeerTodo = () => http.get('/peer/todo', { suppressMessage: true })
export const listReceivedPeerReviews = () => http.get('/peer/received')
export const getPeerReviewDetail = (id) => http.get(`/peer/detail/${id}`)
export const submitPeerReview = (data) => http.post('/peer/submit', data)
