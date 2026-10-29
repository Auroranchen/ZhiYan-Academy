/** AI 智能练习 */
import http from '@/utils/http'

/** 有错题记录的课程，附带各课程错题数 */
export const listPracticeCourses = () => http.get('/ai-insight/practice-courses')

export const generateSmartPractice = (count, courseid) =>
  http.post('/ai-insight/smart-practice', { count, courseid })

/** 上报成绩以发放积分，并把答对的错题标记为已掌握 */
export const reportPracticeResult = (data) => http.post('/ai-insight/practice-done', data)
