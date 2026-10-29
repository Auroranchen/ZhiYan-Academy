<template>
  <div class="ex-page">
    <div class="ex-head">
      <div class="ex-head__l">
        <div class="head-mark"><Icon icon="ph:chalkboard-teacher-duotone" /></div>
        <div>
          <span class="eyebrow">AI MICRO CLASS</span>
          <h2>AI 讲解动画 · 多模态微课</h2>
          <span class="sub">把抽象知识变成一堂看得懂、听得进的生动微课</span>
        </div>
      </div>
      <el-tag size="small" type="info" effect="plain" round>AI 生成内容，仅供参考</el-tag>
    </div>

    <div class="ex-input">
      <el-input v-model="subject" placeholder="学科（可选，如：数据结构）" clearable class="in-sub" />
      <el-input v-model="topic" :placeholder="recognizing ? '正在聆听，请说话…' : '要讲解的知识点，如：快速排序的分治思想'"
        clearable maxlength="40" class="in-topic" @keyup.enter="generate" />
      <VoiceButton
        class="mic-btn"
        :supported="speechSupported"
        :recording="recognizing"
        :disabled="generating"
        @toggle="toggleVoiceInput"
      />
      <el-button type="primary" :icon="MagicStick" :loading="generating" @click="generate">生成微课</el-button>
    </div>
    <div class="quick">
      <span class="q-label">试试：</span>
      <el-tag v-for="t in samples" :key="t" class="q" effect="plain" @click="useSample(t)">{{ t }}</el-tag>
    </div>

    <!-- 播放器 -->
    <div v-if="scenes.length" ref="playerRef" class="player" :class="{ fs: isFs }">
      <div class="stage" :class="'v-' + (cur.visual?.type || 'center')">
        <div class="stage-progress"><div class="stage-progress__bar" :style="{ width: progressPct }"></div></div>
        <div class="stage__bg"></div>
        <div class="stage-orbit orbit-one"></div>
        <div class="stage-orbit orbit-two"></div>
        <div class="stage__head">
          <span class="stage__title"><Icon icon="ph:sparkle-fill" /> {{ title }}</span>
          <div class="stage__head-r">
            <span v-if="cur.keypoint" class="stage__kp"><Icon icon="ph:star-four-duotone" /> {{ cur.keypoint }}</span>
            <span class="stage__idx">{{ index + 1 }} / {{ scenes.length }}</span>
          </div>
        </div>

        <transition name="scene" mode="out-in">
          <div class="scene" :key="index">
            <span class="scene__eyebrow">CHAPTER {{ String(index + 1).padStart(2, '0') }}</span>
            <h3 class="scene__title">{{ cur.title }}</h3>

            <!-- center / formula -->
            <div v-if="cur.visual?.type === 'center' || cur.visual?.type === 'formula'" class="vis-center">
              <div :class="cur.visual?.type === 'formula' ? 'big-formula' : 'big-text'">{{ cur.visual?.text || cur.title }}</div>
            </div>

            <!-- bullets -->
            <ul v-else-if="cur.visual?.type === 'bullets'" class="vis-bullets">
              <li v-for="(it, i) in (cur.visual?.items || [])" :key="i" :style="{ '--i': i }">
                <span class="dot"></span>{{ it }}
              </li>
            </ul>

            <!-- steps -->
            <div v-else-if="cur.visual?.type === 'steps'" class="vis-steps">
              <template v-for="(it, i) in (cur.visual?.items || [])" :key="i">
                <div class="step" :style="{ '--i': i }">
                  <div class="step__no">{{ i + 1 }}</div>
                  <div class="step__txt">{{ it }}</div>
                </div>
                <div v-if="i < (cur.visual?.items || []).length - 1" class="step__arrow" :style="{ '--i': i }">→</div>
              </template>
            </div>

            <!-- compare -->
            <div v-else-if="cur.visual?.type === 'compare'" class="vis-compare">
              <div class="cmp cmp-l"><div class="cmp-h">A</div><p>{{ cur.visual?.left }}</p></div>
              <div class="cmp-vs">VS</div>
              <div class="cmp cmp-r"><div class="cmp-h">B</div><p>{{ cur.visual?.right }}</p></div>
            </div>

            <!-- code 代码演示 -->
            <div v-else-if="cur.visual?.type === 'code'" class="vis-code">
              <div class="code-win">
                <div class="code-bar">
                  <span class="cdot-r"></span><span class="cdot-y"></span><span class="cdot-g"></span>
                  <span class="code-lang">{{ cur.visual?.lang || 'code' }}</span>
                </div>
                <pre class="code-body"><code><span v-for="(ln, i) in codeLines" :key="i" class="code-line"
                  :style="{ '--i': i }">{{ ln || ' ' }}</span></code></pre>
              </div>
              <div v-if="(cur.visual?.items || []).length" class="code-notes">
                <span v-for="(n, i) in cur.visual.items" :key="i" class="code-note" :style="{ '--i': i }">
                  <Icon icon="ph:arrow-bend-down-right-bold" /> {{ n }}
                </span>
              </div>
            </div>

            <!-- quiz 互动小测 -->
            <div v-else-if="cur.visual?.type === 'quiz'" class="vis-quiz">
              <div class="quiz-q"><Icon icon="ph:seal-question-duotone" /> {{ cur.visual?.text }}</div>
              <div class="quiz-opts">
                <button v-for="(o, i) in (cur.visual?.items || [])" :key="i" class="quiz-opt"
                  :class="quizOptClass(i)" :style="{ '--i': i }" :disabled="quizSelected !== null" @click="answerQuiz(i)">
                  <span class="opt-letter">{{ letters[i] }}</span><span class="opt-text">{{ o }}</span>
                </button>
              </div>
              <transition name="fade-up">
                <div v-if="quizSelected !== null" class="quiz-explain" :class="quizCorrect ? 'right' : 'wrong'">
                  <Icon :icon="quizCorrect ? 'ph:confetti-duotone' : 'ph:lightbulb-duotone'" />
                  <span>{{ quizCorrect ? '回答正确！' : '正确答案是 ' + letters[quizAnswer] + '。' }}{{ cur.visual?.explain || '' }}</span>
                </div>
              </transition>
              <div v-if="quizSelected === null" class="quiz-hint">点击选项作答，回答后自动继续播放</div>
            </div>

            <div v-else class="vis-center"><div class="big-text">{{ cur.title }}</div></div>
          </div>
        </transition>

        <!-- 字幕 -->
        <div class="subtitle">
          <div class="subtitle__voice"><Icon icon="ph:waveform-bold" :class="{ talking: speaking }" /><span>AI 老师</span></div>
          <span class="subtitle__text">{{ cur.narration }}</span>
        </div>
      </div>

      <!-- 控制条 -->
      <div class="controls">
        <div class="ctrl-dots">
          <span v-for="(s, i) in scenes" :key="i" class="cdot" :class="{ on: i === index, done: i < index }"
            @click="goScene(i)"></span>
        </div>
        <div class="ctrl-btns">
          <el-button :icon="ArrowLeft" circle :disabled="index === 0" @click="prev" />
          <el-button type="primary" :icon="playing ? VideoPause : VideoPlay" circle size="large" @click="togglePlay" />
          <el-button :icon="ArrowRight" circle :disabled="index === scenes.length - 1" @click="next" />
          <el-button :icon="RefreshRight" circle @click="restart" />
        </div>
        <div class="ctrl-right">
          <el-tooltip content="播放倍速" placement="top">
            <button class="rate-btn" @click="cycleRate">{{ rate }}x</button>
          </el-tooltip>
          <el-tooltip content="语音讲解" placement="top">
            <el-switch v-model="ttsOn" inline-prompt active-text="配音" inactive-text="静音" @change="onTtsToggle" />
          </el-tooltip>
          <el-tooltip :content="isFs ? '退出全屏' : '全屏观看'" placement="top">
            <el-button :icon="FullScreen" circle @click="toggleFs" />
          </el-tooltip>
        </div>
      </div>
      <p v-if="!ttsAvailable" class="tts-tip">当前环境不支持语音合成，已按时间轴自动播放字幕。</p>
    </div>

    <el-empty v-else-if="!generating" description="输入知识点，生成你的第一支 AI 讲解微课" />
    <div v-if="generating" v-loading="true" element-loading-text="AI 正在编排分镜脚本…" class="loading-box"></div>

    <!-- 没听懂？针对当前微课继续追问 -->
    <div v-if="scenes.length" class="followup">
      <div class="fu-head">
        <div class="fu-title"><Icon icon="ph:chats-teardrop-duotone" /> 没听懂？针对本节微课继续追问</div>
        <el-button v-if="fuMessages.length" size="small" text type="danger" @click="fuMessages = []">清空对话</el-button>
      </div>
      <div v-if="fuMessages.length" class="fu-msgs">
        <div v-for="(m, i) in fuMessages" :key="i" class="fu-msg" :class="m.role">
          <div v-if="m.role === 'user'" class="fu-bubble">{{ m.content }}</div>
          <div v-else class="fu-bubble markdown-body" v-safe-html="renderMd(m.content)"></div>
        </div>
        <div v-if="fuLoading && !fuStreaming" class="fu-msg assistant"><div class="fu-bubble fu-typing">思考中…</div></div>
      </div>
      <div class="fu-input">
        <el-input v-model="fuInput" :placeholder="`就「${topic || title}」提问，如：能再举一个例子吗？`"
          :disabled="fuLoading" @keyup.enter="askFollowup" />
        <el-button type="primary" :loading="fuLoading" @click="askFollowup">追问</el-button>
      </div>
      <div class="fu-quick">
        <el-tag v-for="q in fuQuickAsks" :key="q" effect="plain" class="fu-q" @click="askFollowup(q)">{{ q }}</el-tag>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { MagicStick, VideoPlay, VideoPause, ArrowLeft, ArrowRight, RefreshRight, FullScreen } from '@element-plus/icons-vue'
