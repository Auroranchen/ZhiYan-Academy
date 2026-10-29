<!-- 视频讲解组件 -->
<template>
  <div class="ai-explain-page">
    <!-- 顶部导航 -->
    <div class="page-header">
      <div class="header-left">
        <el-button link @click="$router.back()">
          <Icon icon="ph:arrow-left-bold" width="20" />
          返回
        </el-button>
        <div class="homework-info" v-if="homeworkInfo">
          <h2 class="title">{{ homeworkInfo.homeworktitle || '作业详情' }}</h2>
          <div class="meta">
            <el-tag :type="getScoreType(homeworkInfo.score)">得分: {{ homeworkInfo.score ?? '-' }}</el-tag>
            <span class="time">生成时间: {{ formatDate(new Date()) }}</span>
          </div>
        </div>
      </div>
      <div class="header-right">
        <el-radio-group v-model="viewMode" size="small" style="margin-right: 15px;">
          <el-radio-button value="text">
            <Icon icon="ph:article-duotone" style="vertical-align: text-bottom; margin-right: 4px;" />
            图文讲解
          </el-radio-button>
          <el-radio-button value="video">
            <Icon icon="ph:film-strip-duotone" style="vertical-align: text-bottom; margin-right: 4px;" />
            视频讲解
          </el-radio-button>
        </el-radio-group>
        <div class="ai-teacher-status">
          <Icon icon="ph:robot-duotone" width="24" class="teacher-icon" />
          <span>{{ loading ? 'AI 正在分析中...' : 'AI 讲解完成' }}</span>
        </div>
      </div>
    </div>

    <!-- 视频模式 -->
    <div v-if="viewMode === 'video'" class="video-container">
      <div class="video-placeholder" v-if="!videoGenerated">
        <div class="video-player-mock start-screen">
          <div class="play-btn-wrapper" v-if="!videoGenerating" @click="startVideoGeneration">
            <div class="play-icon-bg">
              <Icon icon="ph:play-fill" width="48" color="#fff" />
            </div>
            <div class="start-text">开始 AI 视频讲解</div>
            <div class="start-subtext">基于题目内容自动生成 · 沉浸式学习体验</div>
          </div>
          <div class="generating-state" v-else>
            <div class="loading-ring">
              <div></div><div></div><div></div><div></div>
            </div>
            <h3>AI 老师正在备课中...</h3>
            <p>正在生成语音脚本与板书动画</p>
          </div>
        </div>
      </div>
      <div v-else class="video-result">
         <div class="video-player-mock modern-player">
             <!-- 播放器内容层 -->
             <div v-if="isPlayingVideo" class="player-content">
                <!-- 技能特效层 -->
                <div v-if="showSkillEffect" class="skill-effect-layer" :class="currentEffect">
                  <div class="particle-burst"></div>
                </div>

                <!-- 顶部状态栏 -->
                <div class="player-top-bar">
                   <div class="lesson-tag">第 {{ currentPlayingIndex + 1 }} 题讲解</div>
                   <div class="teacher-avatar">
                      <span class="avatar-img">👩‍🏫</span>
                      <span class="avatar-name">AI 老师</span>
                   </div>
                </div>

                <!-- 核心演示区 -->
                <div class="stage-area">
                    <!-- 左侧：题目内容区 -->
                    <div class="stage-left">
                        <!-- 题目卡片 -->
                        <div class="question-card-visual" :class="{ 'has-code': showCodePanel }">
                            <div class="visual-q-header">
                                <el-tag size="small" effect="dark" type="primary">{{ getQuestionTypeLabel(currentPlayingQuestion.questionType) }}</el-tag>
                            </div>
                            <div class="visual-title" v-safe-html="currentPlayingQuestion.questionTitle"></div>
                            <!-- 选项展示 (选择题) -->
                            <div v-if="['single', 'multi'].includes(currentPlayingQuestion.questionType)" class="visual-options">
                                <div v-for="(opt, idx) in currentPlayingQuestion.options" :key="idx" 
                                     class="visual-opt-item"
                                     :class="{ 'correct': isOptionCorrect(idx, currentPlayingQuestion) && showAnswerReveal }">
                                    <span class="opt-label">{{ ['A','B','C','D'][idx] }}</span>
                                    <span class="opt-text">{{ opt }}</span>
                                    <Icon v-if="isOptionCorrect(idx, currentPlayingQuestion) && showAnswerReveal" icon="ph:check-bold" class="opt-icon check" />
                                </div>
                            </div>
                            <!-- 判断题展示 -->
                            <div v-else-if="currentPlayingQuestion.questionType === 'judge'" class="visual-judge">
                                <div class="judge-item" :class="{ 'correct': currentPlayingQuestion.correctAnswer === 'true' && showAnswerReveal }">
                                    <Icon icon="ph:check-circle-bold" width="28" /> 正确
                                </div>
                                <div class="judge-item" :class="{ 'correct': currentPlayingQuestion.correctAnswer === 'false' && showAnswerReveal }">
                                    <Icon icon="ph:x-circle-bold" width="28" /> 错误
                                </div>
                            </div>
                        </div>

                        <!-- 讲解信息面板（始终显示核心知识点） -->
                        <div class="explain-panel">
                            <!-- 核心知识点（每道题始终显示） -->
                            <div class="explain-item keypoint-item">
                                <div class="explain-label"><Icon icon="ph:tag-duotone" /> 核心知识点</div>
                                <div class="explain-text">{{ currentPlayingQuestion.keyPoints || generateKeyPoints(currentPlayingQuestion) }}</div>
                            </div>
                            <!-- 解题步骤 -->
                            <div class="explain-item" v-if="currentVisualPhase === 'steps' && currentPlayingQuestion.steps">
                                <div class="explain-label"><Icon icon="ph:list-numbers-duotone" /> 解题思路</div>
                                <div class="explain-steps">
                                    <div v-for="(step, sIdx) in currentPlayingQuestion.steps" :key="sIdx" class="explain-step">
                                        <span class="step-num">{{ sIdx + 1 }}</span>
                                        <span>{{ stripHtml(step) }}</span>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <!-- 代码面板（条件显示） -->
                        <CodePanel :code="referenceCode" :language="codeLanguage" :visible="showCodePanel && isPlayingVideo" />
                    </div>

                    <!-- 右侧：动画老师 -->
                    <div class="stage-right">
                        <AnimatedTeacher :action="currentTeacherAction" :visible="isPlayingVideo" :speaking="isSpeaking" />
                        <div class="teacher-label">AI 老师</div>
                    </div>
                </div>

                <!-- 字幕条（正常流，不遮挡内容） -->
                <div class="subtitle-bar-row" v-show="isPlayingVideo && displayBuffer">
                    <div class="subtitle-pill">{{ displayBuffer }}</div>
                </div>
                <div class="subtitle-bar-row subtitle-placeholder" v-show="!displayBuffer || !isPlayingVideo"></div>
                
                <!-- 底部控制栏 -->
                <div class="video-controls-modern">
                    <div class="progress-area">
                       <span class="time-text">{{ formatTime(currentTime) }}</span>
                       <div class="progress-track" @click.stop="seekToPosition($event)" ref="progressTrackRef">
                          <div class="progress-fill" :style="{ width: progressPercent + '%' }"></div>
                          <!-- 题目分段标记 -->
                          <div v-for="(mark, idx) in questionMarks" :key="idx"
                               class="progress-mark"
                               :style="{ left: mark.percent + '%' }"
                               :title="'\u7B2C ' + (idx + 1) + ' \u9898'">
                          </div>
                          <!-- 拖动手柄 -->
                          <div class="progress-thumb" :style="{ left: progressPercent + '%' }"></div>
                       </div>
                       <span class="time-text">{{ formatTime(totalDuration) }}</span>
                    </div>
                    <div class="btn-area">
                        <button class="ctrl-btn sub" @click="stopVideoPlay"><Icon icon="ph:stop-circle" width="24"/></button>
                        <button class="ctrl-btn main" @click="toggleVideoPlay">
                            <Icon :icon="videoPaused ? 'ph:play-fill' : 'ph:pause-fill'" width="32"/>
                        </button>
                        <button class="ctrl-btn sub" @click="skipCurrent"><Icon icon="ph:skip-forward-fill" width="24"/></button>
                    </div>
                </div>
             </div>

             <!-- 播放结束/待播放 -->
             <div v-else class="end-screen">
                 <div class="end-content">
                     <div class="end-icon">{{ hasPlayed ? '🎉' : '🎬' }}</div>
                     <h3>{{ hasPlayed ? '本次讲解已结束' : 'AI 讲解视频已就绪' }}</h3>
                     <p v-if="hasPlayed">共学习 {{ questionList.length }} 道题目</p>
                     <p v-else>点击下方按钮开始播放</p>
                     <div class="end-actions">
                        <el-button type="primary" round size="large" @click="startVideoPlay">
                          <Icon :icon="hasPlayed ? 'ph:arrow-counter-clockwise-bold' : 'ph:play-fill'" style="margin-right: 6px"/> 
                          {{ hasPlayed ? '再听一遍' : '开始播放' }}
                        </el-button>
                        <el-button round size="large" @click="viewMode = 'text'">
                          <Icon icon="ph:list-bullets-bold" style="margin-right: 6px"/> 查看图文
                        </el-button>
                     </div>
                 </div>
             </div>
         </div>
      </div>
    </div>

    <!-- 主要内容区 (图文模式) -->
    <div v-else class="content-container" v-loading="loading" element-loading-text="AI老师正在生成讲解...">
      <div v-if="!loading && !error && questionList.length > 0" class="assistant-policy-banner">
        <el-alert type="info" show-icon :closable="false" title="AI 助学助手使用说明">
          <template #default>
            <div class="policy-lines">
              <p><strong>已完成作答的题目</strong>：可对照<b>本题答案</b>请求 AI 进行错因分析、解题步骤与拓展练习，助你彻底弄懂这道题。</p>
              <p><strong>未完成或未提交本题时</strong>：系统启用「题库与学习诚信」策略，AI <b>不提供</b>可直接照抄的答案、对错结论或本题标准解；仅供知识域梳理、学法建议与自拟自查题，请先独立思考后再作答。</p>
            </div>
          </template>
        </el-alert>
      </div>

      <!-- 异常处理 -->
      <div v-if="error" class="error-state">
        <el-empty description="AI 服务暂时不可用">
          <el-button type="primary" @click="fetchData">重新生成</el-button>
        </el-empty>
      </div>

      <!-- 题目列表 -->
      <div v-else class="question-list">
        <div 
          v-for="(q, index) in questionList" 
          :key="q.questionId || index" 
          class="question-card"
          :class="{ 'is-expanded': q.expanded }"
        >
          <!-- 卡片头部 (点击展开/收起) -->
          <div class="card-header" @click="toggleExpand(index)">
            <div class="q-meta">
              <span class="q-index">第 {{ index + 1 }} 题</span>
              <el-tag size="small" effect="plain">{{ getQuestionTypeLabel(q.questionType) }}</el-tag>
              <span class="q-score" :class="{ 'score-full': isFullScore(q) }">
                得分: {{ getStudentScore(q) }}/{{ q.score }}
              </span>
            </div>
            <div class="q-title-preview" :title="stripHtml(q.questionTitle)">{{ stripHtml(q.questionTitle) }}</div>
            <Icon :icon="q.expanded ? 'ph:caret-up-bold' : 'ph:caret-down-bold'" class="expand-icon" />
          </div>

          <!-- 展开内容 -->
          <el-collapse-transition>
            <div v-if="q.expanded" class="card-body">
              <!-- 题目原题与答案 -->
              <div class="original-section">
                <div class="section-title">
                  <Icon icon="ph:file-text-duotone" class="section-icon"/> 题目详情
                </div>
                <div class="q-content" v-safe-html="q.questionTitle"></div>
                <div class="q-options" v-if="q.options && q.options.length">
                   <div v-for="(opt, idx) in q.options" :key="idx" class="option-item"
                        :class="{
                          'opt-correct': isOptionCorrect(idx, q),
                          'opt-wrong': isOptionWrong(idx, q)
                        }">
                     <span class="option-label" :class="{ 'label-correct': isOptionCorrect(idx, q), 'label-wrong': isOptionWrong(idx, q) }">{{ ['A','B','C','D'][idx] }}</span>
                     <span>{{ opt }}</span>
                     <Icon v-if="isOptionCorrect(idx, q)" icon="ph:check-bold" style="margin-left:auto; color:#67C23A;" />
                     <Icon v-if="isOptionWrong(idx, q)" icon="ph:x-bold" style="margin-left:auto; color:#F56C6C;" />
                   </div>
                </div>
                <div class="answer-comparison">
                  <!-- 编程题：显示代码提交 -->
                  <template v-if="isCodeQuestion(q)">
                    <div class="code-submission-box">
                      <div class="code-submission-header">
                        <Icon icon="ph:code-duotone" class="code-icon" />
                        <span>你的提交</span>
                      </div>
                      <div class="code-submission-body">
                        <pre class="code-submission-pre"><code>{{ q.studentAnswer || '未提交' }}</code></pre>
                      </div>
                    </div>
                    <div class="ans-box correct-ans" style="margin-top: 8px;" v-if="correctnessKnown(q)">
                      <span class="label"><Icon icon="ph:arrow-down-bold" style="vertical-align: text-bottom; margin-right: 4px;" />正确答案：</span>
                      <span class="value">参见下方参考代码</span>
                    </div>
                    <el-alert v-else type="warning" show-icon :closable="false" title="题库保护模式下不展示本题参考代码结论" />
                  </template>
                  <!-- 普通题：正常答案对比 -->
                  <template v-else>
                    <div class="ans-box student-ans" :class="{ 'is-wrong': correctnessKnown(q) && !isCorrect(q) }">
                      <span class="label">你的答案：</span>
                      <span class="value">{{ formatAnswer(q.studentAnswer, q.questionType) }}</span>
                      <Icon v-if="correctnessKnown(q) && !isCorrect(q)" icon="ph:x-circle-bold" class="status-icon wrong" />
                      <Icon v-if="correctnessKnown(q) && isCorrect(q)" icon="ph:check-circle-bold" class="status-icon correct" />
                    </div>
                    <div class="ans-box correct-ans" v-if="correctnessKnown(q)">
                      <span class="label">正确答案：</span>
                      <span class="value">{{ formatAnswer(q.correctAnswer, q.questionType) }}</span>
                    </div>
                    <el-alert v-if="isExplainRestricted(q)" type="warning" show-icon :closable="false" class="masked-answer-tip" description="当前为题库/诚信保护模式：在您有效完成本题作答前，不提供可直接照抄的正确答案结论；可先结合下方 AI 的知识要点与学习路径自检。" />
                  </template>
                </div>
              </div>

              <!-- AI 讲解区域 -->
              <div class="ai-section">
                <div class="ai-header">
                  <div class="ai-title-row">
                    <div class="ai-title">
                      <div class="ai-avatar">
                        <Icon icon="ph:magic-wand-fill" />
                      </div>
                      <span class="text">AI 深度讲解</span>
                    </div>
                    <div class="ai-actions">
                    <el-tooltip content="语音播报" placement="top" v-if="voiceReady || synth">
                      <el-button size="small" circle @click.stop="playVoice(q)" :type="q.isPlaying ? 'primary' : ''">
                        <Icon :icon="q.isPlaying ? 'ph:pause-circle-bold' : 'ph:speaker-high-bold'" />
                      </el-button>
                    </el-tooltip>
                    <el-tooltip content="收藏题目" placement="top">
                      <el-button size="small" circle @click.stop="toggleFavorite(q)" :type="q.isFavorite ? 'warning' : ''">
                        <Icon :icon="q.isFavorite ? 'ph:star-fill' : 'ph:star-bold'" />
                      </el-button>
                    </el-tooltip>
                    </div>
                  </div>
                  <div v-if="isExplainRestricted(q)" class="ai-policy-inline">
                    <Icon icon="ph:shield-check-duotone" /> 题库保护模式：本节仅补充知识方法与自查引导，不包含本题可直接照抄的结论。
                  </div>
                </div>

                <div class="ai-content-wrapper">
                  <!-- 关键知识点 -->
                  <div class="ai-block key-points">
                    <span class="block-label"><Icon icon="ph:tag-duotone" /> 关键知识点</span>
                    <div class="tags-wrapper">
                      <el-tag type="primary" effect="light" round size="large">{{ q.keyPoints || generateKeyPoints(q) }}</el-tag>
                    </div>
                  </div>

                  <!-- 错因分析 (仅错误时显示) -->
                  <div v-if="correctnessKnown(q) && !isCorrect(q)" class="ai-block error-analysis">
                    <span class="block-label error-label"><Icon icon="ph:warning-circle-duotone" /> 错因分析</span>
                    <div class="analysis-box">
                      {{ q.errorAnalysis || '暂无分析' }}
                    </div>
                  </div>

                  <!-- 解题思路 -->
                  <div class="ai-block steps">
                    <span class="block-label"><Icon icon="ph:list-numbers-duotone" /> 解题思路</span>
                    <div class="steps-container">
                      <div v-for="(step, sIdx) in (q.steps || [])" :key="sIdx" class="step-item">
                        <div class="step-index">{{ sIdx + 1 }}</div>
                        <div class="step-content">{{ step }}</div>
                      </div>
                    </div>
                  </div>

                  <!-- 参考代码（算法/数据结构题） -->
                  <div class="ai-block reference-code" v-if="q.referenceCode">
                    <span class="block-label"><Icon icon="ph:code-duotone" /> 参考代码</span>
                    <CodePanel :code="q.referenceCode" :language="codeLanguage" :visible="true" />
                  </div>

                  <!-- 常见错误 -->
                  <div class="ai-block common-mistakes" v-if="q.commonMistakes && q.commonMistakes.length">
                    <span class="block-label"><Icon icon="ph:warning-diamond-duotone" /> 常见错误与易混淆点</span>
                    <div class="mistakes-list">
                      <div v-for="(mistake, mIdx) in q.commonMistakes" :key="mIdx" class="mistake-item">
                        <Icon icon="ph:x-circle" class="mistake-icon" />
                        <span>{{ mistake }}</span>
                      </div>
                    </div>
                  </div>

                  <!-- 举一反三 -->
                  <div class="ai-block expansion" v-if="q.expansion">
                    <div class="expansion-card">
                      <div class="exp-header">
                        <span class="exp-title"><Icon icon="ph:lightbulb-duotone" /> 举一反三</span>
                        <el-button link type="primary" size="small" @click="q.showExpAns = !q.showExpAns">
                          {{ q.showExpAns ? '收起解析' : '查看解析' }}
                          <Icon :icon="q.showExpAns ? 'ph:caret-up' : 'ph:caret-down'" />
                        </el-button>
                      </div>
                      <div class="exp-content">
                        <div class="exp-q"><strong>问：</strong>{{ q.expansion.question }}</div>
                        <el-collapse-transition>
                          <div class="exp-ans-box" v-show="q.showExpAns">
                            <div class="exp-ans"><strong>答：</strong>{{ q.expansion.answer }}</div>
                            <div class="exp-analysis"><strong>析：</strong>{{ q.expansion.analysis }}</div>
                          </div>
                        </el-collapse-transition>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </el-collapse-transition>
        </div>
      </div>
    </div>

    <!-- 底部声明 -->
    <div class="page-footer">
      <Icon icon="ph:info" style="vertical-align: middle; margin-right: 4px;" />
      内容由 AI 自动生成，仅供学习参考
    </div>
  </div>
