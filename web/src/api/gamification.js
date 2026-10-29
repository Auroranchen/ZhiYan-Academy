/** 积分、徽章与排行榜 */
import http from '@/utils/http'

export const getGamificationProfile = () => http.get('/gamification/profile')

/** scope: 'all' 全校 | 'class' 本班 */
export const getLeaderboard = (scope) => http.get('/gamification/leaderboard', { params: { scope } })

export const dailyCheckin = () => http.post('/gamification/checkin')
