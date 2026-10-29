<template>
  <div class="slider-captcha" :style="{ width: width + 'px' }">
    <!-- 拼图区 -->
    <div class="sc-canvas" :style="{ width: width + 'px', height: height + 'px' }">
      <img v-if="bgImage" :src="bgImage" class="sc-bg" :width="width" :height="height" draggable="false" />
      <img
        v-if="blockImage"
        :src="blockImage"
        class="sc-block"
        :style="{ top: blockY + 'px', left: blockLeft + 'px', width: pieceSize + 'px', height: pieceSize + 'px' }"
        draggable="false"
      />
      <div v-if="loading" class="sc-loading">加载中...</div>
      <div class="sc-refresh" title="刷新验证" @click="load">⟳</div>
      <transition name="sc-fade">
        <div v-if="status === 'success'" class="sc-result sc-ok">✓ 验证通过</div>
      </transition>
      <transition name="sc-fade">
        <div v-if="status === 'fail'" class="sc-result sc-err">✗ 验证失败，请重试</div>
      </transition>
    </div>

    <!-- 滑动轨道 -->
    <div class="sc-track" :class="'sc-' + status" :style="{ width: width + 'px' }">
      <div class="sc-track-fill" :style="{ width: (handleLeft + handleSize / 2) + 'px' }"></div>
      <span class="sc-track-text">{{ trackText }}</span>
      <div
        class="sc-handle"
        :class="{ 'sc-handle-active': dragging }"
        :style="{ left: handleLeft + 'px', width: handleSize + 'px', height: handleSize + 'px' }"
        @mousedown="onDown"
        @touchstart.passive="onDown"
      >
        <span class="sc-handle-icon">{{ status === 'success' ? '✓' : '›' }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
/** 滑动拼图验证码：拖动补齐缺口，通过后向父组件抛出一次性 token */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { getSliderCaptcha, verifySliderCaptcha } from '@/api/auth'

/** 轨道滑块尺寸固定，与样式里的高度保持一致 */
const handleSize = 42
/** 验证失败后停留一会儿再换新图，让用户看清失败提示 */
const RETRY_DELAY = 600

const emit = defineEmits(['success', 'fail', 'change'])

const width = ref(320)
const height = ref(160)
const pieceSize = ref(50)

const bgImage = ref('')
const blockImage = ref('')
const blockY = ref(0)
const blockLeft = ref(0)
const handleLeft = ref(0)
const captchaId = ref('')
const loading = ref(false)
/** idle | moving | success | fail */
const status = ref('idle')
const dragging = ref(false)

let startX = 0
let startLeft = 0

const maxDx = () => width.value - pieceSize.value

const trackText = computed(() => {
  if (status.value === 'success') return '验证通过'
  if (status.value === 'fail') return '请重新拖动'
  return '向右拖动滑块完成拼图'
})

const load = async () => {
  loading.value = true
  status.value = 'idle'
  blockLeft.value = 0
  handleLeft.value = 0
  emit('change', false)
  try {
    const res = await getSliderCaptcha()
    const d = res.data || {}
    width.value = d.width || 320
    height.value = d.height || 160
    pieceSize.value = d.pieceSize || 50
    bgImage.value = d.image
    blockImage.value = d.block
    blockY.value = d.y || 0
    captchaId.value = d.captchaId
  } catch (e) {
    console.error('加载验证码失败', e)
  } finally {
    loading.value = false
  }
}

const pointX = (e) => (e.touches && e.touches.length ? e.touches[0].clientX : e.clientX)

const removeListeners = () => {
  window.removeEventListener('mousemove', onMove)
  window.removeEventListener('mouseup', onUp)
  window.removeEventListener('touchmove', onMove)
  window.removeEventListener('touchend', onUp)
}

const fail = () => {
  status.value = 'fail'
  emit('fail')
  emit('change', false)
  setTimeout(load, RETRY_DELAY)
}

const onMove = (e) => {
  if (!dragging.value) return
  if (e.cancelable) e.preventDefault()
  const dx = Math.max(0, Math.min(startLeft + (pointX(e) - startX), maxDx()))
  blockLeft.value = dx
  // 轨道滑块行程与拼图块行程一致，保证提交坐标即为拼图块偏移
  handleLeft.value = dx
}

const onUp = async () => {
  if (!dragging.value) return
  dragging.value = false
  removeListeners()
  try {
    const res = await verifySliderCaptcha(captchaId.value, Math.round(blockLeft.value))
    const d = res.data || {}
    if (d.success && d.token) {
      status.value = 'success'
      emit('success', d.token)
      emit('change', true)
    } else {
      fail()
    }
  } catch (e) {
    fail()
  }
}

const onDown = (e) => {
  if (status.value === 'success' || loading.value) return
  dragging.value = true
  status.value = 'moving'
  startX = pointX(e)
  startLeft = blockLeft.value
  window.addEventListener('mousemove', onMove)
  window.addEventListener('mouseup', onUp)
  window.addEventListener('touchmove', onMove, { passive: false })
  window.addEventListener('touchend', onUp)
}

onMounted(load)
onBeforeUnmount(removeListeners)

/** 父组件重开弹窗时调用，换一张新图 */
defineExpose({ reset: load })
</script>

<style scoped>
.slider-captcha { user-select: none; }
.sc-canvas {
  position: relative; border-radius: 8px; overflow: hidden;
  background: #f1f5f9; box-shadow: 0 1px 4px rgba(0,0,0,0.1);
}
.sc-bg { display: block; }
.sc-block { position: absolute; cursor: grab; filter: drop-shadow(0 0 4px rgba(0,0,0,0.4)); }
.sc-loading {
  position: absolute; inset: 0; display: flex; align-items: center; justify-content: center;
  color: #909399; font-size: 13px; background: rgba(255,255,255,0.6);
}
.sc-refresh {
  position: absolute; top: 6px; right: 6px; width: 26px; height: 26px; line-height: 26px;
  text-align: center; border-radius: 50%; background: rgba(0,0,0,0.35); color: #fff;
  font-size: 16px; cursor: pointer; transition: background 0.15s;
}
.sc-refresh:hover { background: rgba(0,0,0,0.55); }
.sc-result {
  position: absolute; left: 0; bottom: 0; width: 100%; padding: 4px 0; text-align: center;
  font-size: 13px; font-weight: 600; color: #fff;
}
.sc-ok { background: rgba(103,194,58,0.9); }
.sc-err { background: rgba(245,108,108,0.9); }
.sc-fade-enter-active, .sc-fade-leave-active { transition: opacity 0.2s; }
.sc-fade-enter-from, .sc-fade-leave-to { opacity: 0; }

/* 轨道 */
.sc-track {
  position: relative; height: 42px; margin-top: 12px; border-radius: 8px;
  background: #f1f5f9; border: 1px solid #e2e8f0; overflow: hidden;
}
.sc-track-fill {
  position: absolute; left: 0; top: 0; height: 100%; background: #d7ebff; transition: none;
}
.sc-track.sc-success .sc-track-fill { background: #e1f3d8; }
.sc-track.sc-fail .sc-track-fill { background: #fde2e2; }
.sc-track-text {
  position: absolute; width: 100%; text-align: center; line-height: 42px;
  font-size: 13px; color: #94a3b8; pointer-events: none;
}
.sc-handle {
  position: absolute; top: 0; left: 0; display: flex; align-items: center; justify-content: center;
  background: #fff; border: 1px solid #dcdfe6; border-radius: 8px; cursor: grab;
  box-shadow: 0 1px 4px rgba(0,0,0,0.15); transition: none; z-index: 2;
}
.sc-handle-active { cursor: grabbing; border-color: #0ea5e9; }
.sc-track.sc-success .sc-handle { border-color: #67c23a; color: #67c23a; }
.sc-handle-icon { font-size: 18px; font-weight: 700; color: #475569; }
.sc-track.sc-success .sc-handle-icon { color: #67c23a; }
</style>
