/**
 * 讯飞语音听写（IAT）流式识别器
 *
 * 流程：
 *  1. 向后端 /asr/sign 获取带 HMAC-SHA256 签名的 wss 地址（密钥不出后端）
 *  2. getUserMedia 采集麦克风音频，Web Audio 重采样为 16kHz 16bit 单声道 PCM
 *  3. 按讯飞协议每 40ms 发送 1280 字节音频帧（status 0 首帧 / 1 中间帧 / 2 尾帧）
 *  4. 解析返回结果，支持 wpgs 动态修正，实时回调最新文本
 */
import { getSpeechRecognitionSign } from '@/api/speech'

const TARGET_RATE = 16000
const FRAME_SAMPLES = 640 // 640 个 16bit 采样 = 1280 字节 ≈ 40ms
const FRAME_INTERVAL = 40

export default class IatRecorder {
  /**
   * @param {Object} opts
   * @param {Function} opts.onText  识别文本更新回调 (text)
   * @param {Function} opts.onStop  会话结束回调 (finalText)
   * @param {Function} opts.onError 出错回调 (Error)
   */
  constructor(opts = {}) {
    this.onText = opts.onText || (() => {})
    this.onStop = opts.onStop || (() => {})
    this.onError = opts.onError || (() => {})
    this.ws = null
    this.stream = null
    this.audioCtx = null
    this.source = null
    this.processor = null
    this.sendTimer = null
    this.sampleQueue = []
    this.firstFrameSent = false
    this.stopping = false
    this.closed = false
    this.resultMap = {}
    this.appid = ''
  }

  async start() {
    // 1. 后端签名
    const res = await getSpeechRecognitionSign()
    const { url, appid } = res.data || {}
    if (!url) throw new Error('获取语音服务签名失败')
    this.appid = appid

    // 2. 麦克风（先拿权限，失败可尽早提示）
    this.stream = await navigator.mediaDevices.getUserMedia({ audio: true })

    // 3. WebSocket 直连讯飞
    await this._openSocket(url)

    // 4. 音频采集管线
    this._initAudio()
  }

  /** 停止说话：上传尾帧，等服务端返回最终结果后自动关闭 */
  stop() {
    if (this.stopping || this.closed) return
    this.stopping = true
    this._stopMic()
    // 兜底：3 秒内服务端未关闭则强制结束
    setTimeout(() => this._close(), 3000)
  }

  /** 立即中止（不等最终结果） */
  abort() {
    this._close()
  }

  getText() {
    return Object.keys(this.resultMap)
      .map(Number)
      .sort((a, b) => a - b)
      .map(sn => this.resultMap[sn])
      .join('')
  }

  // ==================== 内部实现 ====================

  _openSocket(url) {
    return new Promise((resolve, reject) => {
      let opened = false
      const ws = new WebSocket(url)
      this.ws = ws
      ws.onopen = () => {
        opened = true
        this._startSendLoop()
        resolve()
      }
      ws.onerror = () => {
        if (!opened) {
          reject(new Error('语音服务连接失败'))
        } else if (!this.closed) {
          this.onError(new Error('语音服务连接中断'))
        }
        this._close()
      }
      ws.onmessage = e => this._handleMessage(e)
      ws.onclose = () => {
        if (!this.closed) {
          this._cleanup()
          this.onStop(this.getText())
        }
      }
    })
  }

  _initAudio() {
    const AC = window.AudioContext || window.webkitAudioContext
    this.audioCtx = new AC()
    this.source = this.audioCtx.createMediaStreamSource(this.stream)
    // ScriptProcessor 兼容性最好（AudioWorklet 需要 HTTPS+独立文件）
    this.processor = this.audioCtx.createScriptProcessor(4096, 1, 1)
    this.processor.onaudioprocess = e => {
      if (this.stopping || this.closed) return
      const input = e.inputBuffer.getChannelData(0)
      const down = this._downsample(input, this.audioCtx.sampleRate)
      for (let i = 0; i < down.length; i++) {
        const s = Math.max(-1, Math.min(1, down[i]))
        this.sampleQueue.push(s < 0 ? s * 0x8000 : s * 0x7fff)
      }
    }
    this.source.connect(this.processor)
    this.processor.connect(this.audioCtx.destination)
  }