import { Icon } from '@iconify/vue'
import { VoiceButton } from '@/components/ui'
import { buildExplainerScenes } from '@/api/agent'
import { useVoiceInput } from '@/utils/useVoiceInput'
import XfTtsPlayer from '@/utils/xfTts'
import { sendMessageStream } from '@/utils/ai'
import { renderMarkdown } from '@/utils/miniMarkdown'

const subject = ref('')
const topic = ref('')
const generating = ref(false)
const title = ref('')
const scenes = ref([])
const index = ref(0)
const playing = ref(false)
const speaking = ref(false)
const ttsOn = ref(true)

// 语音输入知识点（讯飞语音听写，浏览器识别兜底）
const { speechSupported, recognizing, toggleVoiceInput } = useVoiceInput(topic)

const samples = ['快速排序的分治思想', 'TCP 三次握手', '数据库索引为什么快', '面向对象三大特性', '哈希表冲突处理']
const cur = computed(() => scenes.value[index.value] || {})
const letters = ['A', 'B', 'C', 'D']

const ttsSupported = typeof window !== 'undefined' && 'speechSynthesis' in window
let advanceTimer = null
let browserTtsUnlocked = false

const unlockBrowserTts = () => {
  if (!ttsSupported || browserTtsUnlocked) return
  try {
    const synth = window.speechSynthesis
    if (synth.paused) synth.resume()
    const warmup = new SpeechSynthesisUtterance(' ')
    warmup.lang = 'zh-CN'
    warmup.volume = 0
    synth.speak(warmup)
    browserTtsUnlocked = true
  } catch (e) { /* 后续仍可降级为字幕时间轴 */ }
}

