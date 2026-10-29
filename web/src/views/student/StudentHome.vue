<template>
  <div ref="containerRef">
    <div class="pull-refresh-indicator" :style="{ height: pullDistance + 'px' }">
      <template v-if="refreshing">
        <span class="pull-spinner"></span>
        <span>刷新中...</span>
      </template>
      <template v-else-if="pullDistance > 0">
        <Icon icon="ph:arrow-down-bold" class="pull-arrow" :class="{ flipped: pullDistance >= 30 }" />
        <span>{{ pullDistance >= 30 ? '松手刷新' : '下拉刷新' }}</span>
      </template>
    </div>

    <PageLayout :title="`${greetWord}，${studentName}`" :description="subtitle">
      <template #actions>
        <DateBadge />
      </template>

      <template #summary>
        <StatTile
          v-for="item in statTiles"
          :key="item.label"
          :label="item.label"
          :value="item.value"
          :icon="item.icon"
          :tone="item.tone"
          :to="item.to"
        />
      </template>

      <SectionCard title="今日学习建议">
        <template #action>
          <span class="focus__hint">根据作业、画像与学习路径动态排序</span>
        </template>
        <ol class="focus">
          <li v-for="(action, index) in recommendedActions" :key="action.key">
            <button
              class="focus__item"
              :class="{ 'is-primary': index === 0 }"
              type="button"
              @click="$router.push(action.path)"
            >
              <span class="focus__no">{{ String(index + 1).padStart(2, '0') }}</span>
              <Icon :icon="action.icon" class="focus__icon" />
              <span class="focus__copy">
                <b>{{ action.title }}</b>
                <small>{{ action.description }}</small>
              </span>
              <Icon icon="ph:arrow-up-right-bold" class="focus__arrow" />
            </button>
          </li>
        </ol>
      </SectionCard>

      <div class="home">
        <SectionCard
          title="待完成作业"
          :count="stats.pendingCount || null"
          action-label="查看全部"
          action-to="/student/homework/course"
          :loading="loading"
          :empty="!recentHomeworks.length"
          empty-text="太棒了，暂无待完成作业"
          empty-icon="ph:confetti-duotone"
        >
          <FeedList :items="recentHomeworks" item-key="homeworkid" @select="openHomework">
            <template #title="{ item }">{{ item.homeworkname }}</template>
            <template #sub="{ item }">{{ item.coursename }}</template>
            <template #meta="{ item }">
              <el-tag :type="deadlineTone(item.deadline)" size="small" disable-transitions>
                {{ countdown(item.deadline).text }}
              </el-tag>
            </template>
          </FeedList>
        </SectionCard>

        <div class="home__aside">
          <SectionCard title="快捷入口">
            <QuickLinks :items="quickLinks" />
          </SectionCard>

          <SectionCard
            title="最新通知"
            action-label="更多"
            action-to="/student/notice"
            :loading="loading"
            :skeleton-rows="3"
            :empty="!recentNotices.length"
            empty-text="暂无通知"
            empty-icon="ph:bell-slash-duotone"
          >
            <FeedList :items="recentNotices" item-key="noticeid" @select="$router.push('/student/notice')">
              <template #title="{ item }">{{ item.title }}</template>
              <template #meta="{ item }">{{ formatDate(item.createtime) }}</template>
            </FeedList>
          </SectionCard>
        </div>
      </div>
    </PageLayout>
  </div>
</template>

