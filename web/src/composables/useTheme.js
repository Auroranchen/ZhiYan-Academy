/**
 * 主题与显示密度
 *
 * 模块级单例：所有组件共享同一份状态，任意位置切换都会立即全站生效。
 * 外观状态写在 <html> 上（class="dark" 与 data-density），CSS 令牌据此切换，
 * 因此不需要任何组件重新渲染。
 */
import { computed, ref } from 'vue'

const THEME_KEY = 'zy-theme'
const DENSITY_KEY = 'zy-density'

/** @typedef {'light' | 'dark' | 'auto'} ThemeMode */
/** @typedef {'comfortable' | 'compact'} Density */

const THEME_MODES = ['light', 'dark', 'auto']
const DENSITIES = ['comfortable', 'compact']

const readStored = (key, allowed, fallback) => {
  try {
    const value = localStorage.getItem(key)
    return allowed.includes(value) ? value : fallback
  } catch {
    return fallback
  }
}

const persist = (key, value) => {
  try {
    localStorage.setItem(key, value)
  } catch {
    // 隐私模式下 localStorage 不可写，仅影响持久化，不影响当前会话
  }
}

const mode = ref(readStored(THEME_KEY, THEME_MODES, 'light'))
const density = ref(readStored(DENSITY_KEY, DENSITIES, 'comfortable'))

const media = typeof window !== 'undefined' && window.matchMedia
  ? window.matchMedia('(prefers-color-scheme: dark)')
  : null

const systemPrefersDark = ref(media ? media.matches : false)

const isDark = computed(() =>
  mode.value === 'auto' ? systemPrefersDark.value : mode.value === 'dark'
)

const applyToDocument = () => {
  const root = document.documentElement
  root.classList.toggle('dark', isDark.value)
  root.dataset.density = density.value
  root.style.colorScheme = isDark.value ? 'dark' : 'light'
}

if (media) {
  const onChange = (e) => {
    systemPrefersDark.value = e.matches
    if (mode.value === 'auto') applyToDocument()
  }
  // Safari 14 以下只支持 addListener
  if (media.addEventListener) media.addEventListener('change', onChange)
  else if (media.addListener) media.addListener(onChange)
}

/** 在 app.mount 之前调用，避免首屏闪烁 */
export const initTheme = () => {
  applyToDocument()
}

export const useTheme = () => {
  /** @param {ThemeMode} next */
  const setMode = (next) => {
    if (!THEME_MODES.includes(next)) return
    mode.value = next
    persist(THEME_KEY, next)
    applyToDocument()
  }

  /** 在亮色与暗色之间切换；当前为跟随系统时，切到与系统相反的那一侧 */
  const toggleTheme = () => {
    setMode(isDark.value ? 'light' : 'dark')
  }

  /** @param {Density} next */
  const setDensity = (next) => {
    if (!DENSITIES.includes(next)) return
    density.value = next
    persist(DENSITY_KEY, next)
    applyToDocument()
  }

  const toggleDensity = () => {
    setDensity(density.value === 'compact' ? 'comfortable' : 'compact')
  }

  return {
    mode: computed(() => mode.value),
    density: computed(() => density.value),
    isDark,
    setMode,
    toggleTheme,
    setDensity,
    toggleDensity
  }
}