// 温和自然的教师音色：略慢语速、轻微抬高音调，失败时降级浏览器合成
const xfTts = new XfTtsPlayer({ vcn: 'xiaoyan', speed: 48, volume: 68, pitch: 52 })
const xfTtsUsable = ref(true)
const ttsAvailable = computed(() => xfTtsUsable.value || ttsSupported)

/** 朗读一段文本：讯飞 TTS 优先，浏览器合成兜底，最后定时器兜底 */
const speakText = (text, done) => {
  const finish = () => { speaking.value = false; done && done() }
  if (xfTtsUsable.value) {
    xfTts.speak(text, {
      rate: rate.value,
      onStart: () => { speaking.value = true },
      onEnd: finish,
      onError: () => {
        // 讯飞合成失败：本次会话降级浏览器合成
        xfTtsUsable.value = false
        browserSpeak(text, done)
      }
    })
    return
  }
  browserSpeak(text, done)
}

const browserSpeak = (text, done) => {
  if (ttsSupported) {
    try {
      window.speechSynthesis.cancel()
      const u = new SpeechSynthesisUtterance(text || '')
      u.lang = 'zh-CN'
      u.rate = 0.94 * rate.value
      u.pitch = 1.06
      u.volume = 0.92
      const voices = window.speechSynthesis.getVoices()
      u.voice = voices.find(v => /Xiaoxiao|Xiaoyi|Huihui|小晓|晓伊|慧慧/i.test(v.name)) ||
        voices.find(v => /^zh(-|_)?CN/i.test(v.lang)) ||
        voices.find(v => /^zh/i.test(v.lang)) ||
        null
      u.onstart = () => { speaking.value = true }
      u.onend = () => { speaking.value = false; done && done() }
      u.onerror = () => {
        speaking.value = false
        ElMessage.warning('语音播放被浏览器阻止，请点击播放按钮重试，并确认标签页未静音')
        advanceTimer = setTimeout(() => done && done(), estimateMs(text) / rate.value)
      }
      if (window.speechSynthesis.paused) window.speechSynthesis.resume()
      // Chrome 在 cancel 后立即 speak 偶发吞首句
      setTimeout(() => window.speechSynthesis.speak(u), 60)
      return
    } catch (e) { /* 降级到定时 */ }
  }
  advanceTimer = setTimeout(() => done && done(), estimateMs(text) / rate.value)
}

const stopSpeak = () => {
  xfTts.stop()
  if (ttsSupported) window.speechSynthesis.cancel()
  speaking.value = false
}

// 总进度
const progressPct = computed(() =>
  scenes.value.length ? ((index.value + 1) / scenes.value.length * 100).toFixed(1) + '%' : '0%')

// code 分镜：按行渲染（逐行入场动画）
const codeLines = computed(() => String(cur.value.visual?.text || '').split('\n'))

// quiz 互动小测
const quizSelected = ref(null)
const quizAnswer = computed(() => Number(cur.value.visual?.answer) || 0)
const quizCorrect = computed(() => quizSelected.value === quizAnswer.value)
const quizOptClass = (i) => {
  if (quizSelected.value === null) return ''
  if (i === quizAnswer.value) return 'correct'
  if (i === quizSelected.value) return 'wrong'
  return 'dim'
}
const answerQuiz = (i) => {
  if (quizSelected.value !== null) return
  quizSelected.value = i
  const text = (quizCorrect.value ? '回答正确！' : '正确答案是' + letters[quizAnswer.value] + '。') + (cur.value.visual?.explain || '')
  if (ttsOn.value) speakText(text)
  // 作答后继续播放
  if (playing.value) advanceTimer = setTimeout(autoNext, 4200)
}
watch(index, () => { quizSelected.value = null })

