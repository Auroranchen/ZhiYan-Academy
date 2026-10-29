/**
 * 智慧作业收集系统 - 前端入口文件 */
import { createApp } from 'vue'
import App from './App.vue'
import { sanitizeHtml } from '@/utils/safeHtml'
import router from './router'
import store from './store'

import { addAPIProvider } from '@iconify/vue'

addAPIProvider('', {
  resources: ['/api-iconify'],
  rotate: 750,
  timeout: 5000
})

// Element Plus
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
// 顺序不可调整：tokens 必须晚于 Element Plus 的变量加载才能覆盖它们，
// design-system 是旧变量兼容层，依赖 tokens 已定义。
import './assets/styles/tokens.css'
import './assets/styles/design-system.css'
import './assets/styles/animations.css'
import './assets/styles/noise-texture.css'
import './assets/styles/modern-global.css'
import './assets/styles/responsive.css'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import { initTheme } from '@/composables/useTheme'

// 首屏渲染前套用主题，避免亮暗切换时闪一下
initTheme()

// 创建Vue应用实例
const app = createApp(App)
app.directive('safe-html', {
  beforeMount(el, binding) { el.innerHTML = sanitizeHtml(binding.value) },
  updated(el, binding) { el.innerHTML = sanitizeHtml(binding.value) }
})

// 使用插件
app.use(store)
app.use(router)
app.use(ElementPlus, {
  locale: zhCn // 设置中文语言
})

const setFavicon = (href) => {
  const link = document.querySelector('link[rel="icon"]') || document.createElement('link')
  link.setAttribute('rel', 'icon')
  link.setAttribute('type', 'image/png')
  link.setAttribute('href', href)
  document.head.appendChild(link)
}
setFavicon(process.env.BASE_URL + 'favicon.png')

window.addEventListener('error', (e) => {
  const msg = e && e.message ? e.message : ''
  const name = e && e.error && e.error.name ? e.error.name : ''
  if (msg.includes('ResizeObserver loop completed with undelivered notifications') || name === 'ResizeObserverLoopError') {
    if (e.preventDefault) e.preventDefault()
    return
  }
})
window.addEventListener('unhandledrejection', (e) => {
  const reason = e && e.reason ? e.reason : ''
  const msg = typeof reason === 'string' ? reason : (reason && reason.message ? reason.message : '')
  if (String(msg).includes('ResizeObserver loop completed with undelivered notifications')) {
    if (e.preventDefault) e.preventDefault()
    return
  }
})

// 挂载应用
app.mount('#app')
