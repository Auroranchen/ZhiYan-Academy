<!-- 女教师动画组件 -->
<template>
  <div class="animated-teacher" v-show="visible" :class="['action-' + action, { speaking: speaking }]">
    <svg viewBox="0 0 160 230" xmlns="http://www.w3.org/2000/svg" class="teacher-svg">
      <!-- 头发 -->
      <ellipse cx="75" cy="32" rx="28" ry="26" fill="#4A3728" />
      <path d="M47 32 Q47 72 55 77 L55 57 Q47 52 47 32Z" fill="#4A3728" />
      <path d="M103 32 Q103 72 95 77 L95 57 Q103 52 103 32Z" fill="#4A3728" />
      <path d="M53 24 Q60 12 75 14 Q90 12 97 24 Q93 18 75 17 Q57 18 53 24Z" fill="#3D2E1F" />

      <!-- 脸 -->
      <ellipse cx="75" cy="40" rx="22" ry="24" fill="#FDDCB5" stroke="#E8C49A" stroke-width="1" />

      <!-- 眉毛 -->
      <path class="left-brow" d="M60 30 Q65 27 70 30" fill="none" stroke="#5C4033" stroke-width="1.5" stroke-linecap="round" />
      <path class="right-brow" d="M80 30 Q85 27 90 30" fill="none" stroke="#5C4033" stroke-width="1.5" stroke-linecap="round" />

      <!-- 眼睛 -->
      <g class="eyes">
        <ellipse cx="65" cy="37" rx="3.5" ry="3" fill="#fff" />
        <circle class="left-pupil" cx="65" cy="37" r="2" fill="#2C1810" />
        <circle cx="63.5" cy="36" r="0.8" fill="#fff" opacity="0.8" />
        <ellipse cx="85" cy="37" rx="3.5" ry="3" fill="#fff" />
        <circle class="right-pupil" cx="85" cy="37" r="2" fill="#2C1810" />
        <circle cx="83.5" cy="36" r="0.8" fill="#fff" opacity="0.8" />
      </g>

      <!-- 睫毛 -->
      <path d="M61 34.5 L60 33" stroke="#3D2E1F" stroke-width="0.8" />
      <path d="M64 34 L64 32.5" stroke="#3D2E1F" stroke-width="0.8" />
      <path d="M81 34 L81 32.5" stroke="#3D2E1F" stroke-width="0.8" />
      <path d="M89 34.5 L90 33" stroke="#3D2E1F" stroke-width="0.8" />

      <!-- 鼻子 -->
      <path d="M75 42 L73 48 Q75 49 77 48Z" fill="#F0C9A0" opacity="0.6" />

      <!-- 嘴巴：微笑线（不说话时） -->
      <path class="mouth-closed" d="M68 52 Q75 57 82 52" fill="none" stroke="#D4736A" stroke-width="2" stroke-linecap="round" />
      <!-- 嘴巴：说话用的椭圆（SVG animate 控制张合） -->
      <ellipse class="mouth-open" cx="75" cy="53" rx="5" ry="0" fill="#C0524A">
        <animate ref="talkAnimRef" attributeName="ry" values="0;3.5;1;3;0" dur="0.45s" repeatCount="indefinite" begin="indefinite" />
      </ellipse>

      <!-- 腮红 -->
      <circle cx="58" cy="47" r="4" fill="#F5A0A0" opacity="0.25" />
      <circle cx="92" cy="47" r="4" fill="#F5A0A0" opacity="0.25" />

      <!-- 耳环 -->
      <circle cx="52" cy="44" r="2" fill="#FFD700" opacity="0.7" />
      <circle cx="98" cy="44" r="2" fill="#FFD700" opacity="0.7" />

      <!-- 脖子 -->
      <rect x="69" y="62" width="12" height="10" rx="3" fill="#FDDCB5" />

      <!-- 身体 -->
      <path d="M50 72 Q49 70 55 68 L69 68 L75 74 L81 68 L95 68 Q101 70 100 72 L103 132 L47 132Z" fill="#2C3E6B" />
      <path d="M69 68 L75 82 L65 72Z" fill="#F0F0F0" />
      <path d="M81 68 L75 82 L85 72Z" fill="#F0F0F0" />
      <path d="M69 68 L75 87 L81 68" fill="none" stroke="#E8E8E8" stroke-width="1" />
      <circle cx="75" cy="92" r="1.5" fill="#C0A060" />
      <circle cx="75" cy="102" r="1.5" fill="#C0A060" />
      <circle cx="75" cy="112" r="1.5" fill="#C0A060" />

      <!-- 左臂 -->
      <g class="left-arm">
        <path d="M50 74 L27 107 L25 110" stroke="#2C3E6B" stroke-width="10" stroke-linecap="round" fill="none" />
        <circle cx="25" cy="112" r="5" fill="#FDDCB5" stroke="#E8C49A" stroke-width="0.8" />
      </g>

      <!-- 右臂 + 教鞭 -->
      <g class="right-arm">
        <path d="M100 74 L123 107 L125 110" stroke="#2C3E6B" stroke-width="10" stroke-linecap="round" fill="none" />
        <circle cx="125" cy="112" r="5" fill="#FDDCB5" stroke="#E8C49A" stroke-width="0.8" />
        <!-- 教鞭：长木棍，从手延伸到左上方（指向黑板） -->
        <g class="pointer-stick">
          <line x1="127" y1="108" x2="155" y2="68" stroke="#8B5E3C" stroke-width="3" stroke-linecap="round" />
          <circle cx="155" cy="68" r="2.5" fill="#D4A76A" />
        </g>
      </g>

      <!-- 裙子 -->
      <path d="M47 132 L40 182 L110 182 L103 132Z" fill="#243255" />
      <line x1="60" y1="134" x2="55" y2="180" stroke="#1E2A48" stroke-width="0.8" opacity="0.4" />
      <line x1="75" y1="132" x2="75" y2="182" stroke="#1E2A48" stroke-width="0.8" opacity="0.4" />
      <line x1="90" y1="134" x2="95" y2="180" stroke="#1E2A48" stroke-width="0.8" opacity="0.4" />

      <!-- 腿 -->
      <line x1="60" y1="182" x2="57" y2="207" stroke="#FDDCB5" stroke-width="6" stroke-linecap="round" />
      <line x1="90" y1="182" x2="93" y2="207" stroke="#FDDCB5" stroke-width="6" stroke-linecap="round" />

      <!-- 鞋 -->
      <path d="M49 207 L57 205 L61 207 L57 210Z" fill="#2C3E6B" />
      <path d="M89 207 L93 205 L101 207 L93 210Z" fill="#2C3E6B" />
    </svg>
  </div>
