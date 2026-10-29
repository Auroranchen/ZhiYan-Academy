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

    <PageLayout :title="`${greetWord}，${teacherName} 老师`" :description="pendingSummary">
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

      <SectionCard title="今日教学工作流">
        <template #action>
          <span class="workflow__hint">从任务处理到学情干预，一步直达</span>
        </template>
        <ol class="workflow">
          <li v-for="(action, index) in workflowActions" :key="action.key">
            <button
              class="workflow__item"
              :class="{ 'is-primary': index === 0 }"
              type="button"
              @click="$router.push(action.path)"
            >
              <span class="workflow__no">{{ index + 1 }}</span>
              <Icon :icon="action.icon" class="workflow__icon" />
              <span class="workflow__copy">
                <b>{{ action.title }}</b>
                <small>{{ action.description }}</small>
              </span>
              <Icon icon="ph:caret-right-bold" class="workflow__arrow" />
            </button>
          </li>
        </ol>
      </SectionCard>

      <div class="home">
        <SectionCard
          title="最近发布的作业"
          action-label="查看全部"
          action-to="/teacher/homework"
          :loading="loading"
          :empty="!recentHomeworks.length"
          empty-text="还没有发布作业"
          empty-icon="ph:clipboard-text-duotone"
        >
          <FeedList :items="recentHomeworks" item-key="homeworkid" @select="openHomeworkStats">
            <template #title="{ item }">{{ item.title }}</template>
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
            action-to="/teacher/notice"
            :loading="loading"
            :skeleton-rows="3"
            :empty="!recentNotices.length"
            empty-text="暂无通知"
            empty-icon="ph:bell-slash-duotone"
          >
            <FeedList :items="recentNotices" item-key="noticeid" @select="$router.push('/teacher/notice')">
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
/** 教师端首页 */
import { computed, ref } from 'vue'
import { useStore } from 'vuex'
import { useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import { DateBadge, FeedList, PageLayout, QuickLinks, SectionCard, StatTile } from '@/components/ui'
import { usePullRefresh } from '@/composables/usePullRefresh'
import { countdown, formatDate } from '@/utils/format'
import { getCurrentSemester } from '@/utils/semester'
import { listCoursesByTeacher } from '@/api/course'
import { listHomeworkByCourse, listStudentHomeworkByTeacher } from '@/api/homework'
import { listTeacherExams } from '@/api/exam'
import { listNotices } from '@/api/notice'

const store = useStore()
const router = useRouter()

const teacherName = computed(() => store.state.user?.teachername || '教师')

const greetWord = (() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 12) return '早上好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})()

const loading = ref(true)
const stats = ref({ courseCount: 0, homeworkCount: 0, pendingCount: 0, examCount: 0 })
const recentHomeworks = ref([])
const recentNotices = ref([])

const pendingSummary = computed(() =>
  stats.value.pendingCount > 0
    ? `有 ${stats.value.pendingCount} 份作业等待批改`
    : '今日批改任务已清空，祝您工作顺利'
)

const statTiles = computed(() => [
  { label: '我的课程', value: stats.value.courseCount, icon: 'ph:book-open-duotone', to: '/teacher/course' },
  { label: '发布作业', value: stats.value.homeworkCount, icon: 'ph:file-text-duotone', to: '/teacher/homework' },
  {
    label: '待批改',
    value: stats.value.pendingCount,
    icon: 'ph:pencil-line-duotone',
    tone: stats.value.pendingCount > 0 ? 'warning' : 'default',
    to: '/teacher/studenthomework'
  },
  { label: '在线考试', value: stats.value.examCount, icon: 'ph:exam-duotone', to: '/teacher/exam' }
])

const quickLinks = [
  { label: '我的课程', icon: 'ph:book-open-duotone', to: '/teacher/course' },
  { label: '作业管理', icon: 'ph:file-text-duotone', to: '/teacher/homework' },
  { label: '批改作业', icon: 'ph:pencil-line-duotone', to: '/teacher/studenthomework' },
  { label: '课程资源', icon: 'ph:folder-open-duotone', to: '/teacher/resource' },
  { label: '在线考试', icon: 'ph:exam-duotone', to: '/teacher/exam' },
  { label: '作业查重', icon: 'ph:magnifying-glass-duotone', to: '/teacher/plagiarism' },
  { label: '统计报表', icon: 'ph:chart-bar-duotone', to: '/teacher/statistics' },
  { label: '学生考勤', icon: 'ph:calendar-duotone', to: '/teacher/attendance' },
  { label: 'AI 教学', icon: 'ph:sparkle-duotone', to: '/teacher/agent' }
]

const workflowActions = computed(() => [
  stats.value.pendingCount > 0
    ? {
        key: 'grade',
        title: `批改 ${stats.value.pendingCount} 份待处理作业`,
        description: '优先处理已提交但尚未评分的作业',
        icon: 'ph:exam-duotone',
        path: '/teacher/studenthomework'
      }
    : {
        key: 'publish',
        title: '发布下一项教学任务',
        description: '支持 AI 出题、题型配比和截止时间设置',
        icon: 'ph:plus-circle-duotone',
        path: '/teacher/homework'
      },
  {
    key: 'report',
    title: '查看班级学情与薄弱点',
    description: '根据作业、考试和错题生成班级周报',
    icon: 'ph:chart-line-up-duotone',
    path: '/teacher/class-report'
  },
  {
    key: 'agent',
    title: '生成并推送教学资源包',
    description: '多智能体生成讲义、题库、导图与实操案例',
    icon: 'ph:circles-three-plus-duotone',
    path: '/teacher/agent'
  }
])

const deadlineTone = (deadline) => {
  const { expired, urgent } = countdown(deadline)
  if (expired) return 'info'
  return urgent ? 'danger' : 'success'
}

const openHomeworkStats = (homework) => {
  router.push({ path: '/teacher/statistics', query: { homeworkid: homework.homeworkid } })
}

const loadTeaching = async () => {
  const teacherid = store.state.user?.teacherid
  if (!teacherid) return

  const semester = getCurrentSemester()
  const courseRes = await listCoursesByTeacher(teacherid).catch(() => null)
  const courses = courseRes?.data || []

  // 优先只统计当前学期；当前学期没有排课时退回全部课程
  const currentTerm = courses.filter((c) => c.semester === semester)
  const scoped = currentTerm.length ? currentTerm : courses
  stats.value.courseCount = scoped.length

  // 原实现按课程串行请求，课程多时首屏很慢，这里改为并发
  const perCourse = await Promise.all(
    scoped.map((c) =>
      listHomeworkByCourse(c.courseid)
        .then((res) => (res?.data || []).map((h) => ({ ...h, coursename: c.coursename })))
        .catch(() => [])
    )
  )

  const allHomeworks = perCourse.flat()
  stats.value.homeworkCount = allHomeworks.length
  recentHomeworks.value = [...allHomeworks]
    .sort((a, b) => {
      if (!a.deadline) return 1
      if (!b.deadline) return -1
      return new Date(b.deadline) - new Date(a.deadline)
    })
    .slice(0, 5)

  const [submissions, exams] = await Promise.all([
    listStudentHomeworkByTeacher(teacherid, { page: 1, pageSize: 10000, semester: semester || '' })
      .catch(() => null),
    listTeacherExams({ page: 1, pageSize: 1 }).catch(() => null)
  ])

  // status 为 1 表示已提交待批改
  stats.value.pendingCount = (submissions?.data || []).filter((r) => Number(r.status) === 1).length
  stats.value.examCount = exams?.total || 0
}

const refreshAll = async () => {
  const [, notices] = await Promise.all([
    loadTeaching().catch(() => {}),
    listNotices({ page: 1, pageSize: 5 }).catch(() => null)
  ])
  recentNotices.value = notices?.data || []
}

const { pullDistance, refreshing, containerRef } = usePullRefresh(refreshAll)

refreshAll().finally(() => { loading.value = false })
</script>

<style scoped>
/* ===== 教学工作流 ===== */
.workflow__hint {
  font-size: var(--zy-font-size-xs);
  color: var(--zy-text-tertiary);
}

.workflow {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: var(--zy-space-3);
  margin: 0;
  padding: 0;
  list-style: none;
}

.workflow__item {
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

.workflow__item:hover {
  border-color: var(--zy-brand-border);
  background: var(--zy-brand-soft);
}

.workflow__item.is-primary {
  border-color: var(--zy-brand-border);
  background: var(--zy-brand-soft);
}

.workflow__no {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  border-radius: var(--zy-radius-full);
  background: var(--zy-brand-base);
  color: var(--zy-brand-on);
  font-size: var(--zy-font-size-xs);
  font-weight: var(--zy-font-weight-semibold);
  line-height: 20px;
  text-align: center;
}

.workflow__icon {
  flex-shrink: 0;
  font-size: 22px;
  color: var(--zy-brand-base);
}

.workflow__copy {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  flex: 1;
}

.workflow__copy b {
  font-size: var(--zy-font-size-base);
  font-weight: var(--zy-font-weight-medium);
  color: var(--zy-text-primary);
}

.workflow__copy small {
  font-size: var(--zy-font-size-xs);
  color: var(--zy-text-tertiary);
}

.workflow__arrow {
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
