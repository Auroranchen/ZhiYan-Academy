import { ref, onMounted, onUnmounted } from 'vue'

/**
 * 移动端下拉刷新 composable
 * @param {Function} onRefresh - 刷新回调，需返回 Promise
 * @param {Object} options - 配置项
 * @param {number} options.threshold - 触发刷新的下拉距离（px），默认 60
 * @param {number} options.maxPull - 最大下拉距离（px），默认 100
 * @returns {{ pullDistance, refreshing, containerRef }}
 */
export function usePullRefresh(onRefresh, options = {}) {
  const { threshold = 40, maxPull = 80 } = options

  const pullDistance = ref(0)
  const refreshing = ref(false)
  const containerRef = ref(null)

  let startY = 0
  let pulling = false

  const isAtTop = () => {
    return window.scrollY <= 0
  }

  const onTouchStart = (e) => {
    if (refreshing.value || !isAtTop()) return
    startY = e.touches[0].clientY
    pulling = true
  }

  const onTouchMove = (e) => {
    if (!pulling || refreshing.value) return
    if (!isAtTop()) {
      pulling = false
      pullDistance.value = 0
      return
    }

    const currentY = e.touches[0].clientY
    const diff = currentY - startY

    if (diff > 0) {
      // 阻尼效果：拉得越远阻力越大
      pullDistance.value = Math.min(diff * 0.4, maxPull)
      if (pullDistance.value > 10) {
        e.preventDefault()
      }
    } else {
      pullDistance.value = 0
    }
  }

  const onTouchEnd = async () => {
    if (!pulling) return
    pulling = false

    if (pullDistance.value >= threshold && !refreshing.value) {
      refreshing.value = true
      pullDistance.value = threshold * 0.6
      try {
        await onRefresh()
      } catch (e) {
        console.error('刷新失败:', e)
      } finally {
        refreshing.value = false
        pullDistance.value = 0
      }
    } else {
      pullDistance.value = 0
    }
  }

  let bindEl = null

  onMounted(() => {
    bindEl = containerRef.value || document
    bindEl.addEventListener('touchstart', onTouchStart, { passive: true })
    bindEl.addEventListener('touchmove', onTouchMove, { passive: false })
    bindEl.addEventListener('touchend', onTouchEnd, { passive: true })
  })

  onUnmounted(() => {
    if (bindEl) {
      bindEl.removeEventListener('touchstart', onTouchStart)
      bindEl.removeEventListener('touchmove', onTouchMove)
      bindEl.removeEventListener('touchend', onTouchEnd)
    }
  })

  return { pullDistance, refreshing, containerRef }
}