</template>

<script setup>
import { watch, ref, onMounted, nextTick } from 'vue'

const props = defineProps({
  action: {
    type: String,
    default: 'idle',
    validator: (v) => ['idle', 'teaching', 'thinking', 'writing', 'encouraging', 'pointing'].includes(v)
  },
  visible: {
    type: Boolean,
    default: false
  },
  speaking: {
    type: Boolean,
    default: false
  }
})

// 使用 template ref 获取 SVG animate 元素
const talkAnimRef = ref(null)

const updateMouth = () => {
  nextTick(() => {
    const el = talkAnimRef.value
    if (!el) return
    if (props.speaking) {
      try { el.beginElement() } catch { /* SMIL 动画未就绪时忽略 */ }
    } else {
      try { el.endElement() } catch { /* SMIL 动画未就绪时忽略 */ }
    }
  })
}

// 嘴巴动画跟随 speaking prop（由语音事件驱动）
watch(() => props.speaking, updateMouth)
onMounted(() => { setTimeout(updateMouth, 300) })
</script>

<style scoped>
.animated-teacher {
  width: 160px;
  height: 250px;
  pointer-events: none;
  transition: opacity 0.3s ease;
  filter: drop-shadow(0 4px 12px rgba(0,0,0,0.15));
}
.teacher-svg { width: 100%; height: 100%; }

/* 教鞭默认隐藏 */
.pointer-stick { opacity: 0; transition: opacity 0.3s; }
/* 张嘴默认隐藏，由 speaking class 控制 */
.mouth-open { opacity: 0; }
.mouth-closed { opacity: 1; }
/* 语音播放时：显示张嘴，隐藏微笑 */
.speaking .mouth-open { opacity: 1; }
.speaking .mouth-closed { opacity: 0; }

/* ===== 空闲 ===== */
.action-idle .teacher-svg { animation: breathe 3.5s ease-in-out infinite; }
.action-idle .left-arm { animation: arm-l 4s ease-in-out infinite; transform-origin: 50px 74px; }
.action-idle .right-arm { animation: arm-r 4s ease-in-out infinite; transform-origin: 100px 74px; }
.action-idle .eyes { animation: blink 4s ease-in-out infinite; }

/* ===== 讲解：教鞭 + 说话 ===== */
.action-teaching .pointer-stick { opacity: 1; }
.action-teaching .right-arm { animation: teach-point 1.8s ease-in-out infinite; transform-origin: 100px 74px; }
.action-teaching .left-arm { animation: teach-gest 2.5s ease-in-out infinite; transform-origin: 50px 74px; }
.action-teaching .teacher-svg { animation: sway 3s ease-in-out infinite; }
.action-teaching .eyes { animation: blink 3.5s ease-in-out infinite; }

