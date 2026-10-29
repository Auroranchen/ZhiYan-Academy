/**
 * 讯飞语音服务签名
 *
 * 鉴权参数不能下发到浏览器，前端只拿后端签好的一次性 WebSocket 地址。
 */
import http from '@/utils/http'

/** 语音识别（听写）连接签名 */
export const getSpeechRecognitionSign = () => http.get('/asr/sign')

/** 语音合成连接签名 */
export const getSpeechSynthesisSign = () => http.get('/asr/tts-sign')