</template>

<script setup>
import { ref, watch, onMounted, onUnmounted, computed } from 'vue'
import { useRoute, onBeforeRouteLeave } from 'vue-router'
import { Icon } from '@iconify/vue'
import { favoriteExplain, getHomeworkExplain } from '@/api/ai'
import { getHomework, getStudentHomework } from '@/api/homework'
import { ElMessage } from 'element-plus'
import { createVoiceEngine } from '@/utils/voiceEngine'
import { createEmotionController } from '@/utils/emotionController'
import { createSpeechTemplates } from '@/utils/speechTemplates'
import { mapCourseToLanguage } from '@/utils/languageMapper'
import { getTeacherAction, shouldShowCode } from '@/utils/playerHelpers'
import skillsConfig from '@/config/skills_system.json'
import {
  correctnessKnown,
  formatAnswer,
  generateCodeExplanation,
  generateKeyPoints,
  getQuestionTypeLabel,
  getScoreType,
  getStudentScore,
  isCodeQuestion,
  isCorrect,
  isExplainRestricted,
  isFullScore,
  isOptionCorrect,
  isOptionWrong,
  stripHtml
} from '@/utils/explain'
import { formatDate } from '@/utils/format'
import AnimatedTeacher from '@/components/AnimatedTeacher.vue'
import CodePanel from '@/components/CodePanel.vue'

