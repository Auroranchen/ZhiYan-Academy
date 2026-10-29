<template>
  <div class="pattern-lock-wrap">
    <div
      class="pattern-lock"
      :style="{ width: size + 'px', height: size + 'px' }"
      ref="boardRef"
      @mousedown="onStart"
      @touchstart.prevent="onStart"
    >
      <svg class="pl-svg" :width="size" :height="size">
        <line
          v-for="(seg, idx) in segments"
          :key="'s' + idx"
          :x1="seg.x1" :y1="seg.y1" :x2="seg.x2" :y2="seg.y2"
          class="pl-line"
        />
        <line
          v-if="drawing && liveSeg"
          :x1="liveSeg.x1" :y1="liveSeg.y1" :x2="liveSeg.x2" :y2="liveSeg.y2"
          class="pl-line pl-line-live"
        />
      </svg>
      <div
        v-for="i in 9"
        :key="i - 1"
        class="pl-dot"
        :class="{ active: displaySelected.includes(i - 1) }"
        :style="dotStyle(i - 1)"
      >
        <span class="pl-dot-inner"></span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'

const props = defineProps({
  modelValue: { type: String, default: '' },
  readonly: { type: Boolean, default: false },
  size: { type: Number, default: 280 }
})
const emit = defineEmits(['update:modelValue', 'complete'])

const boardRef = ref(null)
const path = ref([])
const drawing = ref(false)
const pointer = ref({ x: 0, y: 0 })

const parsePattern = (str) => (str || '').split('').map(Number).filter(n => !isNaN(n) && n >= 0 && n <= 8)

const displaySelected = computed(() => props.readonly ? parsePattern(props.modelValue) : path.value)

watch(() => props.modelValue, (val) => {
  if (drawing.value || props.readonly) return
  const parsed = parsePattern(val)
  if (parsed.join('') !== path.value.join('')) path.value = parsed
})

const cell = computed(() => props.size / 3)
const centerOf = (i) => ({ x: (i % 3 + 0.5) * cell.value, y: (Math.floor(i / 3) + 0.5) * cell.value })

const dotStyle = (i) => {
  const c = centerOf(i)
  const ds = cell.value * 0.5
  return { left: (c.x - ds / 2) + 'px', top: (c.y - ds / 2) + 'px', width: ds + 'px', height: ds + 'px' }
}

const segments = computed(() => {
  const segs = []
  const sel = displaySelected.value
  for (let k = 0; k < sel.length - 1; k++) {
    const a = centerOf(sel[k]); const b = centerOf(sel[k + 1])
    segs.push({ x1: a.x, y1: a.y, x2: b.x, y2: b.y })
  }
  return segs
})

const liveSeg = computed(() => {
  if (path.value.length === 0) return null
  const a = centerOf(path.value[path.value.length - 1])
  return { x1: a.x, y1: a.y, x2: pointer.value.x, y2: pointer.value.y }
})

// 两点之间的中点（直线穿过的点需自动连接）
const midMap = {
  '0,2': 1, '3,5': 4, '6,8': 7,
  '0,6': 3, '1,7': 4, '2,8': 5,
  '0,8': 4, '2,6': 4
}
const midBetween = (a, b) => {
  const key = Math.min(a, b) + ',' + Math.max(a, b)
  return midMap[key] != null ? midMap[key] : -1
}

const getPos = (e) => {
  const rect = boardRef.value.getBoundingClientRect()
  const clientX = e.touches ? e.touches[0].clientX : e.clientX
  const clientY = e.touches ? e.touches[0].clientY : e.clientY
  return { x: clientX - rect.left, y: clientY - rect.top }
}

const hitDot = (pos) => {
  const r = cell.value * 0.32
  for (let i = 0; i < 9; i++) {
    const c = centerOf(i)
    const dx = pos.x - c.x; const dy = pos.y - c.y
    if (dx * dx + dy * dy <= r * r) return i
  }
  return -1
}

const addDot = (i) => {
  if (path.value.includes(i)) return
  if (path.value.length > 0) {
    const last = path.value[path.value.length - 1]
    const mid = midBetween(last, i)
    if (mid >= 0 && !path.value.includes(mid)) path.value.push(mid)
  }
  path.value.push(i)
}

const onStart = (e) => {
  if (props.readonly) return
  drawing.value = true
  path.value = []
  const pos = getPos(e)
  pointer.value = pos
  const hit = hitDot(pos)
  if (hit >= 0) addDot(hit)
  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onEnd)
  document.addEventListener('touchmove', onMove, { passive: false })
  document.addEventListener('touchend', onEnd)
}

const onMove = (e) => {
  if (!drawing.value) return
  if (e.cancelable) e.preventDefault()
  const pos = getPos(e)
  pointer.value = pos
  const hit = hitDot(pos)
  if (hit >= 0) addDot(hit)
}

const onEnd = () => {
  drawing.value = false
  document.removeEventListener('mousemove', onMove)
  document.removeEventListener('mouseup', onEnd)
  document.removeEventListener('touchmove', onMove)
  document.removeEventListener('touchend', onEnd)
  const str = path.value.join('')
  emit('update:modelValue', str)
  emit('complete', str)
}

const clear = () => { path.value = [] }
defineExpose({ clear })
</script>

<style scoped>
.pattern-lock-wrap { display: inline-block; }
.pattern-lock {
  position: relative; touch-action: none; user-select: none;
  background: #f7f9fc; border-radius: 12px;
}
.pl-svg { position: absolute; top: 0; left: 0; pointer-events: none; z-index: 1; }
.pl-line { stroke: #409eff; stroke-width: 4; stroke-linecap: round; }
.pl-line-live { stroke: #79bbff; }
.pl-dot {
  position: absolute; border-radius: 50%; z-index: 2;
  display: flex; align-items: center; justify-content: center;
  border: 2px solid #c0c4cc; background: #fff; transition: border-color 0.15s, background 0.15s;
}
.pl-dot-inner { width: 30%; height: 30%; border-radius: 50%; background: transparent; transition: background 0.15s; }
.pl-dot.active { border-color: #409eff; background: #ecf5ff; }
.pl-dot.active .pl-dot-inner { background: #409eff; }
</style>
