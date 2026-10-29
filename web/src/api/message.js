/** 站内消息 */
import http from '@/utils/http'

export const listReceivedMessages = (params) => http.get('/message/received', { params })
export const listSentMessages = (params) => http.get('/message/sent', { params })
export const markMessageRead = (id) => http.put(`/message/read/${id}`)
export const markAllMessagesRead = (data) => http.put('/message/readAll', data)
export const sendMessage = (data) => http.post('/message/send', data)
export const sendMessageToClass = (data) => http.post('/message/sendToClass', data)
export const deleteMessage = (id) => http.delete(`/message/delete/${id}`)

/** 顶栏红点用；未登录或无权限时静默失败即可 */
export const getUnreadCount = (receivertype, receiverid) =>
  http.get('/message/unreadCount', {
    params: { receivertype, receiverid },
    suppressMessage: true
  })