const route = useRoute()
const studentHomeworkId = route.query.id

const loading = ref(true)
const error = ref(false)
const questionList = ref([])
const homeworkInfo = ref(null)
const viewMode = ref('text') // text | video
const videoGenerating = ref(false)
const videoGenerated = ref(false)
const isPlayingVideo = ref(false)
const hasPlayed = ref(false)
const videoPaused = ref(false)
const currentPlayingIndex = ref(0)
const currentPlayingQuestion = ref({})
let videoTimer = null
let typingTimer = null

const currentVisualPhase = ref('intro') // 视觉阶段：intro, question, keypoint, analysis, steps, transition

// 答案揭示控制（老师讲完后揭晓答案时才标注正确选项）
const showAnswerReveal = computed(() => {
  return ['answer_reveal', 'transition', 'code_explain'].includes(currentVisualPhase.value)
})

// 升级播放器的新状态
const currentTeacherAction = ref('idle')
const isSpeaking = ref(false) // 语音是否正在播放（驱动口型动画）
const referenceCode = ref('')
const codeLanguage = ref('plaintext')
const showCodePanel = ref(false)
const courseName = ref('')

// 技能系统状态
const showSkillEffect = ref(false)
const currentEffect = ref('')

const skipCurrent = () => {
    if (isSeeking) return
    isSeeking = true
    playGeneration++
    if (typingTimer) clearTimeout(typingTimer)
    if (videoTimer) clearInterval(videoTimer)
    displayBuffer.value = ''
    currentPlayingIndex.value++
    if (currentPlayingIndex.value < questionDurations.value.length) {
      currentTime.value = questionDurations.value[currentPlayingIndex.value].startTime
      const nextQ = questionList.value[currentPlayingIndex.value]
      if (nextQ) {
        currentPlayingQuestion.value = nextQ
        currentVisualPhase.value = 'intro'
        referenceCode.value = nextQ.referenceCode || ''
        showCodePanel.value = false
      }
    }
    synth.cancel()
    setTimeout(() => {
      const warmup = new SpeechSynthesisUtterance('')
      warmup.volume = 0
      warmup.onend = () => {
        isSeeking = false
        if (!videoPaused.value) {
          playNextQuestion()
        }
      }
      warmup.onerror = () => {
        isSeeking = false
        if (!videoPaused.value) {
          playNextQuestion()
        }
      }
      synth.speak(warmup)
      setTimeout(() => {
        if (isSeeking) {
          isSeeking = false
          if (!videoPaused.value) {
            playNextQuestion()
          }
        }
      }, 400)
    }, 50)
}