// 倍速
const rates = [1, 1.25, 1.5, 0.8]
const rate = ref(1)
const cycleRate = () => {
  rate.value = rates[(rates.indexOf(rate.value) + 1) % rates.length]
  if (playing.value) playCurrent()
}

// 全屏
const playerRef = ref(null)
const isFs = ref(false)
const toggleFs = () => {
  if (!document.fullscreenElement) playerRef.value?.requestFullscreen?.()
  else document.exitFullscreen()
}
const onFsChange = () => { isFs.value = !!document.fullscreenElement }

const useSample = (t) => { topic.value = t; generate() }

const generate = async () => {
  if (!topic.value.trim()) { ElMessage.warning('请输入知识点'); return }
  stopAll()
  // 在用户点击事件内解锁语音，避免等待 AI 生成后失去浏览器用户激活
  unlockBrowserTts()
  generating.value = true
  scenes.value = []
  fuMessages.value = []
  try {
    const res = await buildExplainerScenes({ subject: subject.value, topic: topic.value })
    const data = res.data || {}
    title.value = data.title || '讲解微课'
    scenes.value = (data.scenes || []).filter(s => s && (s.narration || s.title))
    if (!scenes.value.length) { ElMessage.warning('生成失败，请重试'); return }
    index.value = 0
    play()
  } catch (e) { /* 拦截器已提示 */ } finally {
    generating.value = false
  }
}

// ===== 追问模式：带微课上下文的即时答疑（流式输出） =====
const fuMessages = ref([])
const fuInput = ref('')
const fuLoading = ref(false)
const fuStreaming = ref(false)
const fuQuickAsks = ['能再举一个例子吗？', '这个知识点常见的坑有哪些？', '和相近概念有什么区别？']
const renderMd = (s) => renderMarkdown(s)

const followupContext = () => {
  const kps = scenes.value.map(s => s.keypoint).filter(Boolean).slice(0, 8).join('；')
  return `学生刚看完一节 AI 讲解微课并提出追问。微课主题：「${topic.value || title.value}」` +
    (subject.value ? `（学科：${subject.value}）` : '') +
    (kps ? `。微课要点：${kps}` : '') +
    '。请围绕该主题精准解答学生的追问，可给出补充示例、图解式文字说明或易错提醒。'
}

const askFollowup = async (preset) => {
  const q = (typeof preset === 'string' ? preset : fuInput.value).trim()
  if (!q || fuLoading.value) return
  fuInput.value = ''
  fuMessages.value.push({ role: 'user', content: q })
  fuLoading.value = true
  fuStreaming.value = false
  const history = fuMessages.value.slice(-6)
  const reply = { role: 'assistant', content: '' }
  try {
    await sendMessageStream(history, 'student', '', (chunk) => {
      if (!fuStreaming.value) { fuMessages.value.push(reply); fuStreaming.value = true }
      reply.content += chunk
      // 触发响应式更新
      fuMessages.value = [...fuMessages.value.slice(0, -1), { ...reply }]
    }, null, { extraContext: followupContext() })
  } catch (e) {
    fuMessages.value.push({ role: 'assistant', content: '回答失败了，请稍后重试。' })
  } finally {
    fuLoading.value = false
    fuStreaming.value = false
  }
}

const estimateMs = (text) => Math.max(3500, Math.min(12000, (String(text || '').length) * 230 + 1500))

const playCurrent = () => {
  clearTimeout(advanceTimer)
  stopSpeak()
  const scene = cur.value
  // 互动小测镜头：朗读题目后暂停等待作答，不自动跳镜
  const isQuiz = scene.visual?.type === 'quiz' && quizSelected.value === null
  const text = scene.narration || scene.title || ''
  const done = () => { if (playing.value && !isQuiz) autoNext() }
  if (ttsOn.value) {
    speakText(text, done)
    return
  }
  if (playing.value && !isQuiz) advanceTimer = setTimeout(autoNext, estimateMs(text) / rate.value)
}

const autoNext = () => {
  if (index.value < scenes.value.length - 1) { index.value++; playCurrent() }
  else { playing.value = false; speaking.value = false }
}

const play = () => { playing.value = true; playCurrent() }
const pause = () => {
  playing.value = false
  clearTimeout(advanceTimer)
  stopSpeak()
}
const togglePlay = () => { playing.value ? pause() : play() }
const stopAll = () => { pause(); index.value = 0 }

const goScene = (i) => { index.value = i; playCurrent() }
const next = () => { if (index.value < scenes.value.length - 1) { index.value++; playCurrent() } }
const prev = () => { if (index.value > 0) { index.value--; playCurrent() } }
const restart = () => { index.value = 0; play() }

const onTtsToggle = () => {
  stopSpeak()
  if (playing.value) playCurrent()
}

