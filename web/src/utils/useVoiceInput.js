/**
 * 语音输入组合式函数（讯飞语音听写为主，浏览器 Web Speech 兜底）
 *
 * 用法：
 *   const input = ref('')
 *   const { speechSupported, recognizing, toggleVoiceInput, stopVoiceInput } = useVoiceInput(input)
 *
 * 识别文本会实时追加写入传入的 ref；组件卸载时自动释放麦克风。
 */
import { ref, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import IatRecorder from '@/utils/iatRecorder'

export function useVoiceInput(inputRef) {
  const SpeechRecognitionImpl = window.SpeechRecognition || window.webkitSpeechRecognition
  const xfyunCapable = !!(navigator.mediaDevices && navigator.mediaDevices.getUserMedia && window.WebSocket)
  const speechSupported = xfyunCapable || !!SpeechRecognitionImpl
  const recognizing = ref(false)

  // 讯飞不可用（未配置/签名失败/连接失败）时自动降级浏览器识别
  let xfyunUsable = xfyunCapable
  let iat = null
  let recognition = null
  // 记录开始语音前已有的文本，识别结果追加在其后
  let textBefore = ''

  // ---- 讯飞 IAT ----
  const startXfyun = async () => {
    iat = new IatRecorder({
      onText: (text) => { inputRef.value = textBefore + text },
      onStop: (finalText) => {
        recognizing.value = false
        if (finalText) inputRef.value = textBefore + finalText
        iat = null
      },
      onError: (err) => {
        recognizing.value = false
        ElMessage.error(err.message || '语音识别出错')
        iat = null
      }
    })
    await iat.start()
    recognizing.value = true
  }

  // ---- 浏览器 Web Speech 兜底 ----
  const initRecognition = () => {
    if (!SpeechRecognitionImpl || recognition) return
    recognition = new SpeechRecognitionImpl()
    recognition.lang = 'zh-CN'
    recognition.continuous = true
    recognition.interimResults = true

    recognition.onresult = (event) => {
      let finalText = ''
      let interimText = ''
      for (let i = 0; i < event.results.length; i++) {
        const r = event.results[i]
        if (r.isFinal) finalText += r[0].transcript
        else interimText += r[0].transcript
      }
      inputRef.value = textBefore + finalText + interimText
    }

    recognition.onerror = (event) => {
      recognizing.value = false
      if (event.error === 'not-allowed' || event.error === 'service-not-allowed') {
        ElMessage.warning('麦克风权限被拒绝，请在浏览器设置中允许访问麦克风')
      } else if (event.error === 'no-speech') {
        ElMessage.info('没有检测到语音，请重试')
      } else if (event.error !== 'aborted') {
        ElMessage.error('语音识别出错：' + event.error)
      }
    }

    recognition.onend = () => { recognizing.value = false }
  }

  const startBrowserAsr = () => {
    if (!SpeechRecognitionImpl) {
      ElMessage.warning('当前浏览器不支持语音识别')
      return
    }
    initRecognition()
    try {
      recognition.start()
      recognizing.value = true
    } catch (e) { /* start() 在已启动时会抛异常，忽略即可 */ }
  }

  const stopVoiceInput = () => {
    if (iat) {
      iat.stop()
      recognizing.value = false
      return
    }
    if (recognition) {
      recognition.stop()
      recognizing.value = false
    }
  }

  const toggleVoiceInput = async () => {
    if (!speechSupported) return
    if (recognizing.value) {
      stopVoiceInput()
      return
    }
    textBefore = inputRef.value ? inputRef.value.trimEnd() + ' ' : ''
    if (xfyunUsable) {
      try {
        await startXfyun()
        return
      } catch (e) {
        iat = null
        const msg = String((e && e.message) || '')
        if (/permission|denied|notallowed|notfound/i.test((e && e.name) || '') || /麦克风/.test(msg)) {
          ElMessage.warning('麦克风权限被拒绝，请在浏览器设置中允许访问麦克风')
          return
        }
        // 签名/连接失败：本次会话降级到浏览器识别
        xfyunUsable = false
        ElMessage.info('讯飞语音服务不可用，已切换为浏览器内置识别')
      }
    }
    startBrowserAsr()
  }

  onUnmounted(() => {
    if (iat) {
      try { iat.abort() } catch (e) { /* ignore */ }
      iat = null
    }
    if (recognition) {
      try { recognition.abort() } catch (e) { /* ignore */ }
      recognition = null
    }
  })

  return { speechSupported, recognizing, toggleVoiceInput, stopVoiceInput }
}