const startVideoGeneration = () => {
  // 分镜数据已由后端生成，点击后直接启动本地动画与语音播放。
  unlockSpeech()
  videoGenerating.value = false
  videoGenerated.value = true
  hasPlayed.value = false
  startVideoPlay()
}

const startVideoPlay = () => {
  if (questionList.value.length === 0) {
    ElMessage.warning('暂无题目可播放')
    return
  }
  isPlayingVideo.value = true
  hasPlayed.value = true
  videoPaused.value = false
  currentPlayingIndex.value = 0
  
  // 估算总时长
  calculateTotalDuration()
  currentTime.value = 0
  
  playNextQuestion()
}

// 每道题的预估时长（秒）和累计起始时间
const questionDurations = ref([])   // [{ duration, startTime }]

// 估算单道题的语音时长（秒）— 与实际 speech 构建逻辑保持一致
const estimateQuestionDuration = (q) => {
  let totalChars = 0
  const cleanTitle = (q.questionTitle || '').replace(/<[^>]+>/g, '')
  totalChars += cleanTitle.length + 10 // 开场白 + 题目
  totalChars += (q.keyPoints || '').length + 10 // 知识点
  if (!isCorrect(q) && q.errorAnalysis) {
    totalChars += q.errorAnalysis.length + 10
  }
  // 只计算前2步（与播放逻辑一致）
  if (q.steps) {
    const maxSteps = Math.min(q.steps.length, 2)
    for (let i = 0; i < maxSteps; i++) {
      totalChars += q.steps[i].length + 8
    }
  }
  totalChars += 15 // 鼓励/安慰 + 过渡
  // 代码讲解阶段（有参考代码时额外增加时长）
  if (q.referenceCode && q.referenceCode.trim()) {
    totalChars += 80 // 代码讲解约80字
  }
  // 中文语音约 0.28s/字（rate=1.0），加上段间停顿
  return Math.ceil(totalChars * 0.28) + 3
}

// 估算视频总时长，并构建每题时间偏移表
const calculateTotalDuration = () => {
  const durations = []
  let cumulative = 0
  questionList.value.forEach((q) => {
    const dur = estimateQuestionDuration(q)
    durations.push({ duration: dur, startTime: cumulative })
    cumulative += dur
  })
  questionDurations.value = durations
  totalDuration.value = cumulative
}

