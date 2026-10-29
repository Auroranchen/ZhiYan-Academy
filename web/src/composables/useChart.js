/**
 * ECharts 封装
 *
 * 统一解决三件原先各页面各写一遍的事：实例创建与销毁、窗口缩放重绘、
 * 以及最容易被漏掉的——切换亮暗主题后图表配色不跟随。
 *
 * 配置由工厂函数产出，内部用 watchEffect 追踪其中引用的响应式数据，
 * 因此数据变化和主题切换都会自动重绘，调用方不需要手动 setOption。
 */
import { onBeforeUnmount, onMounted, watchEffect } from 'vue'
import * as echarts from 'echarts'
import { useTheme } from './useTheme'

const readToken = (name) =>
  getComputedStyle(document.documentElement).getPropertyValue(name).trim()

/** 从设计令牌读取图表配色，保证全站图表与界面同源 */
export const chartPalette = () => ({
  series: Array.from({ length: 8 }, (_, i) => readToken(`--zy-chart-${i + 1}`)),
  brand: readToken('--zy-brand-base'),
  text: readToken('--zy-text-secondary'),
  textMuted: readToken('--zy-text-tertiary'),
  axis: readToken('--zy-border-default'),
  split: readToken('--zy-border-subtle'),
  surface: readToken('--zy-bg-surface'),
  success: readToken('--zy-success-base'),
  warning: readToken('--zy-warning-base'),
  danger: readToken('--zy-danger-base')
})

/**
 * @param {import('vue').Ref<HTMLElement | null>} elRef 图表容器
 * @param {(palette: ReturnType<typeof chartPalette>) => object} optionFactory
 * @param {{ on?: Record<string, Function> }} [options] ECharts 事件监听，如 { on: { click } }
 */
export function useChart(elRef, optionFactory, options = {}) {
  const { isDark } = useTheme()

  let instance = null
  let stopEffect = null

  const resize = () => instance?.resize()

  /** 导出图片等场景需要直接操作实例 */
  const getInstance = () => instance

  onMounted(() => {
    if (!elRef.value) return
    instance = echarts.init(elRef.value)
    window.addEventListener('resize', resize)

    Object.entries(options.on || {}).forEach(([event, handler]) => {
      instance.on(event, handler)
    })

    stopEffect = watchEffect(
      () => {
        // 读一下 isDark，让主题切换也能触发重绘
        void isDark.value
        if (!instance) return
        instance.setOption(optionFactory(chartPalette()), true)
      },
      // 主题切换是先改 <html> 的 class 再触发响应式更新，
      // 用 post 保证读取 CSS 变量时拿到的是切换后的值
      { flush: 'post' }
    )
  })

  onBeforeUnmount(() => {
    stopEffect?.()
    window.removeEventListener('resize', resize)
    instance?.dispose()
    instance = null
  })

  return { resize, getInstance }
}
