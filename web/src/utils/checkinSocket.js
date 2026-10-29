/**
 * 签到实时推送 WebSocket 客户端封装。
 * 连接后端 /ws/checkin?classid=xxx，收到事件后回调。
 * 自带断线自动重连，连接失败时不影响页面（轮询作为兜底）。
 */
export function createCheckinSocket(classid, onMessage) {
  if (!classid) return { close() {} }

  let ws = null
  let closedByUser = false
  let retryTimer = null
  let retryDelay = 2000

  const buildUrl = () => {
    const proto = window.location.protocol === 'https:' ? 'wss' : 'ws'
    return `${proto}://${window.location.host}/ZhiYanAcademy/ws/checkin?classid=${classid}`
  }

  const connect = () => {
    try {
      ws = new WebSocket(buildUrl())
    } catch (e) {
      scheduleRetry()
      return
    }

    ws.onopen = () => {
      retryDelay = 2000
    }

    ws.onmessage = (evt) => {
      try {
        const data = JSON.parse(evt.data)
        onMessage && onMessage(data)
      } catch (e) {
        /* 忽略非法消息 */
      }
    }

    ws.onclose = () => {
      if (!closedByUser) scheduleRetry()
    }

    ws.onerror = () => {
      try { ws && ws.close() } catch (e) { /* ignore */ }
    }
  }

  const scheduleRetry = () => {
    if (closedByUser) return
    clearTimeout(retryTimer)
    retryTimer = setTimeout(connect, retryDelay)
    retryDelay = Math.min(retryDelay * 2, 30000) // 指数退避，上限 30s
  }

  connect()

  return {
    close() {
      closedByUser = true
      clearTimeout(retryTimer)
      try { ws && ws.close() } catch (e) { /* ignore */ }
    }
  }
}
