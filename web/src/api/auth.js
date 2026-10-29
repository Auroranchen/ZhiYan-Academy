/**
 * 登录与注册
 *
 * 登录失败要按 code 区分账号锁定、限流与验证失效，文案由页面自行决定，
 * 所以这里统一 suppressMessage，不让拦截器抢先弹一个笼统的提示。
 */
import http from '@/utils/http'

/** captchaToken 由滑动验证通过后下发，一次性有效 */
export const login = (data) => http.post('/auth/login', data, { suppressMessage: true })

export const registerStudent = (data) => http.post('/student/register', data)

/* ---------- 滑动验证 ---------- */

/** 取一张带缺口的验证图；失败时组件自行重试，不弹提示 */
export const getSliderCaptcha = () => http.get('/captcha/slider', { suppressMessage: true })

/** 校验滑块落点，通过后返回一次性 token */
export const verifySliderCaptcha = (captchaId, x) =>
  http.post('/captcha/verify', { captchaId, x }, { suppressMessage: true })