onMounted(() => { document.addEventListener('fullscreenchange', onFsChange) })
onBeforeUnmount(() => {
  pause()
  document.removeEventListener('fullscreenchange', onFsChange)
})
</script>

<style scoped>
/*
 * 卡片外壳跟随主题（底色、描边、正文色走令牌），否则深色模式下会是一块白板；
 * --blue / --cyan / --warm 与下方的舞台配色属于这一页的视觉资产，刻意不跟随主题。
 */
.ex-page { --ink: var(--zy-text-primary); --blue: #2364d2; --cyan: #37b8b1; --warm: #ffb25b;
  background:
    radial-gradient(circle at 96% 3%, rgba(55,184,177,.09), transparent 24%),
    radial-gradient(circle at 2% 16%, rgba(255,178,91,.1), transparent 20%), var(--zy-bg-surface);
  border: 1px solid var(--zy-border-subtle); border-radius: 18px; padding: 24px 28px; min-height: 72vh;
  box-shadow: var(--zy-shadow-md); }
.ex-head { display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 14px; margin-bottom: 20px; }
.ex-head__l { display: flex; align-items: center; gap: 14px; }
.head-mark { width: 48px; height: 48px; display: grid; place-items: center; flex-shrink: 0; border-radius: 15px;
  color: #fff; font-size: 26px; background: linear-gradient(145deg, var(--blue), #23438c);
  box-shadow: 0 8px 20px rgba(35,100,210,.24); transform: rotate(-2deg); }
.eyebrow { display: block; margin-bottom: 3px; color: var(--cyan); font-size: 10px; font-weight: 800; letter-spacing: 1.8px; }
.ex-head__l h2 { margin: 0 0 4px; color: var(--ink); font-size: 21px; letter-spacing: -.3px; }
.ex-head__l .sub { color: var(--zy-text-tertiary); font-size: 13px; }

