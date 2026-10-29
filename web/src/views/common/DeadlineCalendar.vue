<template>
  <PageLayout title="截止日历" description="作业与考试的截止时间总览，红色代表考试，橙色代表作业。">
    <template #actions>
      <div class="legend">
        <span v-for="l in LEGEND" :key="l.status" class="legend__item">
          <i class="legend__dot" :class="`is-${l.status}`"></i>{{ l.label }}
        </span>
      </div>
    </template>

    <div v-loading="loading" class="layout">
      <div class="layout__calendar">
        <el-calendar v-model="calendarDate">
          <template #date-cell="{ data }">
            <div class="cell" :class="{ 'is-today': data.day === todayKey }">
              <span class="cell__day">{{ Number(data.day.split('-')[2]) }}</span>
              <div v-if="dayMap[data.day]" class="cell__items">
                <div
                  v-for="(item, index) in dayMap[data.day].slice(0, MAX_CELL_ITEMS)"
                  :key="index"
                  class="cell__item"
                  :class="`is-${toneOf(item)}`"
                  :title="item.title"
                >
                  {{ item.typeLabel }} · {{ item.title }}
                </div>
                <span v-if="dayMap[data.day].length > MAX_CELL_ITEMS" class="cell__more">
                  +{{ dayMap[data.day].length - MAX_CELL_ITEMS }}
                </span>
              </div>
            </div>
          </template>
        </el-calendar>
      </div>

      <aside class="layout__side">
        <h3 class="side__title">即将截止（7 天内）</h3>

        <el-empty v-if="!upcoming.length" description="近期没有截止项" :image-size="80" />

        <div v-else class="upcoming">
          <button
            v-for="(item, index) in upcoming"
            :key="index"
            type="button"
            class="task"
            :class="`is-${toneOf(item)}`"
            @click="goTo(item)"
          >
            <div class="task__head">
              <el-tag
                :type="item.type === 'exam' ? 'danger' : 'warning'"
                size="small"
                effect="plain"
                disable-transitions
              >
                {{ item.typeLabel }}
              </el-tag>
              <span class="task__countdown" :class="{ 'is-urgent': item.hoursLeft <= URGENT_HOURS }">
                {{ countdownText(item) }}
              </span>
            </div>

            <p class="task__title">{{ item.title }}</p>

            <div class="task__meta">
              <span>{{ item.course || '—' }}</span>
              <span>{{ formatDate(item.due, 'minute') }}</span>
            </div>
          </button>
        </div>
      </aside>
    </div>
  </PageLayout>
</template>

<script setup>
/** 截止日历：教师与学生共用，按角色加载各自的作业与考试 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useStore } from 'vuex'
import { ElNotification } from 'element-plus'
import { PageLayout } from '@/components/ui'
import { listHomeworkByClass, listHomeworkByTeacher, listStudentHomeworkByStudent } from '@/api/homework'
import { listStudentExams, listTeacherExams } from '@/api/exam'
import { dateKey, formatDate, toDate } from '@/utils/format'

const MAX_CELL_ITEMS = 3
const URGENT_HOURS = 24
const UPCOMING_DAYS = 7
const HOUR = 3600 * 1000
const BULK_PAGE = { page: 1, pageSize: 1000 }

const LEGEND = [
  { status: 'homework', label: '作业' },
  { status: 'exam', label: '考试' },
  { status: 'done', label: '已完成' },
  { status: 'closed', label: '已截止' }
]

const router = useRouter()
const store = useStore()

const loading = ref(false)
const calendarDate = ref(new Date())
const items = ref([])

const role = computed(() => store.state.user?.role || 'student')
const todayKey = computed(() => dateKey(new Date()))

const makeItem = (type, title, course, due, finished) => ({
  type,
  title: title || '(未命名)',
  typeLabel: type === 'exam' ? '考试' : '作业',
  course,
  due,
  status: finished ? 'done' : due.getTime() < Date.now() ? 'closed' : 'pending',
  hoursLeft: (due.getTime() - Date.now()) / HOUR
})

/** 已完成、已截止优先于类型，决定条目的配色 */
const toneOf = (item) => (item.status === 'pending' ? item.type : item.status)

