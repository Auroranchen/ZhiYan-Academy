<template>
  <PageLayout
    title="AI 学习路径"
    description="结合错题本、各课程掌握度与学习画像，智能规划分阶段的学习路径。"
  >
    <template #actions>
      <el-button type="primary" :loading="generating" @click="generate">生成新路径</el-button>
    </template>

    <el-alert
      v-if="generating"
      type="info"
      :closable="false"
      :title="generatingStep"
      class="tip"
    />

    <el-row :gutter="16">
      <el-col :xs="24" :lg="7">
        <SectionCard title="我的学习计划" :count="plans.length">
          <el-empty v-if="!plans.length" description="暂无计划，点击生成" :image-size="70" />

          <button
            v-for="plan in plans"
            :key="plan.id"
            type="button"
            class="plan"
            :class="{ 'is-active': current?.id === plan.id }"
            @click="select(plan)"
          >
            <span class="plan__title">{{ plan.title }}</span>
            <span class="plan__meta">
              {{ formatDate(plan.createTime) }}
              <el-tag v-if="plan.status === 0" size="small" type="info" disable-transitions>
                已归档
              </el-tag>
            </span>
          </button>
        </SectionCard>
      </el-col>

      <el-col :xs="24" :lg="17">
        <el-empty v-if="!current" description="请选择或生成一份学习计划" />

        <SectionCard v-else :title="content.title || current.title">
          <template #action>
            <div class="detail-actions">
              <el-button link type="primary" @click="exportPlan">导出</el-button>
              <el-button v-if="current.status !== 0" link @click="archive">归档</el-button>
              <el-button link type="danger" @click="remove">删除</el-button>
            </div>
          </template>

          <p class="summary">
            <Icon icon="ph:chart-line-up-duotone" :width="20" :height="20" />
            <span>{{ content.summary || current.summary }}</span>
          </p>

          <div class="progress">
            <span class="progress__label">完成进度</span>
            <el-progress :percentage="percent" :stroke-width="16" text-inside class="progress__bar" />
            <span class="progress__count">{{ doneCount }} / {{ totalTasks }} 项</span>
          </div>

          <el-timeline>
            <el-timeline-item
              v-for="(stage, si) in content.stages"
              :key="si"
              :timestamp="`阶段 ${si + 1} · 约 ${stage.days || '—'} 天`"
              :type="STAGE_TONE[si % STAGE_TONE.length]"
              placement="top"
              size="large"
            >
              <div class="stage">
                <h4 class="stage__name">{{ stage.name }}</h4>

                <p class="stage__row"><span class="label">目标</span>{{ stage.goal }}</p>

                <div v-if="stage.knowledgePoints?.length" class="stage__row">
                  <span class="label">知识点</span>
                  <el-tag
                    v-for="(kp, ki) in stage.knowledgePoints"
                    :key="ki"
                    size="small"
                    class="stage__kp"
                    disable-transitions
                  >
                    {{ kp }}
                  </el-tag>
                </div>

                <div v-if="stage.tasks?.length" class="stage__row">
                  <span class="label">学习任务</span>
                  <div class="tasks">
                    <el-checkbox
                      v-for="(task, ti) in stage.tasks"
                      :key="ti"
                      :model-value="isDone(si, ti)"
                      :class="{ 'is-done': isDone(si, ti) }"
                      @change="toggleTask(si, ti)"
                    >
                      {{ task }}
                    </el-checkbox>
                  </div>
                </div>
              </div>
            </el-timeline-item>
          </el-timeline>

          <div v-if="content.suggestions?.length" class="suggestions">
            <h4 class="suggestions__title">
              <Icon icon="ph:lightbulb-duotone" /> 学习建议
            </h4>
            <ul>
              <li v-for="(s, i) in content.suggestions" :key="i">{{ s }}</li>
            </ul>
          </div>
        </SectionCard>
      </el-col>
    </el-row>
  </PageLayout>
</template>

<script setup>
/** AI 学习路径（学生端） */
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Icon } from '@iconify/vue'
import { PageLayout, SectionCard } from '@/components/ui'
import {
  archiveLearningPath,
  deleteLearningPath,
  generateLearningPath,
  listLearningPaths,
  saveLearningPathProgress
} from '@/api/learning'
import { formatDate } from '@/utils/format'
import { confirmDelete } from '@/utils/confirm'

const STAGE_TONE = ['primary', 'success', 'warning', 'danger']

/** 生成要跑十几到几十秒，分步提示避免用户以为卡死 */
const GENERATING_STEPS = [
  '归集最新错题与掌握度数据…',
  '读取学习画像，分析认知风格与学习节奏…',
  'AI 正在规划分阶段学习路径（约需 10-60 秒）…',
  '校验并保存学习计划…'
]
const STEP_INTERVAL = 6000
const PROGRESS_SAVE_DELAY = 600

