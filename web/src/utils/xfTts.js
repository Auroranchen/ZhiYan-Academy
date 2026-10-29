/**
 * 讯飞在线语音合成（TTS）播放器
 *
 * 流程：
 *  1. 向后端 /asr/tts-sign 获取带签名的 wss 地址（密钥不出后端）
 *  2. WebSocket 发送文本（UTF8 Base64），流式接收 MP3 音频分片
 *  3. 拼为 Blob 用 <audio> 播放；同一文本结果缓存，倍速用 playbackRate 调节
 */
import { getSpeechSynthesisSign } from '@/api/speech'

export default class XfTtsPlayer {
  /**
   * @param {Object} opts
   * @param {string} opts.vcn 发音人，默认 xiaoyan（讯飞小燕）
   */
  constructor(opts = {}) {
    this.vcn = opts.vcn || 'xiaoyan'
    this.speed = opts.speed ?? 48
    this.volume = opts.volume ?? 68
    this.pitch = opts.pitch ?? 52
    this.cache = new Map() // text -> mp3 Blob
    this.audio = null
    this._token = 0
  }

  /**
   * 合成并播放一段文本（自动停止上一段）
   * @param {string} text
   * @param {Object} cbs { rate, onStart, onEnd, onError }
   */
  async speak(text, { rate = 1, onStart, onEnd, onError } = {}) {
    this.stop()
    const myToken = ++this._token
    const t = String(text || '').trim()
    if (!t) { onEnd && onEnd(); return }

    let blob
    try {
      blob = this.cache.get(t)
      if (!blob) {
        blob = await this._synth(t)
        if (this.cache.size > 60) this.cache.clear()
        this.cache.set(t, blob)
      }
    } catch (e) {
      if (myToken === this._token) onError && onError(e)
      return
    }
    if (myToken !== this._token) return // 期间已被停止/切换

    const url = URL.createObjectURL(blob)
    const audio = new Audio(url)
    audio.playbackRate = rate
    this.audio = audio
    audio.onplay = () => { if (myToken === this._token) onStart && onStart() }
    audio.onended = () => {
      URL.revokeObjectURL(url)
      if (myToken === this._token) { this.audio = null; onEnd && onEnd() }
    }
    audio.onerror = () => {
      URL.revokeObjectURL(url)
      if (myToken === this._token) { this.audio = null; onError && onError(new Error('音频播放失败')) }
    }
    try {
      await audio.play()
    } catch (e) {
      // 自动播放被浏览器拦截等
      if (myToken === this._token) onError && onError(e)
    }
  }

  stop() {
    this._token++
    if (this.audio) {
      try { this.audio.pause() } catch (e) { /* ignore */ }
      this.audio = null
    }
  }

  // ==================== 内部实现 ====================

  /** 通过讯飞 TTS WebSocket 合成 MP3 Blob */
  async _synth(text) {
    const res = await getSpeechSynthesisSign()
    const { url, appid } = res.data || {}
    if (!url) throw new Error('获取语音合成签名失败')

    return new Promise((resolve, reject) => {
      let settled = false
      const fail = (err) => { if (!settled) { settled = true; reject(err) } }
      const ws = new WebSocket(url)
      const chunks = []

      ws.onopen = () => {
        ws.send(JSON.stringify({
          common: { app_id: appid },
          business: {
            aue: 'lame',  // MP3
            sfl: 1,       // 流式分片返回
            vcn: this.vcn,
            speed: this.speed,
            volume: this.volume,
            pitch: this.pitch,
            tte: 'UTF8'
          },
          data: { status: 2, text: this._utf8B64(text) }
        }))
      }
      ws.onmessage = (e) => {
        let msg
        try { msg = JSON.parse(e.data) } catch (err) { return }
        if (msg.code !== 0) {
          fail(new Error(msg.message || ('语音合成错误 code=' + msg.code)))
          try { ws.close() } catch (err) { /* ignore */ }
          return
        }
        const d = msg.data || {}
        if (d.audio) chunks.push(this._b64Bytes(d.audio))
        if (d.status === 2) {
          settled = true
          try { ws.close() } catch (err) { /* ignore */ }
          resolve(new Blob(chunks, { type: 'audio/mpeg' }))
        }
      }
      ws.onerror = () => fail(new Error('语音合成服务连接失败'))
      ws.onclose = () => fail(new Error('语音合成连接中断'))
      // 超时保护
      setTimeout(() => {
        if (!settled) { fail(new Error('语音合成超时')); try { ws.close() } catch (err) { /* ignore */ } }
      }, 15000)
    })
  }

  _utf8B64(text) {
    return btoa(unescape(encodeURIComponent(text)))
  }

  _b64Bytes(b64) {
    const bin = atob(b64)
    const bytes = new Uint8Array(bin.length)
    for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i)
    return bytes
  }
}