const loadStudentItems = async () => {
  const { classid, studentid } = store.state.user || {}
  const list = []

  const [homework, submissions, exams] = await Promise.all([
    classid ? listHomeworkByClass(classid, BULK_PAGE).catch(() => null) : null,
    studentid ? listStudentHomeworkByStudent(studentid, BULK_PAGE).catch(() => null) : null,
    listStudentExams().catch(() => null)
  ])

  // status >= 1 表示已提交（0 为草稿）
  const submitted = new Set(
    (submissions?.data || [])
      .filter((s) => s.homeworkid != null && Number(s.status) >= 1)
      .map((s) => s.homeworkid)
  )

  ;(homework?.data || []).forEach((hw) => {
    const due = toDate(hw.deadline)
    if (due) list.push(makeItem('homework', hw.title, hw.coursename, due, submitted.has(hw.homeworkid)))
  })
  ;(exams?.data || []).forEach((ex) => {
    const due = toDate(ex.endtime) || toDate(ex.starttime)
    if (due) list.push(makeItem('exam', ex.title, ex.coursename, due, Number(ex.recordStatus) >= 1))
  })

  return list
}

const loadTeacherItems = async () => {
  const teacherid = store.state.user?.teacherid
  const list = []

  const [homework, exams] = await Promise.all([
    teacherid ? listHomeworkByTeacher(teacherid, BULK_PAGE).catch(() => null) : null,
    listTeacherExams(BULK_PAGE).catch(() => null)
  ])

  ;(homework?.data || []).forEach((hw) => {
    const due = toDate(hw.deadline)
    if (due) list.push(makeItem('homework', hw.title, hw.coursename, due, false))
  })
  ;(exams?.data || []).forEach((ex) => {
    const due = toDate(ex.endtime) || toDate(ex.starttime)
    if (due) list.push(makeItem('exam', ex.title, ex.coursename, due, false))
  })

  return list
}

const dayMap = computed(() => {
  const map = {}
  items.value.forEach((item) => {
    const key = dateKey(item.due)
    ;(map[key] ||= []).push(item)
  })
  return map
})

/** 保留昨天的条目，方便学生看到刚错过的截止 */
const upcoming = computed(() => {
  const now = Date.now()
  const from = now - URGENT_HOURS * HOUR
  const to = now + UPCOMING_DAYS * 24 * HOUR
  return items.value
    .filter((item) => item.due.getTime() >= from && item.due.getTime() <= to)
    .sort((a, b) => a.due - b.due)
})

const countdownText = (item) => {
  if (item.status === 'done') return '已完成'
  if (item.status === 'closed') return '已截止'
  if (item.hoursLeft < 1) return '不足 1 小时'
  if (item.hoursLeft < 24) return `剩 ${Math.floor(item.hoursLeft)} 小时`
  return `剩 ${Math.floor(item.hoursLeft / 24)} 天`
}

const goTo = (item) => {
  const teacher = role.value === 'teacher'
  if (item.type === 'exam') router.push(teacher ? '/teacher/exam' : '/student/exam')
  else router.push(teacher ? '/teacher/homework' : '/student/homework/course')
}

const notifyDueSoon = () => {
  if (role.value === 'teacher') return
  const soon = items.value.filter((i) => i.status === 'pending' && i.hoursLeft <= URGENT_HOURS)
  if (!soon.length) return

  ElNotification({
    title: '截止提醒',
    message: `你有 ${soon.length} 项将在 24 小时内截止，请尽快完成`,
    type: 'warning',
    duration: 6000,
    position: 'bottom-right'
  })
}