const EMPTY_CONTENT = { title: '', summary: '', stages: [], suggestions: [] }

const route = useRoute()

const plans = ref([])
const current = ref(null)
const content = reactive({ ...EMPTY_CONTENT })
const done = ref({})

const generating = ref(false)
const generatingStep = ref('')

/* ---------- 计划加载 ---------- */

const parseContent = (plan) => {
  let parsed = {}
  try {
    parsed = JSON.parse(plan.content || '{}')
  } catch {
    // 老数据可能不是合法 JSON，按空计划展示
  }
  Object.assign(content, EMPTY_CONTENT, parsed)
}

const progressKey = (planId) => `learnpath_progress_${planId}`

/** 进度以服务端为准，localStorage 只在服务端没有记录时兜底 */
const loadProgress = (planId, serverProgress) => {
  if (serverProgress) {
    try {
      done.value = JSON.parse(serverProgress)
      return
    } catch {
      // 服务端数据损坏时退回本地
    }
  }
  try {
    done.value = JSON.parse(localStorage.getItem(progressKey(planId)) || '{}')
  } catch {
    done.value = {}
  }
}

const select = (plan) => {
  if (!plan) return
  current.value = plan
  parseContent(plan)
  loadProgress(plan.id, plan.progressJson)
}

const loadPlans = async () => {
  const res = await listLearningPaths().catch(() => null)
  plans.value = res?.data || []
  if (!plans.value.length || current.value) return

  const planId = Number(route.query.planId)
  const target = Number.isFinite(planId) ? plans.value.find((p) => Number(p.id) === planId) : null
  select(target || plans.value[0])
}

onMounted(loadPlans)

/* ---------- 任务勾选 ---------- */

const taskKey = (stageIndex, taskIndex) => `${stageIndex}-${taskIndex}`
const isDone = (stageIndex, taskIndex) => !!done.value[taskKey(stageIndex, taskIndex)]

let saveTimer = null

const persistProgress = () => {
  if (!current.value) return

  const planId = current.value.id
  const progress = { ...done.value }
  localStorage.setItem(progressKey(planId), JSON.stringify(progress))

  // 连续勾选会产生大量请求，防抖后再同步
  clearTimeout(saveTimer)
  saveTimer = setTimeout(() => {
    saveLearningPathProgress(planId, progress).catch(() => {})
  }, PROGRESS_SAVE_DELAY)
}

const toggleTask = (stageIndex, taskIndex) => {
  const key = taskKey(stageIndex, taskIndex)
  done.value = { ...done.value, [key]: !done.value[key] }
  persistProgress()
}

const totalTasks = computed(() =>
  (content.stages || []).reduce((sum, stage) => sum + (stage.tasks?.length || 0), 0)
)

const doneCount = computed(() =>
  (content.stages || []).reduce(
    (sum, stage, si) => sum + (stage.tasks || []).filter((_, ti) => isDone(si, ti)).length,
    0
  )
)

const percent = computed(() =>
  totalTasks.value ? Math.round((doneCount.value * 100) / totalTasks.value) : 0
)

/* ---------- 操作 ---------- */

let stepTimer = null

const generate = async () => {
  generating.value = true

  let step = 0
  generatingStep.value = GENERATING_STEPS[0]
  stepTimer = setInterval(() => {
    step = Math.min(step + 1, GENERATING_STEPS.length - 1)
    generatingStep.value = GENERATING_STEPS[step]
  }, STEP_INTERVAL)

  try {
    const res = await generateLearningPath()
    ElMessage.success(res?.msg || '生成成功')
    current.value = null
    await loadPlans()
    if (res?.data) select(res.data)
  } catch (e) {
    ElMessage.error(e?.msg || e?.message || '生成失败，请稍后重试')
  } finally {
    clearInterval(stepTimer)
    generating.value = false
  }
}

const archive = async () => {
  await archiveLearningPath(current.value.id)
  ElMessage.success('已归档')
  await loadPlans()
}

const remove = async () => {
  const ok = await confirmDelete('确定删除该学习计划吗？删除后无法恢复。')
  if (!ok) return

  await deleteLearningPath(current.value.id)
  ElMessage.success('已删除')
  current.value = null
  await loadPlans()
}