  _downsample(buffer, fromRate) {
    if (fromRate === TARGET_RATE) return buffer
    const ratio = fromRate / TARGET_RATE
    const newLen = Math.floor(buffer.length / ratio)
    const result = new Float32Array(newLen)
    for (let i = 0; i < newLen; i++) {
      result[i] = buffer[Math.floor(i * ratio)]
    }
    return result
  }

  _startSendLoop() {
    this.sendTimer = setInterval(() => {
      if (!this.ws || this.ws.readyState !== WebSocket.OPEN) return
      const samples = this.sampleQueue.splice(0, FRAME_SAMPLES)
      const audio = this._toBase64(samples)
      const dataBlock = { status: 1, format: 'audio/L16;rate=16000', encoding: 'raw', audio }

      if (!this.firstFrameSent) {
        this.firstFrameSent = true
        dataBlock.status = 0
        this.ws.send(JSON.stringify({
          common: { app_id: this.appid },
          business: {
            language: 'zh_cn',
            domain: 'iat',
            accent: 'mandarin',
            vad_eos: 5000,
            dwa: 'wpgs', // 动态修正，边说边出字
            ptt: 1       // 加标点
          },
          data: dataBlock
        }))
        return
      }
      if (this.stopping && this.sampleQueue.length === 0 && !samples.length) {
        dataBlock.status = 2
        this.ws.send(JSON.stringify({ data: dataBlock }))
        clearInterval(this.sendTimer)
        this.sendTimer = null
        return
      }
      this.ws.send(JSON.stringify({ data: dataBlock }))
    }, FRAME_INTERVAL)
  }

  _toBase64(samples) {
    if (!samples.length) return ''
    const int16 = new Int16Array(samples)
    const bytes = new Uint8Array(int16.buffer)
    let binary = ''
    const CHUNK = 0x8000
    for (let i = 0; i < bytes.length; i += CHUNK) {
      binary += String.fromCharCode.apply(null, bytes.subarray(i, i + CHUNK))
    }
    return btoa(binary)
  }

  _handleMessage(e) {
    let msg
    try { msg = JSON.parse(e.data) } catch (err) { return }
    if (msg.code !== 0) {
      this.onError(new Error(msg.message || ('语音识别错误 code=' + msg.code)))
      this._close()
      return
    }
    const result = msg.data && msg.data.result
    if (result) {
      const text = (result.ws || [])
        .map(w => (w.cw || []).map(c => c.w).join(''))
        .join('')
      // wpgs 动态修正：rpl 表示替换之前 rg 区间的结果
      if (result.pgs === 'rpl' && Array.isArray(result.rg)) {
        for (let sn = result.rg[0]; sn <= result.rg[1]; sn++) delete this.resultMap[sn]
      }
      this.resultMap[result.sn] = text
      this.onText(this.getText())
    }
    if (msg.data && msg.data.status === 2) {
      const finalText = this.getText()
      this._close()
      this.onStop(finalText)
    }
  }

  _stopMic() {
    try { if (this.processor) { this.processor.disconnect(); this.processor.onaudioprocess = null } } catch (e) { /* ignore */ }
    try { if (this.source) this.source.disconnect() } catch (e) { /* ignore */ }
    try { if (this.audioCtx && this.audioCtx.state !== 'closed') this.audioCtx.close() } catch (e) { /* ignore */ }
    try { if (this.stream) this.stream.getTracks().forEach(t => t.stop()) } catch (e) { /* ignore */ }
    this.processor = null
    this.source = null
    this.audioCtx = null
    this.stream = null
  }

  _close() {
    if (this.closed) return
    this.closed = true
    if (this.sendTimer) { clearInterval(this.sendTimer); this.sendTimer = null }
    this._stopMic()
    try {
      if (this.ws && this.ws.readyState <= WebSocket.OPEN) this.ws.close()
    } catch (e) { /* ignore */ }
    this.ws = null
  }

  _cleanup() {
    this.closed = true
    if (this.sendTimer) { clearInterval(this.sendTimer); this.sendTimer = null }
    this._stopMic()
    this.ws = null
  }
}
