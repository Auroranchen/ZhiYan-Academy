<template>
  <div class="agent-wb">
    <!-- 顶部：标题、课程与运行控制 -->
    <div class="wb-hero">
      <div class="wb-hero__row">
        <div class="wb-head__left">
          <div class="wb-badge"><Icon icon="ph:circles-three-plus-duotone" /></div>
          <div>
            <h2>{{ mode === 'teacher' ? '班级学习多智能体工作台' : '学习多智能体工作台' }}</h2>
            <p>12 个 AI 智能体协作，一键产出含 7 类资源与质量复审的个性化学习包</p>
          </div>
        </div>
        <div class="wb-hero__chips">
          <span class="hero-chip"><Icon icon="ph:cpu-duotone" /> 12 个智能体</span>
          <span class="hero-chip"><Icon icon="ph:stack-duotone" /> 7 类资源</span>
          <span class="hero-chip"><Icon icon="ph:shield-check-duotone" /> 防幻觉过滤</span>
        </div>
      </div>

      <div class="wb-controls">
        <el-select v-model="courseid" :placeholder="mode === 'teacher' ? '选择课程（必选）' : '全部课程（可选）'"
          clearable filterable class="wb-select">
          <el-option v-for="c in courses" :key="c.courseid" :label="c.coursename" :value="c.courseid" />
        </el-select>
        <el-select v-model="quizCount" class="wb-count">
          <el-option :value="3" label="练习 3 题" />
          <el-option :value="5" label="练习 5 题" />
          <el-option :value="8" label="练习 8 题" />
        </el-select>
        <el-button type="primary" size="large" :icon="MagicStick" :loading="running"
          :disabled="mode === 'teacher' && !courseid" @click="run">
          {{ running ? '智能体协作中' : '启动多智能体' }}
        </el-button>
        <el-button v-if="running" size="large" type="danger" plain :icon="CircleClose" @click="cancelRun">
          停止生成
        </el-button>
        <el-button size="large" :icon="FolderOpen" @click="openLibrary">资源库</el-button>
        <span v-if="started && (running || elapsedSeconds > 0)" class="wb-timing">
          <Icon icon="ph:timer-duotone" />
          <span v-if="running">已用 {{ elapsedLabel }}</span>
          <span v-else>耗时 {{ elapsedLabel }}</span>
          <template v-if="running && runningCount > 1">
            <span class="wb-timing__sep"></span>
            <Icon icon="ph:circles-three-duotone" />
            并行 {{ runningCount }} 路
          </template>
        </span>
      </div>
      <div v-if="presetFocusPoints.length" class="preset-focus">
        <span><Icon icon="ph:crosshair-duotone" /> 已带入学习效果评估重点</span>
        <el-tag v-for="point in presetFocusPoints" :key="point" size="small" type="warning" effect="light">
          {{ point }}
        </el-tag>
      </div>
    </div>

    <!-- 未开始时展示各智能体的分工介绍 -->
    <div v-if="!started && !pack.title" class="wb-intro">
      <div v-for="(a, i) in agents" :key="a.key" class="intro-card">
        <div class="intro-top">
          <div class="intro-ic"><Icon :icon="a.icon" /></div>
          <span class="intro-no">{{ String(i + 1).padStart(2, '0') }}</span>
        </div>
        <b>{{ a.name }}</b>
        <span>{{ agentIntro[a.key] }}</span>
      </div>
    </div>

    <!-- 流水线进度 -->
    <div v-if="started" class="pipeline">
      <div v-for="(a, i) in agents" :key="a.key" class="agent-node" :class="a.status">
        <div class="agent-node__line" v-if="i < agents.length - 1"></div>
        <div class="agent-node__ic">
          <Icon v-if="a.status === 'done'" icon="ph:check-bold" />
          <Icon v-else-if="a.status === 'error'" icon="ph:x-bold" />
          <Icon v-else-if="a.status === 'running'" icon="ph:spinner-gap-bold" class="spin" />
          <Icon v-else :icon="a.icon" />
        </div>
        <div class="agent-node__name">{{ a.name }}</div>
        <div class="agent-node__status">
          <span v-if="a.status === 'running'">运行中</span>
          <span v-else-if="a.status === 'done'">{{ a.elapsed }}s</span>
          <span v-else-if="a.status === 'error'">失败</span>
          <span v-else>待运行</span>
        </div>
      </div>
    </div>

    <!-- 运行日志 -->
    <div v-if="started" class="console">
      <div class="console__bar">
        <span class="dot r"></span><span class="dot y"></span><span class="dot g"></span>
        <span class="console__title">AGENT RUNTIME LOG</span>
      </div>
      <div ref="logRef" class="console__body">
        <div v-for="(l, i) in logLines" :key="i" class="log-line" :class="l.type">
          <span class="log-time">{{ l.time }}</span>
          <span class="log-text">{{ l.text }}</span>
        </div>
        <div v-if="running" class="log-line log-cursor"><span class="log-time">›</span><span class="caret"></span></div>
      </div>
    </div>

    <!-- 生成结果：资源包 -->
    <div v-if="pack.title" ref="packRef" class="pack">
      <div class="pack__hero">
        <div class="pack__tag">AI 个性化学习资源包</div>
        <h3 class="pack__title">{{ pack.title }}</h3>
        <div class="pack__meta">
          <span v-if="diagnose.level" class="chip">{{ diagnose.level }}</span>
          <span class="chip ghost">{{ pack.subject || '综合' }}</span>
          <span class="chip ghost">{{ (pack.weakPoints || []).length }} 个薄弱点</span>
          <span v-if="quality.score" class="chip quality">
            <Icon icon="ph:seal-check-duotone" /> 质量复审 {{ quality.score }} 分
          </span>
          <el-tooltip v-if="(pack.groundingSources || []).length"
            :content="pack.groundingSources.join('、')" placement="top">
            <span class="chip grounded">
              <Icon icon="ph:database-duotone" /> 课程知识库 {{ pack.groundingSources.length }} 份
            </span>
          </el-tooltip>
          <span v-if="(pack.citations || []).length" class="chip citation-chip">
            <Icon icon="ph:quotes-duotone" /> {{ pack.citations.length }} 个检索片段
          </span>
          <span class="chip warn">AI 生成内容，仅供参考</span>
        </div>
      </div>

      <div v-if="pack.overview" class="pack__overview">
        <div v-if="overviewTyping" class="typing">{{ typedOverview }}<span class="caret"></span></div>
        <div v-else class="markdown-body" v-safe-html="md(pack.overview)"></div>
      </div>

      <!-- 学情诊断 -->
      <section class="pack__sec">
        <div class="sec-h"><span class="sec-no">01</span> 学情诊断<small> · 诊断智能体</small></div>
        <p class="diag-summary">{{ diagnose.summary }}</p>
        <div v-if="(diagnose.strengths || []).length" class="strength-row">
          <span class="strength-label"><Icon icon="ph:trophy-duotone" /> 优势项</span>
          <span v-for="(s, i) in diagnose.strengths" :key="i" class="strength-chip">{{ s }}</span>
        </div>
        <div class="weak-grid">
          <div v-for="(w, i) in diagnose.weakPoints" :key="i" class="weak-card" :class="prioClass(w.priority)">
            <div class="weak-top"><b>{{ w.name }}</b><span class="prio">{{ w.priority }}</span></div>
            <p>{{ w.reason }}</p>
            <p v-if="w.suggestion" class="weak-sug"><Icon icon="ph:lightbulb-duotone" /> {{ w.suggestion }}</p>
          </div>
        </div>
      </section>

      <!-- 学习路径 -->
      <section class="pack__sec" v-if="(plan.stages || []).length">
        <div class="sec-h"><span class="sec-no">02</span> 学习路径<small> · 规划智能体</small></div>
        <div class="stage-row">
          <div v-for="(s, i) in plan.stages" :key="i" class="stage-card">
            <div class="stage-no">阶段 {{ i + 1 }}<span v-if="s.days"> · {{ s.days }}</span></div>
            <h4>{{ s.title }}</h4>
            <p class="stage-goal">{{ s.goal }}</p>
            <ul><li v-for="(it, j) in s.items" :key="j">{{ it }}</li></ul>
            <div v-if="s.milestone" class="stage-milestone"><Icon icon="ph:flag-checkered-duotone" /> 验收：{{ s.milestone }}</div>
          </div>
        </div>
      </section>

      <!-- 知识讲义 -->
      <section class="pack__sec" v-if="(resources.sections || []).length">
        <div class="sec-h"><span class="sec-no">03</span> 知识讲义<small> · 资源生成智能体</small></div>
        <el-collapse v-model="openSections">
          <el-collapse-item v-for="(sec, i) in resources.sections" :key="i" :name="i">
            <template #title><span class="collapse-title">{{ sec.knowledge }}</span></template>
            <div class="markdown-body" v-safe-html="md(sec.content)"></div>
          </el-collapse-item>
        </el-collapse>
      </section>

      <!-- 知识思维导图 -->
      <section class="pack__sec" v-if="mindmap.data">
        <div class="sec-h"><span class="sec-no">04</span> 知识思维导图<small> · 思维导图智能体</small></div>
        <div ref="mindmapRef" class="mindmap-chart"></div>
      </section>

      <!-- 针对性练习 -->
      <section class="pack__sec" v-if="(quiz.questions || []).length">
        <div class="sec-h"><span class="sec-no">05</span> 针对性练习<small> · 出题智能体</small></div>
        <div v-for="(q, i) in quiz.questions" :key="i" class="q-card">
          <div class="q-head">
            <span class="q-idx">{{ i + 1 }}</span>
            <el-tag size="small" effect="plain">{{ typeLabel(q.type) }}</el-tag>
            <el-tag v-if="q.knowledge" size="small" type="info" effect="plain">{{ q.knowledge }}</el-tag>
            <span v-if="q.difficulty" class="q-diff" :class="'d' + diffNum(q.difficulty)" :title="diffLabel(q.difficulty)">
              {{ '★'.repeat(diffNum(q.difficulty)) }}<span class="q-diff__txt">{{ diffLabel(q.difficulty) }}</span>
            </span>
          </div>
          <div class="q-title">{{ q.title }}</div>
          <ul v-if="q.options && q.options.length" class="q-opts">
            <li v-for="(o, oi) in q.options" :key="oi">{{ letter(oi) }}. {{ o }}</li>
          </ul>
          <div v-if="mode === 'student' && !practiceResult" class="practice-input">
            <el-checkbox-group v-if="q.type === 'multi'" v-model="practiceAnswers[i]">
              <el-checkbox v-for="(o, oi) in q.options" :key="oi" :label="String(oi)">{{ letter(oi) }}</el-checkbox>
            </el-checkbox-group>
            <el-radio-group v-else-if="q.type === 'single'" v-model="practiceAnswers[i]">
              <el-radio v-for="(o, oi) in q.options" :key="oi" :label="String(oi)">{{ letter(oi) }}</el-radio>
            </el-radio-group>
            <el-radio-group v-else-if="q.type === 'judge'" v-model="practiceAnswers[i]">
              <el-radio label="true">正确</el-radio><el-radio label="false">错误</el-radio>
            </el-radio-group>
            <el-input v-else v-model="practiceAnswers[i]" placeholder="请输入答案" />
          </div>
          <div v-if="mode !== 'student' || practiceResult" class="q-ans">参考答案：<b>{{ displayAnswer(q) }}</b></div>
          <div v-if="q.analysis && (mode !== 'student' || practiceResult)" class="q-ana">解析：{{ q.analysis }}</div>
        </div>
        <div v-if="mode === 'student' && pack.id && !practiceResult" class="practice-submit">
          <el-button type="primary" :loading="practiceSubmitting" @click="submitPractice">提交练习并更新学习画像</el-button>
        </div>
        <div v-if="practiceResult" class="effect-result">
          <div class="effect-score"><b>{{ practiceResult.mastery }}%</b><span>本轮掌握度</span></div>
          <div class="effect-main">
            <h4>{{ practiceResult.summary }}</h4>
            <p>答对 {{ practiceResult.correctCount }}/{{ practiceResult.totalCount }}，趋势：{{ practiceResult.trend }}</p>
            <div v-if="practiceResult.focusPoints?.length" class="effect-focus">
              <span v-for="point in practiceResult.focusPoints" :key="point">{{ point }}</span>
            </div>
          </div>
        </div>
        <div v-if="practiceResult?.adaptivePath?.length" class="adaptive-path">
          <div v-for="(stage, i) in practiceResult.adaptivePath" :key="i">
            <b>{{ i + 1 }}. {{ stage.title }}</b><span>{{ stage.action }}</span>
          </div>
        </div>
        <div v-if="practiceResult?.recommendations?.length" class="practice-recommendations">
          <b>根据薄弱点推荐</b>
          <button v-for="item in practiceResult.recommendations" :key="item.id" @click="openResource(item.id)">
            <span>{{ item.title }}</span><small>{{ item.reason }}</small>
          </button>
        </div>
        <div v-if="mode === 'student' && practiceResult" class="practice-submit">
          <el-button plain @click="preparePracticeAnswers">重新作答</el-button>
        </div>
      </section>

      <!-- 拓展阅读 -->
      <section class="pack__sec" v-if="(reading.readings || []).length">
        <div class="sec-h"><span class="sec-no">06</span> 拓展阅读<small> · 拓展阅读智能体</small></div>
        <div class="read-grid">
          <div v-for="(r, i) in reading.readings" :key="i" class="read-card">
            <div class="read-top">
              <el-tag size="small" effect="plain" type="success">{{ r.type }}</el-tag>
              <el-tag v-if="r.level" size="small" effect="plain" :type="levelTagType(r.level)">{{ r.level }}</el-tag>
              <b>{{ r.title }}</b>
            </div>
            <p class="read-sum">{{ r.summary }}</p>
            <p class="read-why">推荐理由：{{ r.why }}</p>
            <p v-if="r.takeaway" class="read-take"><Icon icon="ph:target-duotone" /> 重点掌握：{{ r.takeaway }}</p>
          </div>
        </div>
      </section>

      <!-- 代码实操案例 -->
      <section class="pack__sec" v-if="(codecase.cases || []).length">
        <div class="sec-h"><span class="sec-no">07</span> 代码实操案例<small> · 代码实操智能体</small></div>
        <el-collapse v-model="openCases">
          <el-collapse-item v-for="(c, i) in codecase.cases" :key="i" :name="i">
            <template #title>
              <span class="collapse-title">{{ c.title }}</span>
              <el-tag size="small" effect="plain" class="case-lang">{{ c.language }}</el-tag>
            </template>
            <p v-if="c.scenario" class="case-scene">{{ c.scenario }}</p>
            <pre class="case-code"><code>{{ c.code }}</code></pre>
            <div v-if="c.expectedOutput" class="case-output">
              <div class="case-output__label"><Icon icon="ph:terminal-window-duotone" /> 预期运行结果</div>
              <pre>{{ c.expectedOutput }}</pre>
            </div>
            <div v-if="c.explanation" class="markdown-body" v-safe-html="md(c.explanation)"></div>
            <div v-if="c.challenge" class="case-challenge"><Icon icon="ph:rocket-launch-duotone" /> 进阶挑战：{{ c.challenge }}</div>
          </el-collapse-item>
        </el-collapse>
      </section>

      <!-- 多模态动画微课 -->
      <section class="pack__sec" v-if="(explainer.scenes || []).length">
        <div class="sec-h"><span class="sec-no">08</span> 多模态动画微课<small> · 讲解动画智能体</small></div>
        <div class="micro-head">
          <div>
            <h4>{{ explainer.title }}</h4>
            <p>{{ explainer.summary }}</p>
          </div>
          <el-tag type="success" effect="plain">{{ explainer.scenes.length }} 个分镜</el-tag>
        </div>
        <div class="micro-scenes">
          <div v-for="(scene, i) in explainer.scenes" :key="i" class="micro-scene">
            <span class="micro-index">{{ String(i + 1).padStart(2, '0') }}</span>
            <div class="micro-body">
              <b>{{ scene.title }}</b>
              <span v-if="scene.keypoint" class="micro-key">{{ scene.keypoint }}</span>
              <p>{{ scene.narration }}</p>
              <small>{{ scene.visual?.type || 'center' }}</small>
            </div>
          </div>
        </div>
      </section>

      <!-- 协作质量报告 -->
      <section class="pack__sec quality-sec" v-if="quality.summary">
        <div class="sec-h"><span class="sec-no">09</span> 协作质量报告<small> · 评审 / 复审智能体</small></div>
        <div class="quality-summary">
          <span class="quality-score">{{ quality.score || '—' }}</span>
          <div><b>{{ quality.approved ? '复审通过' : '仍需教师复核' }}</b><p>{{ quality.summary }}</p></div>
        </div>
        <div v-if="(quality.issues || []).length" class="quality-issues">
          <span v-for="(issue, i) in quality.issues" :key="i">{{ issue.agent }}：{{ issue.problem }}</span>
        </div>
      </section>

      <!-- RAG 引用校验 -->
      <section class="pack__sec" v-if="(pack.citations || []).length">
        <div class="sec-h"><span class="sec-no">10</span> 知识依据与引用校验<small> · 向量 RAG</small></div>
        <div class="citation-status" :class="{ valid: pack.citationValidation?.allValid }">
          <Icon :icon="pack.citationValidation?.allValid ? 'ph:check-circle-duotone' : 'ph:warning-circle-duotone'" />
          <span>{{ pack.citationValidation?.allValid ? '所有生成步骤均通过引用校验' : '部分内容缺少有效行内引用，建议教师复核' }}</span>
        </div>
        <div class="citation-list">
          <div v-for="(citation, i) in pack.citations" :key="citation.label || i" class="citation-item">
            <code>[{{ citation.label }}]</code>
            <div><b>{{ citation.title }}</b><p>{{ citation.excerpt }}</p></div>
            <span v-if="citation.score != null">相似度{{ Math.round(citation.score * 100) }}%</span>
          </div>
        </div>
      </section>

      <!-- 协作拓扑：证明「智能体真的在协作」而不是各写各的 -->
      <section class="pack__sec handoff-sec" v-if="handoffs">
        <div class="sec-h"><span class="sec-no">11</span> 协作拓扑<small> · 交接智能体</small></div>
        <p class="handoff-note">
          <Icon :icon="handoffs.reordered ? 'ph:shuffle-duotone' : 'ph:list-numbers-duotone'" />
          {{ handoffs.note }}
        </p>

        <div class="handoff-order">
          <span v-for="k in handoffs.contentOrder" :key="k" class="handoff-chip"
            :class="{ moved: handoffs.defaultContentOrder.indexOf(k) !== handoffs.contentOrder.indexOf(k) }">
            {{ labelOf(k) }}
          </span>
        </div>
        <p class="handoff-hint">内容智能体的实际执行顺序（高亮为被交接重排的项）</p>

        <div v-if="(handoffs.edges || []).length" class="handoff-edges">
          <div v-for="(e, i) in handoffs.edges" :key="i" class="handoff-edge">
            <span class="he-flow"><b>{{ labelOf(e.from) }}</b> <Icon icon="ph:arrow-right-bold" /> <b>{{ labelOf(e.to) }}</b></span>
            <span class="he-reason">{{ e.reason || e.artifact }}</span>
            <span class="he-proof" :class="{ ok: e.injected }">
              {{ e.injected ? `已注入 ${e.digestChars} 字` : '未注入' }}
            </span>
          </div>
        </div>
        <p v-else class="handoff-hint">本次未声明交接关系，按默认顺序执行</p>

        <div v-if="(handoffs.rejected || []).length" class="handoff-rejected">
          <span class="hr-title">已丢弃的交接声明</span>
          <span v-for="(r, i) in handoffs.rejected" :key="i" class="hr-item">
            {{ r.from }} → {{ r.to }}：{{ r.reason }}
          </span>
        </div>
      </section>

      <div class="pack__actions">
        <el-button :icon="Download" :loading="exporting" @click="exportPdf">导出资源包 PDF</el-button>
        <el-button v-if="mode === 'teacher' && pack.id" type="success" :icon="PaperPlane"
          :loading="pushing" @click="pushResource(pack.id)">一键推送给学生</el-button>
        <el-button type="primary" :icon="MagicStick" @click="run">重新生成</el-button>
      </div>
    </div>

    <!-- 历史资源包 -->
    <el-drawer v-model="libVisible" title="我的资源库" size="46%">
      <el-empty v-if="!libList.length" description="还没有保存的资源包" />
      <div v-for="item in libList" :key="item.id" class="lib-item">
        <div class="lib-info">
          <div class="lib-title">{{ item.title }}</div>
          <div class="lib-meta">
            <el-tag size="small" effect="plain">{{ scopeLabel(item.scopeType) }}</el-tag>
            <span v-if="item.courseName">{{ item.courseName }}</span>
            <span class="lib-time">{{ item.createTime }}</span>
          </div>
        </div>
        <div class="lib-ops">
          <el-button size="small" type="primary" plain @click="openResource(item.id)">查看</el-button>
          <el-button v-if="mode === 'teacher'" size="small" type="success" plain :icon="PaperPlane"
            :loading="pushingId === item.id" @click="pushResource(item.id)">推送</el-button>
          <el-button size="small" type="danger" plain :icon="Delete" @click="removeResource(item.id)" />
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MagicStick, Download, FolderOpened as FolderOpen, Delete, Promotion as PaperPlane, CircleClose } from '@element-plus/icons-vue'
import { Icon } from '@iconify/vue'
import { useStore } from 'vuex'
import { useRoute } from 'vue-router'
import * as echarts from 'echarts'
import * as agentApi from '@/api/agent'
import { listStudentInsightCourses } from '@/api/aiInsight'
import { listCoursesByTeacher } from '@/api/course'
import { evaluateResourcePractice } from '@/api/learningProfile'
import { renderMarkdown } from '@/utils/miniMarkdown'
import { readJsonEventStream } from '@/utils/sse'