const exportPlan = () => {
  const lines = [
    content.title || current.value?.title || '学习路径',
    `学情：${content.summary || current.value?.summary || ''}`,
    ''
  ]

  ;(content.stages || []).forEach((stage, si) => {
    lines.push(`【阶段${si + 1}】${stage.name}（约 ${stage.days || '—'} 天）`)
    if (stage.goal) lines.push(`  目标：${stage.goal}`)
    if (stage.knowledgePoints?.length) lines.push(`  知识点：${stage.knowledgePoints.join('、')}`)
    ;(stage.tasks || []).forEach((task, ti) => lines.push(`  [${isDone(si, ti) ? '√' : ' '}] ${task}`))
    lines.push('')
  })

  if (content.suggestions?.length) {
    lines.push('学习建议：')
    content.suggestions.forEach((s) => lines.push(`  · ${s}`))
  }

  // BOM 让 Windows 记事本按 UTF-8 打开，否则中文乱码
  const blob = new Blob(['\ufeff' + lines.join('\n')], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${content.title || '学习路径'}.txt`
  link.click()
  URL.revokeObjectURL(url)
}

onBeforeUnmount(() => {
  clearTimeout(saveTimer)
  clearInterval(stepTimer)
})
</script>

<style scoped>
.tip {
  margin-bottom: var(--zy-space-4);
}

/* ===== 计划列表 ===== */
.plan {
  display: flex;
  flex-direction: column;
  gap: var(--zy-space-1);
  width: 100%;
  padding: var(--zy-space-3);
  margin-bottom: var(--zy-space-2);
  font: inherit;
  text-align: left;
  background: none;
  border: 1px solid var(--zy-border-subtle);
  border-radius: var(--zy-radius-md);
  cursor: pointer;
  transition: border-color var(--zy-duration-fast), background var(--zy-duration-fast);
}

.plan:hover {
  border-color: var(--zy-brand-border);
}

.plan.is-active {
  background: var(--zy-brand-soft);
  border-color: var(--zy-brand-base);
}

.plan__title {
  font-size: var(--zy-font-size-base);
  font-weight: var(--zy-font-weight-semibold);
  color: var(--zy-text-primary);
}

.plan__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: var(--zy-font-size-xs);
  color: var(--zy-text-tertiary);
}

/* ===== 计划详情 ===== */
.detail-actions {
  display: flex;
  gap: var(--zy-space-2);
}

.summary {
  display: flex;
  align-items: center;
  gap: var(--zy-space-2);
  margin: 0 0 var(--zy-space-4);
  padding: var(--zy-space-3);
  font-size: var(--zy-font-size-base);
  color: var(--zy-brand-base);
  background: var(--zy-brand-soft);
  border-radius: var(--zy-radius-md);
}

.progress {
  display: flex;
  align-items: center;
  gap: var(--zy-space-3);
  margin-bottom: var(--zy-space-5);
}

.progress__bar {
  flex: 1;
}

.progress__label,
.progress__count {
  font-size: var(--zy-font-size-sm);
  color: var(--zy-text-tertiary);
  white-space: nowrap;
}

.stage {
  padding: var(--zy-space-4);
  background: var(--zy-bg-sunken);
  border-radius: var(--zy-radius-md);
}

.stage__name {
  margin: 0 0 var(--zy-space-2);
  font-size: var(--zy-font-size-md);
  font-weight: var(--zy-font-weight-semibold);
  color: var(--zy-text-primary);
}

.stage__row {
  margin: 0 0 var(--zy-space-2);
  font-size: var(--zy-font-size-sm);
  color: var(--zy-text-secondary);
}

.stage__kp {
  margin: 2px var(--zy-space-1) 2px 0;
}

.label {
  display: inline-block;
  padding: 1px var(--zy-space-2);
  margin-right: var(--zy-space-2);
  font-size: var(--zy-font-size-xs);
  color: var(--zy-text-tertiary);
  background: var(--zy-bg-hover);
  border-radius: var(--zy-radius-full);
}

.tasks {
  display: flex;
  flex-direction: column;
  gap: var(--zy-space-1);
  margin-top: var(--zy-space-1);
}

.tasks .is-done :deep(.el-checkbox__label) {
  color: var(--zy-text-disabled);
  text-decoration: line-through;
}

.suggestions {
  padding: var(--zy-space-4);
  margin-top: var(--zy-space-4);
  background: var(--zy-warning-soft);
  border-radius: var(--zy-radius-md);
}

.suggestions__title {
  display: flex;
  align-items: center;
  gap: var(--zy-space-1);
  margin: 0 0 var(--zy-space-2);
  font-size: var(--zy-font-size-base);
  font-weight: var(--zy-font-weight-semibold);
  color: var(--zy-warning-base);
}

.suggestions ul {
  margin: 0;
  padding-left: var(--zy-space-5);
  font-size: var(--zy-font-size-sm);
  line-height: var(--zy-line-height-relaxed);
  color: var(--zy-text-secondary);
}
</style>