const playNextQuestion = (offsetSeconds = 0) => {
  if (videoPaused.value || isSeeking) return
  if (currentPlayingIndex.value >= questionList.value.length) {
    stopVideoPlay()
    return
  }

  // 递增播放代数，使之前的闭包失效
  playGeneration++
  const thisGeneration = playGeneration

  const q = questionList.value[currentPlayingIndex.value]
  currentPlayingQuestion.value = q
  
  // 重置动画状态
  displayBuffer.value = ''
  currentVisualPhase.value = 'intro'
  currentTeacherAction.value = 'idle'
  
  // 代码面板初始隐藏，等到代码讲解阶段再显示
  referenceCode.value = q.referenceCode || ''
  showCodePanel.value = false
  
  // 触发场景切换特效
  triggerSkill('teleport')

  const cleanTitle = stripHtml(q.questionTitle)
  const idx = currentPlayingIndex.value + 1
  const total = questionList.value.length
  const correctKnownVid = correctnessKnown(q)
  const correct = correctKnownVid ? isCorrect(q) : null

  // 为新题目重置模板
  speechTemplates.reset()

  // 清理文本：去除重复句号，确保只有一个句号结尾
  const cleanText = (t) => {
    if (!t) return ''
    return t.replace(/\u3002{2,}/g, '\u3002').replace(/\.\u3002/g, '\u3002').replace(/\u3002\./g, '\u3002').trim()
  }

  // 将长文本按句号拆分为多条字幕，每条一句话
  const splitSentences = (text, segmentType) => {
    const cleaned = cleanText(text)
    // 按中文句号、感叹号、问号分割
    const parts = cleaned.split(/(?<=[\u3002\uFF01\uFF1F])/).map(s => s.trim()).filter(s => s.length > 0)
    if (parts.length === 0) return [{ text: cleaned, segmentType }]
    return parts.map(p => ({ text: p, segmentType }))
  }

  // 题目上下文（传递给话术模板，实现上下文感知）
  const questionCtx = {
    index: idx,
    total,
    questionType: q.questionType,
    isCorrect: correct === true,
    keyPoints: q.keyPoints || '',
    questionTitle: cleanTitle,
  }

  // 使用语音模板和情感控制器构建语音片段（只读重要内容，代码不朗读）
  const speeches = []

  // 1. 开场白 + 题目（上下文感知：根据题型、答对/答错、是否高频考点生成不同开场）
  const introText = speechTemplates.getIntro(questionCtx)
  speeches.push(...splitSentences(introText + cleanTitle + '\u3002', 'intro'))

  // 2. 关键知识点
  const keyPointText = stripHtml(q.keyPoints || '\u8FD9\u4E2A\u77E5\u8BC6\u70B9\u5F88\u91CD\u8981')
  const keypointLead = speechTemplates.getKeypointLead({ index: idx })
  speeches.push(...splitSentences(keypointLead + keyPointText + '\u3002', 'keypoint'))

  // 3. 解题步骤
  if (q.steps && q.steps.length > 0) {
    const maxSteps = Math.min(q.steps.length, 2)
    for (let sIdx = 0; sIdx < maxSteps; sIdx++) {
      const cleanStep = stripHtml(q.steps[sIdx])
      if (/[{}]|=>|function\s|#include|int\s+main|printf|cout|scanf/.test(cleanStep)) continue
      const stepLead = speechTemplates.getStepLead({ index: sIdx + 1, stepCount: q.steps.length })
      speeches.push(...splitSentences(stepLead + cleanStep + '\u3002', 'step'))
    }
  }

  // 3.5 错误分析（仅已知对错且答错）
  if (correct === false) {
    const errorLead = speechTemplates.getErrorLead(questionCtx)
    speeches.push(...splitSentences(errorLead, 'error_analysis'))
  }

  // 4-6. 答案揭晓 / 代码讲解（根据题目类型分流）
  const hasCode = q.referenceCode && q.referenceCode.trim()
  
  if (hasCode) {
    // 代码题：跳过答案揭晓，直接进入代码讲解
    const codeLeadPhrases = [
      '接下来我们看一下这道题的参考代码。',
      '下面老师给出正确的参考代码，请同学们仔细看。',
      '好，让我们来看看这道题应该怎么写代码。'
    ]
    speeches.push({ text: codeLeadPhrases[idx % codeLeadPhrases.length], segmentType: 'code_explain' })
    const codeExplain = generateCodeExplanation(q)
    if (codeExplain) {
      speeches.push(...splitSentences(codeExplain, 'code_explain'))
    }
    const askPhrases = [
      '看完代码，你理解了吗？',
      '同学们，这段代码的思路你掌握了吗？',
      '好好消化一下这段代码，有问题可以多看几遍。'
    ]
    speeches.push({ text: askPhrases[idx % askPhrases.length], segmentType: 'ask' })
  } else {
    // 非代码题：公布答案仅在已解除题库保护（已知正确答案）时
    if (!correctKnownVid || q.correctAnswer === null || q.correctAnswer === undefined || q.correctAnswer === '') {
      speeches.push(...splitSentences(
        '为保护学习与考核公平，在你提交并完成本题作答前，暂不公布这道题的标准结论。可先结合上面的知识要点与学习路径自查。',
        'answer_reveal'
      ))
    } else {
      const answerText = formatAnswer(q.correctAnswer, q.questionType)
      const answerPhrases = [
        '现在知道答案了吗？这道题的正确答案是' + answerText + '。',
        '好了，公布答案，正确答案是' + answerText + '。',
        '那么答案是什么呢？正确答案是' + answerText + '。'
      ]
      const answerPhrase = answerPhrases[idx % answerPhrases.length]
      speeches.push(...splitSentences(answerPhrase, 'answer_reveal'))
    }

    const askPhrases = [
      '你答对了吗？',
      '同学们，你们答对了吗？',
      '想想看，你的答案和老师一样吗？'
    ]
    speeches.push({ text: askPhrases[idx % askPhrases.length], segmentType: 'ask' })
  }

  // 6.5 根据答题情况给出鼓励或安慰（上下文感知）；未知对错时使用中性的诚信提示
  if (correct === true) {
    const encourageText = speechTemplates.getEncouragement(questionCtx)
    speeches.push(...splitSentences(encourageText, 'transition'))
  } else if (correct === false) {
    const comfortText = speechTemplates.getComfort(questionCtx)
    speeches.push(...splitSentences(comfortText, 'transition'))
  } else {
    speeches.push(...splitSentences('为保护公平，请先独立思考并提交本题；之后 AI 会给你完整的对错评析与详解。加油。', 'transition'))
  }

  // 7. 过渡到下一题
  const transitionText = speechTemplates.getTransition({ index: idx, total, isCorrect: correct === true })
  speeches.push(...splitSentences(transitionText, 'summary'))

  let speechIndex = 0

  // 如果有偏移秒数（从 seekToPosition 传入），跳过对应的语音片段
  if (offsetSeconds > 0) {
    let accumulated = 0
    for (let i = 0; i < speeches.length; i++) {
      const segDur = speeches[i].text.length * 0.28 + 0.5 // 每字0.28秒 + 段间停顿
      accumulated += segDur
      if (accumulated >= offsetSeconds) {
        speechIndex = i
        break
      }
    }
    // 更新视觉状态到跳转位置的阶段
    if (speechIndex > 0 && speechIndex < speeches.length) {
      const seg = speeches[speechIndex]
      if (seg.segmentType === 'code_explain') {
        currentVisualPhase.value = 'code_explain'
        if (referenceCode.value && referenceCode.value.trim()) {
          showCodePanel.value = true
        }
      } else if (seg.segmentType === 'answer_reveal' || seg.segmentType === 'ask') {
        currentVisualPhase.value = 'answer_reveal'
      } else if (seg.segmentType === 'keypoint') {
        currentVisualPhase.value = 'keypoint'
      } else if (seg.segmentType === 'step') {
        currentVisualPhase.value = 'steps'
      }
    }
  }
  
  const speakNext = () => {
    // 代数不匹配或正在跳转，直接退出
    if (thisGeneration !== playGeneration || isSeeking) return
    if (videoPaused.value) return
    if (speechIndex >= speeches.length) {
      currentPlayingIndex.value++
      // 更新时间到下一题起始
      if (currentPlayingIndex.value < questionDurations.value.length) {
        currentTime.value = questionDurations.value[currentPlayingIndex.value].startTime
      }
      setTimeout(playNextQuestion, 800)
      return
    }

    const segment = speeches[speechIndex]
    
    // 1. 更新视觉状态（更精细的阶段控制）
    if (segment.segmentType === 'intro') {
      currentVisualPhase.value = 'question'
    } else if (segment.segmentType === 'keypoint') {
      currentVisualPhase.value = 'keypoint'
    } else if (segment.segmentType === 'error_analysis') {
      currentVisualPhase.value = 'analysis'
    } else if (segment.segmentType === 'step') {
      currentVisualPhase.value = 'steps'
    } else if (segment.segmentType === 'answer_reveal') {
      currentVisualPhase.value = 'answer_reveal'
    } else if (segment.segmentType === 'ask') {
      // "你答对了吗？" — 保持答案高亮状态
      currentVisualPhase.value = 'answer_reveal'
    } else if (segment.segmentType === 'code_explain') {
      // 代码讲解阶段：显示代码面板
      currentVisualPhase.value = 'code_explain'
      if (referenceCode.value && referenceCode.value.trim()) {
        showCodePanel.value = true
      }
    } else {
      currentVisualPhase.value = 'transition'
    }
    currentTeacherAction.value = getTeacherAction(segment.segmentType, videoPaused.value)

    // 2. 获取情感参数和语音角色
    const params = emotionController.getParams(segment.segmentType)
    const voiceRole = emotionController.getVoiceRole(segment.segmentType)
    const pauseAfter = emotionController.getPauseAfter(segment.segmentType)

    // 3. 字幕同步：语音开始时立即显示完整句子（不再逐字）
    if (typingTimer) clearTimeout(typingTimer)
    const subtitleText = segment.text
    displayBuffer.value = subtitleText || ''

    // 4. 创建语音实例
    const u = new SpeechSynthesisUtterance(segment.text)
    u.lang = 'zh-CN'
    u.rate = params.rate
    u.pitch = params.pitch
    u.volume = params.volume

    // 5. Use VoiceEngine to select voice
    const selectedVoice = voiceEngine.getVoice(voiceRole)
    if (selectedVoice) {
      u.voice = selectedVoice
    }

    u.onstart = () => {
      isSpeaking.value = true
    }

    u.onend = () => {
      isSpeaking.value = false
      if (thisGeneration !== playGeneration || isSeeking) return
      speechIndex++
      setTimeout(speakNext, pauseAfter)
    }
    
    u.onerror = (e) => {
      isSpeaking.value = false
      if (thisGeneration !== playGeneration || isSeeking) return
      // 跳转导致的 interrupted 错误直接忽略
      if (e && e.error === 'interrupted') return
      if (e && (e.error === 'not-allowed' || e.error === 'audio-busy')) {
        videoPaused.value = true
        ElMessage.warning('浏览器阻止了语音播放，请点击播放按钮重试，并确认标签页未静音')
        return
      }
      speechIndex++
      speakNext()
    }

    _activeUtterance = u
    if (synth.paused) synth.resume()
    synth.speak(u)
  }
  
  speakNext()
  
  // 更新进度计时器
  if (videoTimer) clearInterval(videoTimer)
  videoTimer = setInterval(() => {
    if (!videoPaused.value) {
      currentTime.value += 0.1
      if (currentTime.value > totalDuration.value && isPlayingVideo.value) {
        totalDuration.value += 5
      }
    }
  }, 100)
}

const stopVideoPlay = () => {
  isSeeking = false
  playGeneration++
  isSpeaking.value = false
  isPlayingVideo.value = false
  videoPaused.value = false
  currentTeacherAction.value = 'idle'
  displayBuffer.value = ''
  synth.cancel()
  if (videoTimer) clearInterval(videoTimer)
  if (typingTimer) clearTimeout(typingTimer)
  currentTime.value = 0
}

const toggleVideoPlay = () => {
  if (videoPaused.value) {
    // 恢复播放
    videoPaused.value = false
    isSpeaking.value = true
    synth.resume()
    // 恢复进度计时器
    if (videoTimer) clearInterval(videoTimer)
    videoTimer = setInterval(() => {
      if (!videoPaused.value) {
        currentTime.value += 0.1
        if (currentTime.value > totalDuration.value && isPlayingVideo.value) {
          totalDuration.value += 5
        }
      }
    }, 100)
  } else {
    // 暂停播放
    videoPaused.value = true
    isSpeaking.value = false
    currentTeacherAction.value = 'idle'
    synth.pause()
    // 暂停时停止进度计时器
    if (videoTimer) clearInterval(videoTimer)
  }
}

const triggerSkill = (skillId) => {
  const skill = skillsConfig.skills.find(s => s.id === skillId)
  if (skill) {
    currentEffect.value = skill.effect
    showSkillEffect.value = true
    setTimeout(() => {
      showSkillEffect.value = false
    }, 1000)
  }
}

const displayBuffer = ref('') // 用于逐字显示的缓冲区
let playGeneration = 0 // 播放代数，用于跳转时使旧闭包失效
let isSeeking = false // 跳转锁，防止 cancel 触发的 onerror 干扰新播放

const totalDuration = ref(0)
const currentTime = ref(0)

const formatTime = (seconds) => {
  const m = Math.floor(seconds / 60)
  const s = Math.floor(seconds % 60)
  return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`
}

// 进度条相关
const progressTrackRef = ref(null)

// 进度百分比
const progressPercent = computed(() => {
  if (totalDuration.value <= 0) return 0
  return Math.min((currentTime.value / totalDuration.value) * 100, 100)
})

// 每道题在进度条上的标记位置（基于预估时长）
const questionMarks = computed(() => {
  if (questionDurations.value.length <= 1) return []
  const total = totalDuration.value
  if (total <= 0) return []
  return questionDurations.value.slice(1).map((d, idx) => ({
    percent: (d.startTime / total) * 100,
    index: idx + 1
  }))
})

// 点击进度条跳转（基于时间偏移表，支持题内跳转）
const seekToPosition = (event) => {
  if (!progressTrackRef.value || !isPlayingVideo.value) return

  const rect = progressTrackRef.value.getBoundingClientRect()
  const clickX = event.clientX - rect.left
  const percent = Math.max(0, Math.min(clickX / rect.width, 1))
  const targetTime = percent * totalDuration.value

  // 根据时间偏移表找到对应题目
  let targetIndex = 0
  for (let i = questionDurations.value.length - 1; i >= 0; i--) {
    if (targetTime >= questionDurations.value[i].startTime) {
      targetIndex = i
      break
    }
  }

  // 计算在目标题目内的时间偏移（秒），用于跳过前面的语音片段
  const questionStartTime = questionDurations.value[targetIndex].startTime
  const offsetInQuestion = targetTime - questionStartTime

  // 停止一切旧播放
  isSeeking = true
  playGeneration++
  if (typingTimer) clearTimeout(typingTimer)
  if (videoTimer) clearInterval(videoTimer)

  // 重置状态
  displayBuffer.value = ''
  videoPaused.value = false
  currentPlayingIndex.value = targetIndex
  currentTime.value = targetTime // 直接跳到点击的时间

  const targetQ = questionList.value[targetIndex]
  if (targetQ) {
    currentPlayingQuestion.value = targetQ
    currentVisualPhase.value = 'intro'
    currentTeacherAction.value = 'idle'
    referenceCode.value = targetQ.referenceCode || ''
    showCodePanel.value = false
  }

  // 重置语音引擎（Chrome 兼容：cancel 后先说一个空白片段唤醒引擎）
  synth.cancel()
  setTimeout(() => {
    // Chrome 需要一个 warmup utterance 来重置引擎状态
    const warmup = new SpeechSynthesisUtterance('')
    warmup.volume = 0
    warmup.onend = () => {
      isSeeking = false
      if (!videoPaused.value) {
        playNextQuestion(offsetInQuestion)
      }
    }
    warmup.onerror = () => {
      // warmup 失败也继续播放
      isSeeking = false
      if (!videoPaused.value) {
        playNextQuestion(offsetInQuestion)
      }
    }
    synth.speak(warmup)
    // 兜底：如果 warmup 300ms 内没触发回调，强制继续
    setTimeout(() => {
      if (isSeeking) {
        isSeeking = false
        if (!videoPaused.value) {
          playNextQuestion(offsetInQuestion)
        }
      }
    }, 400)
  }, 50)
}

// 语音合成
const synth = window.speechSynthesis
// Chrome 会回收没有外部引用的 utterance，导致 onend 不触发、播放卡在中间，
// 这里持有一份引用作为兜底，本身不需要读取
let _activeUtterance = null
let speechUnlocked = false

const unlockSpeech = () => {
  if (!synth || typeof SpeechSynthesisUtterance === 'undefined') {
    ElMessage.warning('当前浏览器不支持语音播报，请使用最新版 Chrome 或 Edge')
    return false
  }
  try {
    if (synth.paused) synth.resume()
    if (!speechUnlocked) {
      const warmup = new SpeechSynthesisUtterance(' ')
      warmup.lang = 'zh-CN'
      warmup.volume = 0
      synth.speak(warmup)
      speechUnlocked = true
    }
    return true
  } catch (e) {
    ElMessage.warning('语音引擎启动失败，请检查浏览器声音权限')
    return false
  }
}

// 智能语音模块
const voiceEngine = createVoiceEngine()
const emotionController = createEmotionController()
const speechTemplates = createSpeechTemplates()
const voiceReady = ref(false)

const fetchData = async () => {
  if (!studentHomeworkId) {
    ElMessage.error('参数错误：缺少作业ID')
    return
  }
  
  loading.value = true
  error.value = false
  
  try {
    // 1. 获取作业基本信息
    const detailRes = await getStudentHomework(studentHomeworkId)
    if (detailRes.code === 200) {
      homeworkInfo.value = detailRes.data
      // 提取课程名称用于语言映射
      if (homeworkInfo.value.coursename) {
        courseName.value = homeworkInfo.value.coursename
      }
    }

    // 2. If no coursename yet, try fetching from homework detail
    if (!courseName.value && homeworkInfo.value && homeworkInfo.value.homeworkid) {
      try {
        const hwRes = await getHomework(homeworkInfo.value.homeworkid)
        if (hwRes.code === 200 && hwRes.data && hwRes.data.coursename) {
          courseName.value = hwRes.data.coursename
        }
      } catch (e) {
        console.warn('Failed to fetch homework detail for coursename:', e)
      }
    }

    // 3. Compute code language and panel visibility
    codeLanguage.value = mapCourseToLanguage(courseName.value)
    const hwType = homeworkInfo.value ? (homeworkInfo.value.type || 0) : 0

    // 4. 获取讲解
    const res = await getHomeworkExplain(studentHomeworkId)
    
    if (res.code === 200 && res.data && res.data.length > 0) {
      // 尝试从作业原始内容中补充选项（旧缓存可能缺少 options/correctAnswer/studentAnswer）
      const originalQuestions = {}
      try {
        // homeworkcontent 是作业原始试题内容（含选项和正确答案）
        const rawContent = homeworkInfo.value && (homeworkInfo.value.homeworkcontent || homeworkInfo.value.content)
        if (rawContent && typeof rawContent === 'string' && rawContent.trim().startsWith('{') || (rawContent && rawContent.trim().startsWith('['))) {
          const parsed = JSON.parse(rawContent)
          const qArr = parsed.questions || (Array.isArray(parsed) ? parsed : [])
          qArr.forEach(oq => {
            if (oq.id) originalQuestions[oq.id] = oq
          })
        }
      } catch (e) {
        // 作业内容可能是 HTML 格式，非 JSON，忽略即可
      }

      questionList.value = res.data.map(q => {
        const orig = originalQuestions[q.questionId] || {}
        const restrictedRow = !!(q.explainRestricted || q.explainPolicy === 'restricted')
        let mergedCorrectAnswer = q.correctAnswer
        if (restrictedRow) {
          mergedCorrectAnswer = null
        } else if (mergedCorrectAnswer === undefined || mergedCorrectAnswer === null || mergedCorrectAnswer === '') {
          mergedCorrectAnswer = orig.answer !== undefined && orig.answer !== null ? orig.answer : mergedCorrectAnswer
        }
        return {
          ...q,
          explainRestricted: restrictedRow,
          explainPolicy: q.explainPolicy || (restrictedRow ? 'restricted' : 'full'),
          // 后端新版本会返回 options，旧缓存从原始作业内容补充
          options: q.options || orig.options || [],
          correctAnswer: mergedCorrectAnswer,
          studentAnswer: q.studentAnswer || '',
          // 确保每道题都有知识点（后端可能为空）
          keyPoints: q.keyPoints || generateKeyPoints(q),
          // 新增：参考代码和常见错误
          referenceCode: (q.referenceCode || '').replace(/\\n/g, '\n'),
          commonMistakes: q.commonMistakes || [],
          expanded: false,
          showExpAns: false,
          isPlaying: false
        }
      })
      // 检查是否有任何题目包含参考代码，据此决定代码面板可见性
      const hasAnyCode = questionList.value.some(q => q.referenceCode && q.referenceCode.trim())
      showCodePanel.value = shouldShowCode(hwType, codeLanguage.value, hasAnyCode)
      // 提取参考代码（如果有）
      if (showCodePanel.value) {
        const firstWithCode = questionList.value.find(q => q.referenceCode)
        if (firstWithCode) {
          referenceCode.value = firstWithCode.referenceCode
        }
      }
    } else {
      questionList.value = []
      error.value = true
      ElMessage.error(res.msg || 'AI 暂未生成有效讲解，请稍后重试')
    }

    // 默认展开第一个
    if (questionList.value.length > 0) {
      questionList.value[0].expanded = true
      // 数据已就绪，视频可直接播放（无需再走"生成"等待动画）
      videoGenerated.value = true
    }

  } catch (err) {
    console.error('AI讲解生成失败', err)
    questionList.value = []
    error.value = true
  } finally {
    loading.value = false
  }
}

const toggleExpand = (index) => {
  questionList.value[index].expanded = !questionList.value[index].expanded
}

// 语音播报 (图文模式)
const playVoice = (q) => {
  if (!unlockSpeech()) return
  if (q.isPlaying) {
    synth.cancel()
    q.isPlaying = false
    return
  }

  questionList.value.forEach(item => item.isPlaying = false)
  synth.cancel()

  const idx = questionList.value.indexOf(q) + 1
  const correct = correctnessKnown(q) ? isCorrect(q) : null

  // 使用语音模板构建片段
  const segments = []

  // 开场白 + 题目
  segments.push({
    text: speechTemplates.getIntro({ index: idx, total: questionList.value.length }) + stripHtml(q.questionTitle) + '\u3002',
    segmentType: 'intro'
  })

  // Keypoints
  segments.push({
    text: speechTemplates.getKeypointLead({ index: idx }) + stripHtml(q.keyPoints || '') + '\u3002',
    segmentType: 'keypoint'
  })

  // 错误分析
  if (correct !== null && !correct && q.errorAnalysis) {
    segments.push({
      text: speechTemplates.getErrorLead({ index: idx, isCorrect: false }) + stripHtml(q.errorAnalysis) + '\u3002',
      segmentType: 'error_analysis'
    })
  }

  // Steps
  if (q.steps && q.steps.length > 0) {
    q.steps.forEach((step, sIdx) => {
      segments.push({
        text: speechTemplates.getStepLead({ index: sIdx + 1, stepCount: q.steps.length }) + stripHtml(step) + '\u3002',
        segmentType: 'step'
      })
    })
  }

  // 鼓励或安慰（受限模式下不进行对错话术）
  const summaryText = correct === true
    ? speechTemplates.getEncouragement()
    : correct === false
      ? speechTemplates.getComfort()
      : '为保护学习诚信，在您提交并完成本题作答前，AI 仅提供知识方法与自查指引，不进行对错评判。请先独立思考完成题目。'

  segments.push({
    text: summaryText,
    segmentType: 'summary'
  })

  let segIdx = 0
  q.isPlaying = true

  const speakSegment = () => {
    if (segIdx >= segments.length || !q.isPlaying) {
      q.isPlaying = false
      return
    }

    const seg = segments[segIdx]
    const params = emotionController.getParams(seg.segmentType)
    const voiceRole = emotionController.getVoiceRole(seg.segmentType)
    const pauseAfter = emotionController.getPauseAfter(seg.segmentType)

    const u = new SpeechSynthesisUtterance(seg.text)
    u.lang = 'zh-CN'
    u.rate = params.rate
    u.pitch = params.pitch
    u.volume = params.volume

    const selectedVoice = voiceEngine.getVoice(voiceRole)
    if (selectedVoice) u.voice = selectedVoice

    u.onstart = () => {
      speechUnlocked = true
    }
    u.onend = () => {
      segIdx++
      setTimeout(speakSegment, pauseAfter)
    }
    u.onerror = (event) => {
      if (event?.error === 'not-allowed' || event?.error === 'audio-busy') {
        q.isPlaying = false
        ElMessage.warning('浏览器阻止了语音播放，请点击页面后重试并检查标签页是否静音')
        return
      }
      segIdx++
      speakSegment()
    }

    _activeUtterance = u
    if (synth.paused) synth.resume()
    synth.speak(u)
  }

  // Chrome 在 cancel() 后立即 speak() 偶发吞掉首段，留出一个事件循环间隔
  setTimeout(speakSegment, 80)
}

// 收藏
const toggleFavorite = async (q) => {
  if (q.isFavorite) return
  
  try {
    await favoriteExplain({
      studentId: homeworkInfo.value.studentid,
      homeworkId: homeworkInfo.value.homeworkid,
      questionId: q.questionId,
      questionContent: q.questionTitle,
      explainContent: JSON.stringify({
        keyPoints: q.keyPoints,
        analysis: q.errorAnalysis
      })
    })
    q.isFavorite = true
    ElMessage.success('收藏成功')
  } catch (e) {
    console.error('收藏失败', e)
    ElMessage.error('收藏失败，请稍后重试')
  }
}

onMounted(async () => {
  fetchData()
  // 初始化语音引擎
  try {
    await voiceEngine.init()
    voiceReady.value = voiceEngine.isReady()
  } catch (e) {
    console.warn('Voice engine init failed:', e)
  }
})

// 完整清理播放状态（离开页面 / 组件销毁时调用）
const cleanupPlayback = () => {
  playGeneration++
  isSeeking = false
  isSpeaking.value = false
  synth.cancel()
  if (videoTimer) { clearInterval(videoTimer); videoTimer = null }
  if (typingTimer) { clearTimeout(typingTimer); typingTimer = null }
  isPlayingVideo.value = false
  videoPaused.value = false
  videoGenerating.value = false
  hasPlayed.value = false
  currentTeacherAction.value = 'idle'
  displayBuffer.value = ''
  currentTime.value = 0
}

// SPA 路由离开时停止播放
onBeforeRouteLeave(() => {
  cleanupPlayback()
})

// 切换到图文模式时停止视频播放
watch(viewMode, (newVal) => {
  if (newVal === 'text' && isPlayingVideo.value) {
    stopVideoPlay()
  }
})

// 切换浏览器标签页时也停止播放
const handleVisibilityChange = () => {
  if (document.hidden && isPlayingVideo.value) {
    cleanupPlayback()
  }
}
document.addEventListener('visibilitychange', handleVisibilityChange)

onUnmounted(() => {
  cleanupPlayback()
  window.removeEventListener('beforeunload', handleBeforeUnload)
  document.removeEventListener('visibilitychange', handleVisibilityChange)
})

// 刷新页面时停止语音播放
const handleBeforeUnload = () => {
  synth.cancel()
}
window.addEventListener('beforeunload', handleBeforeUnload)
</script>

<style scoped src="./styles/ai-explain-homework.css"></style>