const props = defineProps({ mode: { type: String, default: 'student' } })
const store = useStore()
const route = useRoute()

const courses = ref([])
const courseid = ref(null)
const presetFocusPoints = ref(
  typeof route.query.focusPoints === 'string'
    ? route.query.focusPoints.split('|').map(s => s.trim()).filter(Boolean)
    : []
)
const quizCount = ref(5)
const running = ref(false)
const started = ref(false)
const exporting = ref(false)
const pushing = ref(false)
const pushingId = ref(null)
const packRef = ref(null)
const openSections = ref([0])
const openCases = ref([0])
const mindmapRef = ref(null)
let mindmapChart = null

// 运行日志与概览打字机
const logLines = ref([])
const logRef = ref(null)
const overviewTyping = ref(false)
const typedOverview = ref('')
let typeTimer = null
let runController = null

// 总耗时与并行度。
// 服务端把 7 个内容智能体按依赖分成若干波并行执行，同时会有多个节点处于 running，
// 因此「跑了多久」「几路在并行」比单个智能体的耗时更能解释用户感知到的速度。
const elapsedSeconds = ref(0)
let elapsedTimer = null
const runningCount = computed(() => agents.filter(a => a.status === 'running').length)
const elapsedLabel = computed(() => {
  const total = elapsedSeconds.value
  const m = Math.floor(total / 60)
  const s = total % 60
  return m > 0 ? `${m}分${String(s).padStart(2, '0')}秒` : `${s}秒`
})
const startElapsedTimer = () => {
  stopElapsedTimer()
  const begin = Date.now()
  elapsedSeconds.value = 0
  elapsedTimer = setInterval(() => {
    elapsedSeconds.value = Math.floor((Date.now() - begin) / 1000)
  }, 1000)
}
const stopElapsedTimer = () => {
  if (elapsedTimer) { clearInterval(elapsedTimer); elapsedTimer = null }
}
// 单一收口：无论正常结束、失败、取消还是连接断开，只要不再运行就停表
watch(running, value => { if (!value) stopElapsedTimer() })
const nowTime = () => {
  const d = new Date()
  const p = n => String(n).padStart(2, '0')
  return `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}
const pushLog = (text, type = 'info') => {
  logLines.value.push({ time: nowTime(), text, type })
  nextTick(() => { if (logRef.value) logRef.value.scrollTop = logRef.value.scrollHeight })
}
const typeOverview = (text) => {
  if (typeTimer) clearInterval(typeTimer)
  typedOverview.value = ''
  overviewTyping.value = true
  let i = 0
  typeTimer = setInterval(() => {
    typedOverview.value = text.slice(0, i++)
    if (i > text.length) { clearInterval(typeTimer); typeTimer = null; overviewTyping.value = false }
  }, 18)
}

const diagnose = reactive({ summary: '', level: '', weakPoints: [], strengths: [] })
const plan = reactive({ stages: [] })
const resources = reactive({ sections: [] })
const mindmap = reactive({ data: null })
const quiz = reactive({ questions: [] })
const reading = reactive({ readings: [] })
const codecase = reactive({ cases: [] })
const explainer = reactive({ title: '', summary: '', scenes: [] })
const coordination = reactive({ strategy: '', assignments: [], handoffs: [], qualityCriteria: [] })

/**
 * 协作拓扑：**由服务端算好**（顺序、生效的交接边、分层布局都在后端）。
 * 前端只取来渲染——这样图是确定的、可复现的，Vue 侧的改动也最小。
 */
const handoffs = computed(() => pack.collaboration?.handoffs || null)

/** 步骤 key → 中文名。与后端 ArtifactKey 的 key 一一对应。 */
const AGENT_LABELS = {
  diagnose: '诊断', coordinate: '协调', plan: '规划', resource: '资源',
  mindmap: '思维导图', quiz: '出题', reading: '拓展阅读', codecase: '代码实操',
  explainer: '讲解动画', review: '评审', reviewFinal: '复审', assemble: '编排'
}
const labelOf = (key) => AGENT_LABELS[key] || key
const quality = reactive({ approved: false, score: null, summary: '', issues: [], checks: {} })
const pack = reactive({
  id: null, title: '', subject: '', overview: '', weakPoints: [],
  groundingSources: [], citations: [], citationValidation: {}
})
const practiceAnswers = reactive({})
const practiceSubmitting = ref(false)
const practiceResult = ref(null)

const agentIntro = {
  diagnose: '采集作业、考试与错题数据，定位薄弱知识点',
  coordinate: '拆分任务、分配重点并声明智能体间交接关系',
  plan: '按薄弱点制定分阶段路径与可检验的验收标准',
  resource: '生成六段式讲义：讲解、示例、口诀、自测',
  mindmap: '构建可交互的知识层级思维导图',
  quiz: '按基础/进阶/挑战梯度出针对性练习题',
  reading: '推荐入门到深入的真实拓展学习材料',
  codecase: '设计可运行代码案例与进阶挑战任务',
  explainer: '把核心薄弱点编排为可播放的动画分镜微课',
  review: '独立交叉检查一致性、覆盖度、准确性与个性化',
  reviewFinal: '对定向返工结果进行最终质量复审',
  assemble: '整合全部产出写入资源库，支持一键推送'
}

const agents = reactive([
  { key: 'diagnose', name: '诊断智能体', icon: 'ph:stethoscope-duotone', status: 'idle', elapsed: 0 },
  { key: 'coordinate', name: '协调智能体', icon: 'ph:git-merge-duotone', status: 'idle', elapsed: 0 },
  { key: 'plan', name: '规划智能体', icon: 'ph:map-trifold-duotone', status: 'idle', elapsed: 0 },
  { key: 'resource', name: '资源智能体', icon: 'ph:book-open-text-duotone', status: 'idle', elapsed: 0 },
  { key: 'mindmap', name: '思维导图智能体', icon: 'ph:tree-structure-duotone', status: 'idle', elapsed: 0 },
  { key: 'quiz', name: '出题智能体', icon: 'ph:exam-duotone', status: 'idle', elapsed: 0 },
  { key: 'reading', name: '拓展阅读智能体', icon: 'ph:books-duotone', status: 'idle', elapsed: 0 },
  { key: 'codecase', name: '代码实操智能体', icon: 'ph:code-duotone', status: 'idle', elapsed: 0 },
  { key: 'explainer', name: '讲解动画智能体', icon: 'ph:film-strip-duotone', status: 'idle', elapsed: 0 },
  { key: 'review', name: '质量评审智能体', icon: 'ph:magnifying-glass-duotone', status: 'idle', elapsed: 0 },
  { key: 'reviewFinal', name: '复审智能体', icon: 'ph:seal-check-duotone', status: 'idle', elapsed: 0 },
  { key: 'assemble', name: '编排智能体', icon: 'ph:stack-duotone', status: 'idle', elapsed: 0 }
])

const renderMindmap = () => {
  if (!mindmap.data || !mindmapRef.value) return
  // 容器由 v-if 控制，重新渲染时旧实例可能仍挂在已销毁的 DOM 上，先丢弃再重建
  if (mindmapChart && (mindmapChart.isDisposed?.() || mindmapChart.getDom() !== mindmapRef.value)) {
    mindmapChart.dispose()
    mindmapChart = null
  }
  if (!mindmapChart) mindmapChart = echarts.init(mindmapRef.value)
  const palette = ['#2563eb', '#0891b2', '#0ea5e9', '#ea580c', '#16a34a', '#db2777', '#ca8a04']
  // 大模型偶尔把导图包成 JSON 字符串或数组，这里统一成对象
  let raw = mindmap.data
  if (typeof raw === 'string') { try { raw = JSON.parse(raw) } catch (e) { return } }
  if (Array.isArray(raw)) raw = raw[0]
  if (!raw || typeof raw !== 'object') return
  const tree = JSON.parse(JSON.stringify(raw))
  if (!tree.name) tree.name = pack.subject || pack.title || '知识导图'
  if (tree.children) tree.children.forEach((c, i) => {
    const color = palette[i % palette.length]
    c.lineStyle = { color }; c.itemStyle = { color }
  })
  mindmapChart.setOption({
    tooltip: { trigger: 'item', triggerOn: 'mousemove' },
    series: [{
      type: 'tree', data: [tree], top: '3%', left: '7%', bottom: '3%', right: '22%',
      symbol: 'circle', symbolSize: 9, orient: 'LR', expandAndCollapse: true, initialTreeDepth: 3,
      label: { position: 'left', verticalAlign: 'middle', align: 'right', fontSize: 13,
        color: '#1e293b', backgroundColor: '#f1f5f9', padding: [3, 7], borderRadius: 4 },
      leaves: { label: { position: 'right', verticalAlign: 'middle', align: 'left' } },
      lineStyle: { width: 1.5, curveness: 0.5 }, emphasis: { focus: 'descendant' },
      animationDuration: 500, animationDurationUpdate: 600
    }]
  })
  mindmapChart.resize()
  // 布局稳定后再校正一次尺寸，避免初始化时容器宽度未就绪导致空白
  setTimeout(() => { if (mindmapChart && !mindmapChart.isDisposed?.()) mindmapChart.resize() }, 300)
}

// 资源包容器为 v-if="pack.title"，生成流程中第 4 步产出导图数据时容器尚未挂载，
// renderMindmap 会因 mindmapRef 为空而跳过；待容器与数据都就绪后在此补渲染。
watch([() => mindmap.data, () => pack.title], async () => {
  await nextTick()
  renderMindmap()
})

const md = (s) => renderMarkdown(s)
const setStatus = (key, status, elapsed) => {
  const a = agents.find(x => x.key === key)
  if (a) { a.status = status; if (elapsed != null) a.elapsed = (elapsed / 1000).toFixed(1) }
}
const collectGrounding = (data) => {
  const sources = Array.isArray(data?.groundingSources) ? data.groundingSources : []
  pack.groundingSources = [...new Set([...pack.groundingSources, ...sources])]
  const citations = Array.isArray(data?.citations) ? data.citations : []
  const byLabel = new Map(pack.citations.map(c => [c.label, c]))
  citations.forEach(c => { if (c?.label) byLabel.set(c.label, c) })
  pack.citations = [...byLabel.values()]
  if (data?.citationValidation) {
    const reports = { ...(pack.citationValidation.reports || {}) }
    reports[data.agent || `step${Object.keys(reports).length + 1}`] = data.citationValidation
    const values = Object.values(reports)
    pack.citationValidation = {
      reports,
      totalSteps: values.length,
      validSteps: values.filter(v => v?.valid).length,
      allValid: values.length === 0 || values.every(v => v?.valid)
    }
  }
}

/** 各智能体统一带上本轮的取消信号 */
const signal = () => runController?.signal
const runId = ref(null)
const cancelRun = async () => {
  if (!running.value) return
  if (runId.value) {
    try { await agentApi.cancelPipeline(runId.value) } catch (e) { /* 取消失败也要继续中断本地流程 */ }
  }
  runController?.abort()
  pushLog('用户已停止本次生成；已完成的智能体结果仍保留在页面中', 'warn')
}

// ===== 服务端流水线（SSE 流式 + 断点恢复） =====
const agentName = (key) => {
  if (key?.startsWith('revise_')) return `${agentName(key.slice(7))}返工`
  return (agents.find(a => a.key === key)?.name) || key
}

const applyStepData = (step, data) => {
  const d = data || {}
  collectGrounding(d)
  if (step === 'diagnose') {
    Object.assign(diagnose, { summary: d.summary || '', level: d.level || '', weakPoints: d.weakPoints || [], strengths: d.strengths || [] })
    pack.subject = d.subject || ''
    pack.weakPoints = (d.weakPoints || []).map(w => (w && w.name) || w)
  } else if (step === 'coordinate') {
    Object.assign(coordination, {
      strategy: d.strategy || '', assignments: d.assignments || [],
      handoffs: d.handoffs || [], qualityCriteria: d.qualityCriteria || []
    })
  } else if (step === 'plan') {
    plan.stages = d.stages || []
  } else if (step === 'resource') {
    resources.sections = d.sections || []
  } else if (step === 'mindmap') {
    mindmap.data = d.mindmap || null
  } else if (step === 'quiz') {
    quiz.questions = d.questions || []
    preparePracticeAnswers()
  } else if (step === 'reading') {
    reading.readings = d.readings || []
  } else if (step === 'codecase') {
    codecase.cases = d.cases || []
  } else if (step === 'explainer') {
    Object.assign(explainer, { title: d.title || '', summary: d.summary || '', scenes: d.scenes || [] })
  } else if (step === 'review' || step === 'reviewFinal') {
    Object.assign(quality, {
      approved: !!d.approved, score: d.score ?? null, summary: d.summary || '',
      issues: d.issues || [], checks: d.checks || {}
    })
  } else if (step === 'assemble') {
    pack.id = d.id || null
    pack.title = d.title || '个性化学习资源包'
    pack.overview = d.overview || ''
  }
}

const handlePipelineEvent = async (event, payload) => {
  if (event === 'step') {
    const { step, status, data, message, replay } = payload
    const statusKey = step?.startsWith('revise_') ? step.slice(7) : step
    if (status === 'running') {
      setStatus(statusKey, 'running')
      pushLog(`${agentName(step)} 启动`)
    } else if (status === 'done') {
      applyStepData(statusKey, data)
      setStatus(statusKey, 'done', data?.elapsedMs)
      pushLog(`✓ ${agentName(step)} 完成${replay ? '（已恢复）' : ''} · 耗时 ${(((data?.elapsedMs) || 0) / 1000).toFixed(1)}s`, 'ok')
      if (statusKey === 'mindmap') { await nextTick(); renderMindmap() }
    } else if (status === 'error') {
      setStatus(statusKey, 'error')
      pushLog(`✗ ${agentName(step)} 失败：${message || '未知错误'}`, 'err')
    }
  } else if (event === 'done') {
    pushLog('全部智能体协作完成 ✓', 'sys')
    typeOverview(pack.overview)
    ElMessage.success('资源包已生成并保存到资源库')
    await nextTick()
    renderMindmap()
    packRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  } else if (event === 'fail') {
    agents.forEach(a => { if (a.status === 'running') a.status = 'error' })
    pushLog(`✗ ${payload.message || '协作过程中出现错误，已中断'}`, 'err')
  } else if (event === 'cancelled') {
    agents.forEach(a => { if (a.status === 'running') a.status = 'idle' })
    pushLog('本次生成已停止', 'warn')
  }
}

/** 订阅流水线 SSE，把每个事件派发给 handlePipelineEvent */
const openPipelineStream = async (id, abortSignal) => {
  const response = await fetch(agentApi.pipelineStreamUrl(id), {
    headers: { Accept: 'text/event-stream' },
    credentials: 'include',
    signal: abortSignal
  })
  await readJsonEventStream(response, handlePipelineEvent)
}

const preparePracticeAnswers = () => {
  Object.keys(practiceAnswers).forEach(key => delete practiceAnswers[key])
  quiz.questions.forEach((q, i) => {
    practiceAnswers[i] = q.type === 'multi' ? [] : ''
  })
  practiceResult.value = null
}

const submitPractice = async () => {
  if (!pack.id || practiceSubmitting.value) return
  const answers = quiz.questions.map((q, i) =>
    q.type === 'multi' ? (practiceAnswers[i] || []) : (practiceAnswers[i] ?? '')
  )
  if (answers.some((answer, i) => quiz.questions[i]?.type === 'multi'
    ? !Array.isArray(answer) || answer.length === 0
    : String(answer).trim() === '')) {
    ElMessage.warning('请完成全部题目后再提交')
    return
  }
  practiceSubmitting.value = true
  try {
    const res = await evaluateResourcePractice(pack.id, answers)
    practiceResult.value = res.data || null
    const focus = practiceResult.value?.focusPoints || []
    pack.weakPoints = [...new Set([...pack.weakPoints, ...focus])]
    ElMessage.success('评估完成，学习画像与后续路径已自动更新')
  } catch (e) { /* 拦截器已提示 */ } finally {
    practiceSubmitting.value = false
  }
}

const resetPipeline = () => {
  agents.forEach(a => { a.status = 'idle'; a.elapsed = 0 })
  diagnose.summary = ''; diagnose.level = ''; diagnose.weakPoints = []; diagnose.strengths = []
  plan.stages = []; resources.sections = []; quiz.questions = []
  mindmap.data = null; reading.readings = []; codecase.cases = []
  Object.assign(explainer, { title: '', summary: '', scenes: [] })
  Object.assign(coordination, { strategy: '', assignments: [], handoffs: [], qualityCriteria: [] })
  Object.assign(quality, { approved: false, score: null, summary: '', issues: [], checks: {} })
  Object.keys(practiceAnswers).forEach(key => delete practiceAnswers[key])
  practiceResult.value = null
  pack.id = null; pack.title = ''; pack.subject = ''; pack.overview = ''; pack.weakPoints = []
  pack.groundingSources = []; pack.citations = []; pack.citationValidation = {}
}

const loadCourses = async () => {
  try {
    if (props.mode === 'teacher') {
      const tid = store.state.user?.teacherid
      if (!tid) return
      const res = await listCoursesByTeacher(tid)
      courses.value = res.data || []
    } else {
      const res = await listStudentInsightCourses()
      courses.value = res.data || []
    }
  } catch (e) { /* 拦截器已提示 */ }
}

const run = async () => {
  if (props.mode === 'teacher' && !courseid.value) { ElMessage.warning('请先选择课程'); return }
  running.value = true
  runController?.abort()
  runController = new AbortController()
  started.value = true
  startElapsedTimer()
  resetPipeline()
  logLines.value = []
  pushLog(props.mode === 'teacher' ? '教师班级模式 · 多智能体协作流水线启动' : '多智能体协作流水线启动', 'sys')
  let fallbackToLegacy = false
  try {
    const sRes = await agentApi.startPipeline(
      { courseid: courseid.value, quizCount: quizCount.value, focusPoints: presetFocusPoints.value },
      runController.signal
    )
    runId.value = sRes.data?.runId || null
    if (!runId.value) throw new Error('no runId')
    if (sRes.data?.attached) pushLog('检测到进行中的流水线，已重连并恢复进度', 'sys')
    await openPipelineStream(runId.value, runController.signal)
  } catch (e) {
    if (e?.name === 'AbortError' || e?.code === 'ERR_CANCELED' || e?.name === 'CanceledError') {
      agents.forEach(a => { if (a.status === 'running') a.status = 'idle' })
    } else if (runId.value) {
      // 流水线已在服务端运行，仅是连接中断：结果不会丢失
      agents.forEach(a => { if (a.status === 'running') a.status = 'idle' })
      pushLog('进度连接中断，流水线仍在服务端运行；稍后可在「资源库」查看生成结果', 'warn')
    } else {
      fallbackToLegacy = true
      pushLog('服务端流水线不可用，切换为逐步调用模式', 'warn')
    }
  } finally {
    if (!fallbackToLegacy) {
      running.value = false
      runId.value = null
      runController = null
    }
  }
  if (fallbackToLegacy) await runLegacy()
}

/** 兼容旧后端：前端逐步调用各智能体接口 */
const runLegacy = async () => {
  running.value = true
  runController = new AbortController()
  try {
    // 1. 诊断
    setStatus('diagnose', 'running')
    pushLog('① 诊断智能体 启动，正在采集学情数据…')
    const dRes = props.mode === 'teacher'
      ? await agentApi.diagnoseClass({ courseid: courseid.value }, signal())
      : await agentApi.diagnose({ courseid: courseid.value }, signal())
    const d = dRes.data || {}
    Object.assign(diagnose, { summary: d.summary || '', level: d.level || '', weakPoints: d.weakPoints || [], strengths: d.strengths || [] })
    pack.subject = d.subject || ''
    pack.weakPoints = (d.weakPoints || []).map(w => (w && w.name) || w).filter(Boolean)
    presetFocusPoints.value.forEach(point => {
      if (!pack.weakPoints.includes(point)) pack.weakPoints.push(point)
    })
    setStatus('diagnose', 'done', d.elapsedMs)
    pushLog(`✓ 诊断完成：识别薄弱点 ${pack.weakPoints.length} 个${pack.subject ? '（' + pack.subject + '）' : ''} · 耗时 ${(d.elapsedMs / 1000).toFixed(1)}s`, 'ok')
    if (!pack.weakPoints.length) { pushLog('⚠ 暂无足够学情数据用于诊断', 'warn'); ElMessage.warning('暂无足够学情数据用于诊断'); running.value = false; return }
    await nextTick()

    const body = { courseid: courseid.value, subject: pack.subject, weakPoints: pack.weakPoints }

    // 2. 规划
    setStatus('plan', 'running')
    pushLog('② 规划智能体 启动，编排分阶段学习路径…')
    const pRes = await agentApi.planLearning(body, signal())
    plan.stages = pRes.data?.stages || []
    collectGrounding(pRes.data)
    setStatus('plan', 'done', pRes.data?.elapsedMs)
    pushLog(`✓ 规划完成：生成 ${plan.stages.length} 个学习阶段 · 耗时 ${((pRes.data?.elapsedMs || 0) / 1000).toFixed(1)}s`, 'ok')

    // 3. 资源
    setStatus('resource', 'running')
    pushLog('③ 资源智能体 启动，生成知识讲解与要点…')
    const rRes = await agentApi.buildResource(body, signal())
    resources.sections = rRes.data?.sections || []
    collectGrounding(rRes.data)
    setStatus('resource', 'done', rRes.data?.elapsedMs)
    pushLog(`✓ 资源完成：产出 ${resources.sections.length} 个知识章节 · 耗时 ${((rRes.data?.elapsedMs || 0) / 1000).toFixed(1)}s`, 'ok')

    // 4. 思维导图
    setStatus('mindmap', 'running')
    pushLog('④ 思维导图智能体 启动，构建知识层级…')
    const mRes = await agentApi.buildMindMap(body, signal())
    mindmap.data = mRes.data?.mindmap || null
    collectGrounding(mRes.data)
    setStatus('mindmap', 'done', mRes.data?.elapsedMs)
    pushLog(`✓ 思维导图完成：${mindmap.data ? '已生成知识树' : '无数据'} · 耗时 ${((mRes.data?.elapsedMs || 0) / 1000).toFixed(1)}s`, 'ok')
    await nextTick(); renderMindmap()

    // 5. 出题
    setStatus('quiz', 'running')
    pushLog(`⑤ 出题智能体 启动，生成 ${quizCount.value} 道针对性练习…`)
    const qRes = await agentApi.buildQuiz({ ...body, count: quizCount.value }, signal())
    quiz.questions = qRes.data?.questions || []
    preparePracticeAnswers()
    collectGrounding(qRes.data)
    setStatus('quiz', 'done', qRes.data?.elapsedMs)
    pushLog(`✓ 出题完成：生成 ${quiz.questions.length} 道练习题 · 耗时 ${((qRes.data?.elapsedMs || 0) / 1000).toFixed(1)}s`, 'ok')

    // 6. 拓展阅读
    setStatus('reading', 'running')
    pushLog('⑥ 拓展阅读智能体 启动，匹配优质学习材料…')
    const rdRes = await agentApi.buildReading(body, signal())
    reading.readings = rdRes.data?.readings || []
    collectGrounding(rdRes.data)
    setStatus('reading', 'done', rdRes.data?.elapsedMs)
    pushLog(`✓ 拓展阅读完成：推荐 ${reading.readings.length} 条材料 · 耗时 ${((rdRes.data?.elapsedMs || 0) / 1000).toFixed(1)}s`, 'ok')

    // 7. 代码实操
    setStatus('codecase', 'running')
    pushLog('⑦ 代码实操智能体 启动，设计动手案例…')
    const ccRes = await agentApi.buildCodeCase(body, signal())
    codecase.cases = ccRes.data?.cases || []
    collectGrounding(ccRes.data)
    setStatus('codecase', 'done', ccRes.data?.elapsedMs)
    pushLog(`✓ 代码实操完成：生成 ${codecase.cases.length} 个案例 · 耗时 ${((ccRes.data?.elapsedMs || 0) / 1000).toFixed(1)}s`, 'ok')

    // 8. 编排 + 入库
    setStatus('assemble', 'running')
    pushLog('⑧ 编排智能体 启动，整合资源包并写入资源库…')
    const aRes = await agentApi.assemblePack({
      courseid: courseid.value,
      subject: pack.subject,
      weakPoints: pack.weakPoints,
      groundingSources: pack.groundingSources,
      citations: pack.citations,
      citationValidation: pack.citationValidation,
      diagnose: { summary: diagnose.summary, level: diagnose.level, weakPoints: diagnose.weakPoints, strengths: diagnose.strengths },
      plan: { stages: plan.stages },
      resources: { sections: resources.sections },
      mindmap: { data: mindmap.data },
      quiz: { questions: quiz.questions },
      reading: { readings: reading.readings },
      codecase: { cases: codecase.cases },
      explainer: { title: explainer.title, summary: explainer.summary, scenes: explainer.scenes }
    }, signal())
    pack.id = aRes.data?.id || null
    pack.title = aRes.data?.title || '个性化学习资源包'
    setStatus('assemble', 'done', aRes.data?.elapsedMs)
    pushLog(`✓ 编排完成：《${pack.title}》已保存到资源库 · 耗时 ${((aRes.data?.elapsedMs || 0) / 1000).toFixed(1)}s`, 'ok')
    pushLog('全部智能体协作完成 ✓', 'sys')
    pack.overview = aRes.data?.overview || ''
    typeOverview(pack.overview)
    ElMessage.success('资源包已生成并保存到资源库')
    await nextTick()
    packRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  } catch (e) {
    if (e?.code === 'ERR_CANCELED' || e?.name === 'CanceledError') {
      agents.forEach(a => { if (a.status === 'running') a.status = 'idle' })
    } else {
      agents.forEach(a => { if (a.status === 'running') a.status = 'error' })
      pushLog('✗ 协作过程中出现错误，已中断', 'err')
    }
  } finally {
    running.value = false
    runController = null
  }
}

// ===== 资源库 =====
const libVisible = ref(false)
const libList = ref([])
const openLibrary = async () => {
  libVisible.value = true
  try {
    const res = await agentApi.listAgentResources()
    libList.value = res.data || []
  } catch (e) { /* 拦截器已提示 */ }
}
const openResource = async (id) => {
  try {
    const res = await agentApi.getAgentResource(id)
    const p = res.data || {}
    Object.assign(diagnose, { summary: '', level: '', weakPoints: [], strengths: [] }, p.diagnose || {})
    plan.stages = p.plan?.stages || []
    resources.sections = p.resources?.sections || []
    mindmap.data = p.mindmap?.data || null
    quiz.questions = p.quiz?.questions || []
    preparePracticeAnswers()
    reading.readings = p.reading?.readings || []
    codecase.cases = p.codecase?.cases || []
    Object.assign(explainer, {
      title: p.explainer?.title || '', summary: p.explainer?.summary || '',
      scenes: p.explainer?.scenes || []
    })
    const collab = p.collaboration || {}
    Object.assign(coordination, collab.strategy || {})
    Object.assign(quality, p.qualityReview || collab.finalReview || {})
    pack.id = id
    pack.title = p.title || '资源包'
    pack.subject = p.subject || ''
    pack.overview = p.overview || ''
    pack.weakPoints = p.weakPoints || []
    pack.groundingSources = p.groundingSources || []
    pack.citations = p.citations || []
    pack.citationValidation = p.citationValidation || {}
    overviewTyping.value = false
    started.value = false
    agents.forEach(a => { a.status = 'done' })
    openSections.value = resources.sections.length ? [0] : []
    openCases.value = codecase.cases.length ? [0] : []
    libVisible.value = false
    await nextTick()
    renderMindmap()
    packRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  } catch (e) {
    ElMessage.error('资源不存在、已失效或无权访问')
  }
}
const removeResource = async (id) => {
  try {
    await ElMessageBox.confirm('确定删除该资源包？', '提示', { type: 'warning' })
    await agentApi.deleteAgentResource(id)
    libList.value = libList.value.filter(x => x.id !== id)
    ElMessage.success('已删除')
  } catch (e) { /* 取消或失败 */ }
}

const pushResource = async (id) => {
  try {
    await ElMessageBox.confirm('将把该资源包推送到课程内全部学生的资源库，并发送站内通知，确认推送？', '一键推送给学生', {
      type: 'info', confirmButtonText: '推送', cancelButtonText: '取消'
    })
  } catch (e) { return }
  pushing.value = true
  pushingId.value = id
  try {
    const res = await agentApi.pushAgentResource(id)
    const r = res.data || {}
    ElMessage.success(`已推送给 ${r.pushed || 0}/${r.total || 0} 名学生`)
  } catch (e) { /* 拦截器已提示 */ } finally {
    pushing.value = false
    pushingId.value = null
  }
}

const exportPdf = async () => {
  if (!packRef.value) return
  exporting.value = true
  try {
    const html2pdf = (await import('html2pdf.js')).default
    await html2pdf().set({
      margin: 10,
      filename: (pack.title || '学习资源包') + '.pdf',
      image: { type: 'jpeg', quality: 0.95 },
      html2canvas: { scale: 2, useCORS: true },
      jsPDF: { unit: 'mm', format: 'a4', orientation: 'portrait' },
      pagebreak: { mode: ['avoid-all', 'css'] }
    }).from(packRef.value).save()
  } catch (e) {
    ElMessage.error('导出失败')
  } finally {
    exporting.value = false
  }
}

const typeLabel = (t) => ({ single: '单选', multi: '多选', judge: '判断', blank: '填空' }[t] || '题目')
const letter = (i) => String.fromCharCode(65 + i)
const displayAnswer = (q) => {
  if (q.type === 'single') {
    const index = parseInt(q.answer)
    return Number.isNaN(index) ? q.answer : letter(index)
  }
  if (q.type === 'multi') return String(q.answer).split(',').map(x => {
    const index = parseInt(x.trim())
    return Number.isNaN(index) ? x.trim() : letter(index)
  }).join('、')
  if (q.type === 'judge') return /^(true|正确|对|是|t|1)$/i.test(String(q.answer).trim()) ? '正确' : '错误'
  return q.answer
}
const prioClass = (p) => (p === '高' ? 'p-high' : p === '中' ? 'p-mid' : 'p-low')
const diffNum = (d) => Math.min(3, Math.max(1, parseInt(d) || 1))
const diffLabel = (d) => ['基础', '进阶', '挑战'][diffNum(d) - 1]
const levelTagType = (l) => ({ '入门': 'info', '进阶': 'warning', '深入': 'danger' }[l] || 'info')
const scopeLabel = (s) => ({ student: '个人', course: '课程', class: '班级' }[s] || s)

/** 页面加载时恢复进行中的流水线（刷新不丢进度） */
const reattachPipeline = async () => {
  try {
    const res = await agentApi.getLatestPipeline()
    const latest = res.data || {}
    if (!latest.runId) return
    if (latest.status === 'done' && latest.resourceId) {
      const seenKey = `agent_result_seen_${latest.runId}`
      if (!sessionStorage.getItem(seenKey)) {
        sessionStorage.setItem(seenKey, '1')
        try {
          await ElMessageBox.confirm('上一次多智能体资源包已经生成完成，是否立即查看？', '生成完成', {
            confirmButtonText: '查看资源包', cancelButtonText: '稍后', type: 'success'
          })
          await openResource(latest.resourceId)
        } catch (e) { /* 用户选择稍后查看 */ }
      }
      return
    }
    if (latest.status !== 'running') return
    started.value = true
    startElapsedTimer()
    running.value = true
    runId.value = latest.runId
    if (latest.courseId) courseid.value = latest.courseId
    if (latest.quizCount) quizCount.value = latest.quizCount
    runController = new AbortController()
    pushLog('检测到进行中的多智能体流水线，正在恢复进度…', 'sys')
    try {
      await openPipelineStream(latest.runId, runController.signal)
    } catch (e) {
      agents.forEach(a => { if (a.status === 'running') a.status = 'idle' })
      pushLog('进度连接中断，流水线仍在服务端运行；稍后可在「资源库」查看生成结果', 'warn')
    }
    running.value = false
    runId.value = null
    runController = null
  } catch (e) { /* 没有进行中的流水线属正常情况 */ }
}

const onResize = () => mindmapChart && mindmapChart.resize()
onMounted(async () => {
  const queryCourseId = Number(route.query.courseid)
  if (Number.isFinite(queryCourseId) && queryCourseId > 0) courseid.value = queryCourseId
  await loadCourses()
  window.addEventListener('resize', onResize)
  const queryResourceId = Number(route.query.resourceId)
  if (Number.isFinite(queryResourceId) && queryResourceId > 0) await openResource(queryResourceId)
  else if (route.query.library === '1') await openLibrary()
  else await reattachPipeline()
})
onBeforeUnmount(() => {
  runController?.abort()
  stopElapsedTimer()
  if (typeTimer) clearInterval(typeTimer)
  window.removeEventListener('resize', onResize)
  if (mindmapChart) { mindmapChart.dispose(); mindmapChart = null }
})
</script>

<style scoped src="./styles/agent-workbench.css"></style>