.ex-input { display: flex; gap: 10px; margin-bottom: 11px; padding: 11px; flex-wrap: wrap;
  background: rgba(246,249,253,.92); border: 1px solid #e7edf5; border-radius: 13px; }
.in-sub { max-width: 220px; }
.in-topic { max-width: 460px; flex: 1; }
.quick { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 16px; }
.q-label { color: #94a3b8; font-size: 13px; }
.q { cursor: pointer; transition: transform .2s ease, box-shadow .2s ease; }
.q:hover { transform: translateY(-1px); box-shadow: 0 4px 12px rgba(35,100,210,.1); }

.mic-btn { align-self: center; }

.loading-box { height: 40vh; }

/* 播放舞台 */
.player { border: 1px solid #dce5f0; border-radius: 20px; overflow: hidden;
  box-shadow: 0 18px 45px rgba(21,39,72,.16); }
.stage { position: relative; height: 56vh; min-height: 380px; padding: 24px 32px 64px;
  background: linear-gradient(145deg, #122541 0%, #0b172b 62%, #102c3a 100%); color: #fff; overflow: hidden; }
.stage__bg { position: absolute; inset: 0; background-image:
  linear-gradient(rgba(255,255,255,.022) 1px, transparent 1px),
  linear-gradient(90deg, rgba(255,255,255,.022) 1px, transparent 1px),
  radial-gradient(circle at 12% 24%, rgba(255,178,91,.2), transparent 34%),
  radial-gradient(circle at 86% 72%, rgba(55,184,177,.22), transparent 38%);
  background-size: 32px 32px, 32px 32px, auto, auto;
  mask-image: linear-gradient(to bottom, #000 50%, transparent 100%);
  animation: floatBg 11s ease-in-out infinite alternate; }
@keyframes floatBg { from { transform: translateY(-8px) scale(1); } to { transform: translateY(8px) scale(1.05); } }
.stage-orbit { position: absolute; border: 1px solid rgba(255,255,255,.08); border-radius: 50%; pointer-events: none; }
.orbit-one { width: 290px; height: 290px; right: -135px; top: 34px; box-shadow: inset 0 0 40px rgba(55,184,177,.05); }
.orbit-two { width: 110px; height: 110px; left: -58px; bottom: 72px; border-color: rgba(255,178,91,.14); }
.stage__head { position: relative; display: flex; justify-content: space-between; align-items: center;
  font-size: 13px; color: #c7d2fe; z-index: 2; gap: 10px; }
.stage__title { display: inline-flex; align-items: center; gap: 7px; color: #d8e5f3; font-weight: 700; letter-spacing: .3px; }
.stage__title svg { color: var(--warm); }
.stage__head-r { display: flex; align-items: center; gap: 10px; }
.stage__kp { display: inline-flex; align-items: center; gap: 5px; background: rgba(55,184,177,.14);
  border: 1px solid rgba(91,211,204,.28); color: #b8f0ec; padding: 3px 12px; border-radius: 999px;
  font-size: 12px; font-weight: 600; animation: pop .4s ease both; }
.stage__idx { font-family: 'SFMono-Regular', Consolas, monospace; color: #94a3b8; }

/* 顶部总进度条 */
.stage-progress { position: absolute; top: 0; left: 0; right: 0; height: 3px;
  background: rgba(255,255,255,.08); z-index: 3; }
.stage-progress__bar { height: 100%; background: linear-gradient(90deg,var(--warm),#56d4cc);
  transition: width .5s ease; box-shadow: 0 0 10px rgba(86,212,204,.55); }

.scene { position: relative; z-index: 2; margin-top: 18px; display: flex; flex-direction: column; gap: 18px; height: calc(100% - 36px); }
.scene__eyebrow { margin-bottom: -13px; color: rgba(255,178,91,.84); font-size: 10px; font-weight: 800; letter-spacing: 2px; }
.scene__title { margin: 0; font-size: 25px; font-weight: 800; letter-spacing: -.4px;
  background: linear-gradient(90deg,#fff,#c8eced); -webkit-background-clip: text; background-clip: text; color: transparent; }

.vis-center { flex: 1; display: flex; align-items: center; justify-content: center; }
.big-text { font-size: 30px; font-weight: 800; text-align: center; line-height: 1.5; color: #f1f5f9;
  animation: pop .5s ease both; max-width: 90%; }
.big-formula { font-family: 'SFMono-Regular', Consolas, monospace; font-size: 26px; color: #a5f3fc;
  background: rgba(255,255,255,.06); border: 1px solid rgba(255,255,255,.14); padding: 16px 24px; border-radius: 12px;
  animation: pop .5s ease both; text-align: center; }
@keyframes pop { from { opacity: 0; transform: scale(.9); } to { opacity: 1; transform: scale(1); } }

.vis-bullets { list-style: none; padding: 0; margin: auto 0; display: flex; flex-direction: column; gap: 14px; }
.vis-bullets li { display: flex; align-items: center; gap: 12px; font-size: 19px; color: #e2e8f0;
  opacity: 0; transform: translateX(-16px); animation: slideIn .5s ease forwards; animation-delay: calc(var(--i) * .25s + .1s); }
.vis-bullets .dot { width: 10px; height: 10px; border-radius: 50%; flex-shrink: 0;
  background: linear-gradient(135deg,var(--warm),#56d4cc); box-shadow: 0 0 12px rgba(86,212,204,.45); }
@keyframes slideIn { to { opacity: 1; transform: none; } }

.vis-steps { display: flex; align-items: center; justify-content: center; flex-wrap: wrap; gap: 10px; margin: auto 0; }
.step { display: flex; flex-direction: column; align-items: center; gap: 8px; min-width: 110px;
  background: rgba(255,255,255,.06); border: 1px solid rgba(255,255,255,.14); border-radius: 12px; padding: 14px 12px;
  opacity: 0; transform: translateY(14px); animation: slideUp .5s ease forwards; animation-delay: calc(var(--i) * .3s + .1s); }
.step__no { width: 30px; height: 30px; border-radius: 50%; background: linear-gradient(135deg,#e99345,#2ba5a0);
  display: flex; align-items: center; justify-content: center; font-weight: 800; }
.step__txt { font-size: 14px; color: #e2e8f0; text-align: center; }
.step__arrow { font-size: 22px; color: #818cf8; opacity: 0; animation: fadeIn .4s ease forwards; animation-delay: calc(var(--i) * .3s + .35s); }
@keyframes slideUp { to { opacity: 1; transform: none; } }
@keyframes fadeIn { to { opacity: 1; } }

.vis-compare { display: flex; align-items: stretch; justify-content: center; gap: 16px; margin: auto 0; }
.cmp { flex: 1; max-width: 320px; background: rgba(255,255,255,.06); border: 1px solid rgba(255,255,255,.14);
  border-radius: 14px; padding: 18px; animation: pop .5s ease both; }
.cmp-l { border-top: 3px solid #22d3ee; } .cmp-r { border-top: 3px solid #f472b6; animation-delay: .15s; }
.cmp-h { width: 34px; height: 34px; border-radius: 9px; display: flex; align-items: center; justify-content: center;
  font-weight: 800; background: rgba(255,255,255,.12); margin-bottom: 10px; }
.cmp p { margin: 0; font-size: 15px; color: #e2e8f0; line-height: 1.6; }
.cmp-vs { align-self: center; font-weight: 800; color: #94a3b8; }

/* code 代码演示 */
.vis-code { display: flex; flex-direction: column; gap: 10px; margin: auto 0; min-height: 0; overflow: hidden; }
.code-win { background: rgba(2,6,23,.7); border: 1px solid rgba(255,255,255,.12); border-radius: 12px;
  overflow: hidden; max-width: 720px; width: 100%; align-self: center; animation: pop .4s ease both; }
.code-bar { display: flex; align-items: center; gap: 6px; padding: 8px 12px;
  background: rgba(255,255,255,.06); border-bottom: 1px solid rgba(255,255,255,.08); }
.code-bar .cdot-r, .code-bar .cdot-y, .code-bar .cdot-g { width: 10px; height: 10px; border-radius: 50%; }
.code-bar .cdot-r { background: #ff5f56; } .code-bar .cdot-y { background: #ffbd2e; } .code-bar .cdot-g { background: #27c93f; }
.code-lang { margin-left: 8px; color: #94a3b8; font-size: 11.5px; letter-spacing: 1px;
  font-family: 'SFMono-Regular', Consolas, monospace; text-transform: uppercase; }
.code-body { margin: 0; padding: 12px 16px; overflow: auto; max-height: 32vh; }
.code-body code { display: block; }
.code-line { display: block; font-family: 'SFMono-Regular', Consolas, monospace; font-size: 13.5px;
  color: #e2e8f0; line-height: 1.75; white-space: pre-wrap; word-break: break-word;
  opacity: 0; transform: translateX(-10px); animation: slideIn .4s ease forwards;
  animation-delay: calc(var(--i) * .12s + .15s); }
.code-notes { display: flex; flex-wrap: wrap; gap: 8px; justify-content: center; }
.code-note { display: inline-flex; align-items: center; gap: 5px; background: rgba(34,211,238,.12);
  border: 1px solid rgba(34,211,238,.3); color: #a5f3fc; font-size: 12.5px; padding: 4px 12px;
  border-radius: 999px; opacity: 0; animation: fadeIn .4s ease forwards;
  animation-delay: calc(var(--i) * .25s + .8s); }

/* quiz 互动小测 */
.vis-quiz { display: flex; flex-direction: column; gap: 14px; margin: auto 0; max-width: 640px;
  width: 100%; align-self: center; }
.quiz-q { display: flex; align-items: flex-start; gap: 8px; font-size: 19px; font-weight: 700;
  color: #f1f5f9; line-height: 1.6; animation: pop .4s ease both; }
.quiz-q svg { font-size: 24px; color: #fbbf24; flex-shrink: 0; margin-top: 2px; }
.quiz-opts { display: flex; flex-direction: column; gap: 10px; }
.quiz-opt { display: flex; align-items: center; gap: 12px; text-align: left; padding: 12px 16px;
  background: rgba(255,255,255,.07); border: 1px solid rgba(255,255,255,.16); border-radius: 12px;
  color: #e2e8f0; font-size: 15px; cursor: pointer; transition: all .2s ease;
  opacity: 0; transform: translateY(10px); animation: slideUp .4s ease forwards;
  animation-delay: calc(var(--i) * .15s + .2s); }
.quiz-opt:hover:not(:disabled) { background: rgba(124,58,237,.25); border-color: #a78bfa; transform: translateY(-1px); }
.quiz-opt:disabled { cursor: default; }
.quiz-opt.correct { background: rgba(34,197,94,.22); border-color: #4ade80; color: #bbf7d0; }
.quiz-opt.wrong { background: rgba(239,68,68,.2); border-color: #f87171; color: #fecaca; }
.quiz-opt.dim { opacity: .45 !important; }
.opt-letter { width: 28px; height: 28px; border-radius: 8px; flex-shrink: 0; display: flex;
  align-items: center; justify-content: center; font-weight: 800; font-size: 13px;
  background: rgba(255,255,255,.12); }
.quiz-opt.correct .opt-letter { background: #22c55e; color: #fff; }
.quiz-opt.wrong .opt-letter { background: #ef4444; color: #fff; }
.quiz-explain { display: flex; align-items: flex-start; gap: 8px; padding: 10px 14px;
  border-radius: 10px; font-size: 14px; line-height: 1.6; }
.quiz-explain svg { font-size: 20px; flex-shrink: 0; margin-top: 1px; }
.quiz-explain.right { background: rgba(34,197,94,.15); border: 1px solid rgba(74,222,128,.4); color: #bbf7d0; }
.quiz-explain.wrong { background: rgba(251,191,36,.12); border: 1px solid rgba(251,191,36,.35); color: #fde68a; }
.quiz-hint { color: #64748b; font-size: 12.5px; text-align: center; }
.fade-up-enter-active { transition: all .35s ease; }
.fade-up-enter-from { opacity: 0; transform: translateY(8px); }

.subtitle { position: absolute; left: 0; right: 0; bottom: 14px; z-index: 2; margin: 0 24px;
  display: flex; align-items: center; gap: 13px; background: rgba(6,16,31,.72); backdrop-filter: blur(12px);
  border: 1px solid rgba(255,255,255,.12); border-radius: 13px; padding: 10px 15px; font-size: 14px; color: #eaf0f7; line-height: 1.65;
  box-shadow: 0 8px 24px rgba(0,0,0,.16); }
.subtitle__voice { display: flex; align-items: center; gap: 5px; flex-shrink: 0; color: #83d7d1; font-size: 11px; font-weight: 700; }
.subtitle__voice::after { content: ''; width: 1px; height: 20px; margin-left: 7px; background: rgba(255,255,255,.14); }
.subtitle__text { flex: 1; }
.subtitle svg { font-size: 18px; color: #6f8da8; flex-shrink: 0; }
.subtitle svg.talking { color: #4ade80; animation: pulse .8s ease-in-out infinite; }
@keyframes pulse { 50% { transform: scale(1.25); color: #22d3ee; } }

/* 控制条 */
.controls { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 14px 18px; background: var(--zy-bg-surface); }
.ctrl-dots { display: flex; gap: 7px; flex: 1; }
.cdot { width: 22px; height: 5px; border-radius: 3px; background: #e2e8f0; cursor: pointer; transition: all .25s; }
.cdot.on { background: #7c3aed; width: 34px; }
.cdot.done { background: #c4b5fd; }
.ctrl-btns { display: flex; align-items: center; gap: 8px; }
.ctrl-right { flex: 1; display: flex; justify-content: flex-end; align-items: center; gap: 12px; }
.rate-btn { min-width: 48px; height: 30px; border: 1px solid var(--zy-border-strong); border-radius: 999px;
  background: var(--zy-bg-surface); color: var(--zy-text-secondary); font-size: 12.5px; font-weight: 700; cursor: pointer;
  font-family: 'SFMono-Regular', Consolas, monospace; transition: all .2s ease; }
.rate-btn:hover { color: #7c3aed; border-color: #7c3aed; background: #f5f3ff; }
.tts-tip { color: #94a3b8; font-size: 12px; padding: 0 18px 12px; margin: 0; }

/* 全屏模式 */
.player.fs { border: none; border-radius: 0; display: flex; flex-direction: column;
  background: #0b1120; height: 100vh; }
.player.fs .stage { flex: 1; height: auto; min-height: 0; }
.player.fs .controls { background: #0f172a; }
.player.fs .ctrl-dots .cdot { background: #334155; }
.player.fs .ctrl-dots .cdot.on { background: #7c3aed; }
.player.fs .ctrl-dots .cdot.done { background: #6d28d9; }
.player.fs .tts-tip { display: none; }

.scene-enter-active, .scene-leave-active { transition: all .4s ease; }
.scene-enter-from { opacity: 0; transform: translateX(30px); }
.scene-leave-to { opacity: 0; transform: translateX(-30px); }

/* 追问面板 */
.followup { margin-top: 18px; border: 1px solid var(--zy-border-default); border-radius: 14px; padding: 16px 18px; background: var(--zy-bg-surface); }
.fu-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.fu-title { display: flex; align-items: center; gap: 6px; font-weight: 700; color: var(--zy-text-primary); font-size: 14.5px; }
.fu-title svg { color: #7c3aed; font-size: 18px; }
.fu-msgs { display: flex; flex-direction: column; gap: 10px; max-height: 340px; overflow-y: auto; margin-bottom: 12px; }
.fu-msg { display: flex; }
.fu-msg.user { justify-content: flex-end; }
.fu-bubble { max-width: 82%; padding: 9px 14px; border-radius: 12px; font-size: 13.5px; line-height: 1.7; }
.fu-msg.user .fu-bubble { background: #7c3aed; color: #fff; border-bottom-right-radius: 4px; }
.fu-msg.assistant .fu-bubble { background: #f1f5f9; color: #334155; border-bottom-left-radius: 4px; }
.fu-typing { color: #94a3b8; }
.fu-input { display: flex; gap: 10px; }
.fu-quick { display: flex; gap: 8px; flex-wrap: wrap; margin-top: 10px; }
.fu-q { cursor: pointer; }
.fu-bubble.markdown-body :deep(pre) { background: #0f172a; color: #e2e8f0; padding: 10px; border-radius: 8px; overflow-x: auto; }
.fu-bubble.markdown-body :deep(code) { background: #e2e8f0; padding: 1px 5px; border-radius: 4px; font-size: 12.5px; }
.fu-bubble.markdown-body :deep(pre code) { background: none; padding: 0; }
.fu-bubble.markdown-body :deep(p) { margin: 4px 0; }
.fu-bubble.markdown-body :deep(ul), .fu-bubble.markdown-body :deep(ol) { padding-left: 18px; margin: 4px 0; }

@media screen and (max-width: 768px) {
  .ex-page { padding: 18px 16px; border-radius: 14px; }
  .ex-head__l { align-items: flex-start; }
  .head-mark { width: 42px; height: 42px; font-size: 23px; }
  .ex-input { flex-direction: column; }
  .in-sub, .in-topic { max-width: 100%; }
  .mic-btn { align-self: flex-start; }
  .stage { padding: 20px 20px 82px; min-height: 420px; }
  .scene__title { font-size: 20px; }
  .big-text { font-size: 22px; }
  .vis-bullets li { font-size: 16px; }
  .stage__kp { display: none; }
  .subtitle { margin: 0 12px; align-items: flex-start; gap: 8px; padding: 9px 11px; font-size: 13px; }
  .subtitle__voice span, .subtitle__voice::after { display: none; }
  .controls { flex-wrap: wrap; justify-content: center; }
  .ctrl-dots { flex-basis: 100%; justify-content: center; }
  .ctrl-right { flex: initial; }
  .quiz-q { font-size: 16px; }
  .quiz-opt { font-size: 14px; padding: 10px 12px; }
  .code-line { font-size: 12px; }
}
</style>