/* ===== 思考 ===== */
.action-thinking .left-arm { animation: think-chin 3s ease-in-out infinite; transform-origin: 50px 74px; }
.action-thinking .right-arm { animation: think-cross 3s ease-in-out infinite; transform-origin: 100px 74px; }
.action-thinking .teacher-svg { animation: think-tilt 3.5s ease-in-out infinite; }
.action-thinking .left-pupil, .action-thinking .right-pupil { animation: eyes-up 2.5s ease-in-out infinite; }
.action-thinking .left-brow, .action-thinking .right-brow { animation: brow-up 2.5s ease-in-out infinite; }

/* ===== 书写 ===== */
.action-writing .pointer-stick { opacity: 1; }
.action-writing .pointer-stick line { stroke: #fff; stroke-width: 2; }
.action-writing .right-arm { animation: write 0.7s ease-in-out infinite; transform-origin: 100px 74px; }
.action-writing .left-arm { animation: write-hold 2s ease-in-out infinite; transform-origin: 50px 74px; }
.action-writing .teacher-svg { animation: write-lean 2s ease-in-out infinite; }

/* ===== 鼓励 ===== */
.action-encouraging .right-arm { animation: clap-r 0.8s ease-in-out infinite; transform-origin: 100px 74px; }
.action-encouraging .left-arm { animation: clap-l 0.8s ease-in-out infinite; transform-origin: 50px 74px; }
.action-encouraging .teacher-svg { animation: nod 1.2s ease-in-out infinite; }

/* ===== 指点黑板 ===== */
.action-pointing .pointer-stick { opacity: 1; }
.action-pointing .right-arm { animation: point-board 2s ease-in-out infinite; transform-origin: 100px 74px; }
.action-pointing .left-arm { animation: point-hip 2s ease-in-out infinite; transform-origin: 50px 74px; }
.action-pointing .eyes { animation: blink 3s ease-in-out infinite; }

/* ===== 关键帧 ===== */
@keyframes breathe { 0%,100%{transform:translateY(0)} 50%{transform:translateY(-3px)} }
@keyframes arm-l { 0%,100%{transform:rotate(0)} 50%{transform:rotate(-3deg)} }
@keyframes arm-r { 0%,100%{transform:rotate(0)} 50%{transform:rotate(3deg)} }
@keyframes blink { 0%,42%,48%,100%{transform:scaleY(1)} 45%{transform:scaleY(0.1)} }
@keyframes sway { 0%,100%{transform:translateX(0) translateY(0)} 50%{transform:translateX(2px) translateY(-2px)} }

@keyframes teach-point { 0%,100%{transform:rotate(-25deg)} 30%{transform:rotate(-45deg)} 60%{transform:rotate(-35deg)} }
@keyframes teach-gest { 0%,100%{transform:rotate(5deg)} 50%{transform:rotate(15deg)} }

@keyframes think-chin { 0%,100%{transform:rotate(50deg) translateY(-20px)} 50%{transform:rotate(52deg) translateY(-22px)} }
@keyframes think-cross { 0%,100%{transform:rotate(-15deg)} 50%{transform:rotate(-18deg)} }
@keyframes think-tilt { 0%,100%{transform:rotate(0) translateY(0)} 50%{transform:rotate(4deg) translateY(-2px)} }
@keyframes eyes-up { 0%,100%{transform:translateY(0)} 50%{transform:translateY(-1.5px)} }
@keyframes brow-up { 0%,100%{transform:translateY(0)} 50%{transform:translateY(-2px)} }

@keyframes write { 0%{transform:rotate(-20deg) translateX(5px)} 25%{transform:rotate(-25deg) translateX(12px)} 50%{transform:rotate(-18deg) translateX(18px)} 75%{transform:rotate(-25deg) translateX(12px)} 100%{transform:rotate(-20deg) translateX(5px)} }
@keyframes write-hold { 0%,100%{transform:rotate(10deg)} 50%{transform:rotate(12deg)} }
@keyframes write-lean { 0%,100%{transform:translateY(0) rotate(0)} 50%{transform:translateY(2px) rotate(2deg)} }

@keyframes clap-r { 0%,100%{transform:rotate(-50deg)} 50%{transform:rotate(-35deg)} }
@keyframes clap-l { 0%,100%{transform:rotate(50deg)} 50%{transform:rotate(35deg)} }
@keyframes nod { 0%,100%{transform:translateY(0)} 25%{transform:translateY(3px)} 50%{transform:translateY(0)} 75%{transform:translateY(3px)} }

/* 指向黑板：手臂举高，教鞭指向左上方 */
@keyframes point-board { 0%,100%{transform:rotate(-60deg) translateY(-8px)} 50%{transform:rotate(-65deg) translateY(-12px) translateX(3px)} }
@keyframes point-hip { 0%,100%{transform:rotate(20deg)} 50%{transform:rotate(22deg)} }
</style>