onMounted(async () => {
  loading.value = true
  try {
    items.value = role.value === 'teacher' ? await loadTeacherItems() : await loadStudentItems()
    notifyDueSoon()
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.legend {
  display: flex;
  gap: var(--zy-space-4);
  font-size: var(--zy-font-size-xs);
  color: var(--zy-text-tertiary);
}

.legend__item {
  display: inline-flex;
  align-items: center;
  gap: var(--zy-space-1);
}

.legend__dot {
  width: 8px;
  height: 8px;
  border-radius: var(--zy-radius-full);
}

.legend__dot.is-homework {
  background: var(--zy-warning-base);
}

.legend__dot.is-exam {
  background: var(--zy-danger-base);
}

.legend__dot.is-done {
  background: var(--zy-success-base);
}

.legend__dot.is-closed {
  background: var(--zy-text-disabled);
}

.layout {
  display: flex;
  align-items: flex-start;
  gap: var(--zy-space-4);
}

.layout__calendar {
  flex: 1;
  min-width: 0;
}

.layout__side {
  width: 300px;
  flex-shrink: 0;
  padding: var(--zy-space-4);
  background: var(--zy-bg-sunken);
  border: 1px solid var(--zy-border-subtle);
  border-radius: var(--zy-radius-lg);
}

/* ===== 日历单元格 ===== */
.cell {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 64px;
}

.cell__day {
  font-size: var(--zy-font-size-sm);
  color: var(--zy-text-secondary);
}

.cell.is-today .cell__day {
  font-weight: var(--zy-font-weight-bold);
  color: var(--zy-brand-base);
}

.cell__items {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-top: 2px;
  overflow: hidden;
}

.cell__item {
  padding: 1px 4px;
  font-size: var(--zy-font-size-xs);
  line-height: var(--zy-line-height-normal);
  color: #fff;
  border-radius: var(--zy-radius-sm);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.cell__item.is-homework {
  background: var(--zy-warning-base);
}

.cell__item.is-exam {
  background: var(--zy-danger-base);
}

.cell__item.is-done {
  background: var(--zy-success-base);
}

.cell__item.is-closed {
  background: var(--zy-text-disabled);
}

.cell__more {
  font-size: var(--zy-font-size-xs);
  color: var(--zy-text-tertiary);
}

/* ===== 即将截止 ===== */
.side__title {
  margin: 0 0 var(--zy-space-3);
  font-size: var(--zy-font-size-base);
  font-weight: var(--zy-font-weight-semibold);
  color: var(--zy-text-primary);
}

.upcoming {
  display: flex;
  flex-direction: column;
  gap: var(--zy-space-2);
  max-height: 560px;
  overflow-y: auto;
}

.task {
  display: block;
  width: 100%;
  padding: var(--zy-space-3);
  font: inherit;
  text-align: left;
  background: var(--zy-bg-surface);
  border: 1px solid var(--zy-border-subtle);
  border-left: 3px solid var(--zy-warning-base);
  border-radius: var(--zy-radius-md);
  cursor: pointer;
  transition: box-shadow var(--zy-duration-fast), transform var(--zy-duration-fast);
}

.task:hover {
  box-shadow: var(--zy-shadow-sm);
  transform: translateY(-1px);
}

.task.is-exam {
  border-left-color: var(--zy-danger-base);
}

.task.is-done {
  border-left-color: var(--zy-success-base);
  opacity: 0.75;
}

.task.is-closed {
  border-left-color: var(--zy-text-disabled);
  opacity: 0.7;
}

.task__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--zy-space-2);
}

.task__countdown {
  font-size: var(--zy-font-size-xs);
  color: var(--zy-text-tertiary);
}

.task__countdown.is-urgent {
  font-weight: var(--zy-font-weight-bold);
  color: var(--zy-danger-base);
}

.task__title {
  margin: 0 0 var(--zy-space-1);
  font-size: var(--zy-font-size-sm);
  font-weight: var(--zy-font-weight-semibold);
  color: var(--zy-text-primary);
  word-break: break-all;
}

.task__meta {
  display: flex;
  justify-content: space-between;
  gap: var(--zy-space-2);
  font-size: var(--zy-font-size-xs);
  color: var(--zy-text-tertiary);
}

@media screen and (max-width: 992px) {
  .layout {
    flex-direction: column;
  }

  .layout__side {
    width: 100%;
  }
}
</style>