<script setup>
/** 学生端首页 */
import { computed, ref } from 'vue'
import { useStore } from 'vuex'
import { useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import { DateBadge, FeedList, PageLayout, QuickLinks, SectionCard, StatTile } from '@/components/ui'
import { usePullRefresh } from '@/composables/usePullRefresh'
import { countdown, formatDate } from '@/utils/format'
import { listCoursesByClass } from '@/api/course'
import { listHomeworkByClass, listStudentHomeworkByStudent } from '@/api/homework'
import { listNotices } from '@/api/notice'
import { getLearningProfile, listAgentLibrary, listLearningPaths } from '@/api/learning'

const store = useStore()
const router = useRouter()

const studentName = computed(() => store.state.user?.studentname || '学生')
const className = computed(() => store.state.user?.classname || '')

const greetWord = (() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 12) return '早上好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})()

const loading = ref(true)
const stats = ref({ courseCount: 0, pendingCount: 0, submittedCount: 0 })
const recentHomeworks = ref([])
const recentNotices = ref([])
const learningSignals = ref({ profileCompletion: 0, activePlan: null, resourceCount: 0 })

const subtitle = computed(() => {
  const task = stats.value.pendingCount > 0
    ? `还有 ${stats.value.pendingCount} 项作业待完成，加油`
    : '今日作业都完成了，太棒了'
  return className.value ? `${className.value} · ${task}` : task
})

const statTiles = computed(() => [
  { label: '在学课程', value: stats.value.courseCount, icon: 'ph:book-open-duotone', to: '/student/course' },
  {
    label: '待完成作业',
    value: stats.value.pendingCount,
    icon: 'ph:clock-countdown-duotone',
    tone: stats.value.pendingCount > 0 ? 'warning' : 'success',
    to: '/student/homework/course'
  },
  { label: '已提交作业', value: stats.value.submittedCount, icon: 'ph:check-circle-duotone', to: '/student/myhomework' }
])

const quickLinks = [
  { label: '我的课程', icon: 'ph:book-open-duotone', to: '/student/course' },
  { label: '我的作业', icon: 'ph:file-text-duotone', to: '/student/homework/course' },
  { label: '课程资源', icon: 'ph:folder-open-duotone', to: '/student/resource' },
  { label: '课程评价', icon: 'ph:star-duotone', to: '/student/evaluation' },
  { label: '在线考试', icon: 'ph:exam-duotone', to: '/student/exam' },
  { label: '错题本', icon: 'ph:note-pencil-duotone', to: '/student/wrongbook' },
  { label: '学习路径', icon: 'ph:path-duotone', to: '/student/learning-path' },
  { label: '激励中心', icon: 'ph:trophy-duotone', to: '/student/learning-center' },
  { label: '课堂签到', icon: 'ph:hand-fist-duotone', to: '/student/checkin' }
]

/** 建议按紧急度排序，最多给三条，避免首页变成任务清单 */
const recommendedActions = computed(() => {
  const actions = []
  const firstHomework = recentHomeworks.value[0]

  if (firstHomework) {
    actions.push({
      key: 'homework',
      title: `先完成「${firstHomework.homeworkname}」`,
      description: `${firstHomework.coursename || '课程作业'} · ${countdown(firstHomework.deadline).text}`,
      icon: 'ph:pencil-line-duotone',
      path: { path: '/student/homework/submit', query: { id: firstHomework.homeworkid } }
    })
  }

  if (learningSignals.value.profileCompletion < 60) {
    actions.push({
      key: 'profile',
      title: '完善学习画像',
      description: `当前完善度 ${learningSignals.value.profileCompletion}% · 让资源生成更懂你`,
      icon: 'ph:identification-card-duotone',
      path: '/student/learning-profile'
    })
  }

  if (learningSignals.value.activePlan) {
    actions.push({
      key: 'path',
      title: '继续执行学习路径',
      description: `${learningSignals.value.activePlan.title} · ${learningSignals.value.activePlan.progress}% 已完成`,
      icon: 'ph:path-duotone',
      path: '/student/learning-path'
    })
  } else if (stats.value.submittedCount > 0) {
    actions.push({
      key: 'newPath',
      title: '生成本周学习路径',
      description: '根据错题、掌握度和画像规划下一步',
      icon: 'ph:signpost-duotone',
      path: '/student/learning-path'
    })
  }

  actions.push({
    key: 'agent',
    title: learningSignals.value.resourceCount > 0 ? '查看个性化资源包' : '生成个性化资源包',
    description: learningSignals.value.resourceCount > 0
      ? `资源库已有 ${learningSignals.value.resourceCount} 份学习包`
      : '8 个智能体协作生成讲义、题库与实操案例',
    icon: 'ph:circles-three-plus-duotone',
    path: '/student/agent'
  })

  return actions.slice(0, 3)
})

const deadlineTone = (deadline) => {
  const { expired, urgent } = countdown(deadline)
  if (expired) return 'info'
  return urgent ? 'danger' : 'success'
}

const openHomework = (homework) => {
  router.push({ path: '/student/homework/submit', query: { id: homework.homeworkid } })
}

const loadCoursework = async () => {
  const { classid, studentid } = store.state.user || {}
  if (!classid) return

  const [courseRes, homeworkRes, submissionRes] = await Promise.all([
    listCoursesByClass(classid).catch(() => null),
    listHomeworkByClass(classid, { page: 1, pageSize: 1000 }).catch(() => null),
    studentid
      ? listStudentHomeworkByStudent(studentid, { page: 1, pageSize: 1000 }).catch(() => null)
      : Promise.resolve(null)
  ])

  stats.value.courseCount = (courseRes?.data || []).length

  const submitted = (submissionRes?.data || []).filter((h) => h.status >= 1)
  stats.value.submittedCount = submitted.length

  const submittedIds = new Set(submitted.map((s) => s.homeworkid))
  const nowMs = Date.now()
  const pending = (homeworkRes?.data || []).filter(
    (h) => !submittedIds.has(h.homeworkid) && (!h.deadline || new Date(h.deadline).getTime() > nowMs)
  )

  stats.value.pendingCount = pending.length
  recentHomeworks.value = [...pending]
    .sort((a, b) => {
      if (!a.deadline) return 1
      if (!b.deadline) return -1
      return new Date(a.deadline) - new Date(b.deadline)
    })
    .slice(0, 5)
}

const planProgress = (plan) => {
  if (!plan) return 0
  let content = {}
  let done = {}
  try { content = JSON.parse(plan.content || '{}') } catch { /* 内容损坏时按 0 进度处理 */ }
  try { done = JSON.parse(plan.progressJson || '{}') } catch { /* 同上 */ }

  const total = (content.stages || []).reduce((sum, stage) => sum + (stage.tasks || []).length, 0)
  const completed = Object.values(done).filter(Boolean).length
  return total > 0 ? Math.min(100, Math.round((completed * 100) / total)) : 0
}

/** 这三个信号任一失败都只影响建议排序，不应阻断首页 */
const loadLearningSignals = async () => {
  const [profile, paths, library] = await Promise.allSettled([
    getLearningProfile(),
    listLearningPaths(),
    listAgentLibrary()
  ])

  if (profile.status === 'fulfilled') {
    const dimensions = profile.value?.data?.dimensions || {}
    const scores = Object.values(dimensions).map((v) => Number(v?.score) || 0)
    learningSignals.value.profileCompletion = scores.length
      ? Math.round(scores.reduce((a, b) => a + b, 0) / scores.length)
      : 0
  }

  if (paths.status === 'fulfilled') {
    const active = (paths.value?.data || []).find((plan) => plan.status !== 0)
    learningSignals.value.activePlan = active ? { ...active, progress: planProgress(active) } : null
  }

  if (library.status === 'fulfilled') {
    learningSignals.value.resourceCount = (library.value?.data || []).length
  }
}

const refreshAll = async () => {
  const [, notices] = await Promise.all([
    loadCoursework().catch(() => {}),
    listNotices({ page: 1, pageSize: 5 }).catch(() => null),
    loadLearningSignals()
  ])
  recentNotices.value = notices?.data || []
}

const { pullDistance, refreshing, containerRef } = usePullRefresh(refreshAll)

refreshAll().finally(() => { loading.value = false })
</script>

<style scoped>
/* ===== 学习建议 ===== */
.focus__hint {
  font-size: var(--zy-font-size-xs);
  color: var(--zy-text-tertiary);
}

.focus {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: var(--zy-space-3);
  margin: 0;
  padding: 0;
  list-style: none;
}

.focus__item {
  display: flex;
  align-items: center;
  gap: var(--zy-space-3);
  width: 100%;
  padding: var(--zy-space-3);
  border: 1px solid var(--zy-border-subtle);
  border-radius: var(--zy-radius-md);
  background: var(--zy-bg-subtle);
  font-family: inherit;
  text-align: left;
  cursor: pointer;
  transition: border-color var(--zy-duration-fast) var(--zy-ease-standard),
              background-color var(--zy-duration-fast) var(--zy-ease-standard);
}

.focus__item:hover,
.focus__item.is-primary {
  border-color: var(--zy-brand-border);
  background: var(--zy-brand-soft);
}

.focus__no {
  flex-shrink: 0;
  font-size: var(--zy-font-size-xs);
  font-weight: var(--zy-font-weight-semibold);
  color: var(--zy-text-disabled);
  font-variant-numeric: tabular-nums;
}

.focus__icon {
  flex-shrink: 0;
  font-size: 22px;
  color: var(--zy-brand-base);
}

.focus__copy {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  flex: 1;
}

.focus__copy b {
  font-size: var(--zy-font-size-base);
  font-weight: var(--zy-font-weight-medium);
  color: var(--zy-text-primary);
}

.focus__copy small {
  font-size: var(--zy-font-size-xs);
  color: var(--zy-text-tertiary);
}

.focus__arrow {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--zy-text-disabled);
}

/* ===== 双列内容 ===== */
.home {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(280px, 1fr);
  gap: var(--zy-stack-gap);
  align-items: start;
}

.home__aside {
  display: flex;
  flex-direction: column;
  gap: var(--zy-stack-gap);
}

@media screen and (max-width: 1024px) {
  .home {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
