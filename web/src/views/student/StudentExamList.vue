<template>
  <PageLayout title="在线考试" :description="`共 ${exams.length} 场考试，可参加 ${counts.available + counts.ongoing} 场`">
    <template #actions>
      <el-input v-model="keyword" placeholder="搜索考试或课程" clearable class="search">
        <template #prefix><Icon icon="ph:magnifying-glass" /></template>
      </el-input>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </template>

    <el-radio-group v-model="statusFilter">
      <el-radio-button v-for="s in FILTERS" :key="s.value" :value="s.value">
        {{ s.label }} ({{ counts[s.value] }})
      </el-radio-button>
    </el-radio-group>

    <div v-loading="loading" class="list">
      <el-empty v-if="!loading && !visibleExams.length" description="没有符合条件的考试" />

      <article v-for="exam in visibleExams" :key="exam.examid" class="card">
        <header class="card__head">
          <h3 class="card__title">{{ exam.title }}</h3>
          <el-tag :type="state(exam).tone" size="small" disable-transitions>{{ state(exam).label }}</el-tag>
        </header>

        <p class="card__course">{{ exam.coursename }}</p>

        <div class="card__meta">
          <span><Icon icon="ph:clock-duotone" /> {{ exam.duration }} 分钟</span>
          <span><Icon icon="ph:medal-duotone" /> {{ exam.totalScore }} 分</span>
        </div>

        <p v-if="exam.starttime || exam.endtime" class="card__window">
          <Icon icon="ph:calendar-duotone" />
          {{ formatDate(exam.starttime, 'minute') }} 至 {{ formatDate(exam.endtime, 'minute') }}
        </p>

        <p v-if="exam.description" class="card__desc">{{ exam.description }}</p>

        <footer class="card__footer">
          <el-tag v-if="exam.antiCheat === 1" type="danger" size="small" effect="plain" disable-transitions>
            防作弊监考
          </el-tag>
          <span v-else></span>
          <el-button
            :type="state(exam).buttonType"
            :disabled="!state(exam).enterable"
            size="small"
            @click="enter(exam)"
          >
            {{ state(exam).buttonText }}
          </el-button>
        </footer>
      </article>
    </div>
  </PageLayout>
</template>

<script setup>
/** 在线考试列表（学生端） */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Icon } from '@iconify/vue'
import { PageLayout } from '@/components/ui'
import { listStudentExams } from '@/api/exam'
import { formatDate, toDate } from '@/utils/format'

const FILTERS = [
  { value: 'all', label: '全部' },
  { value: 'available', label: '可参加' },
  { value: 'ongoing', label: '进行中' },
  { value: 'done', label: '已交卷' },
  { value: 'closed', label: '已截止' }
]

/**
 * 每种状态对应的展示与可进入性。
 * 原实现把状态判定写了两遍（一份用于筛选计数、一份用于按钮文案），
 * 这里只判一次，其余信息都从状态派生。
 */
const STATES = {
  done: { label: '已交卷', tone: 'info', buttonText: '查看成绩', buttonType: 'info', enterable: true },
  notStarted: { label: '未开始', tone: 'warning', buttonText: '未开始', buttonType: 'primary', enterable: false },
  closed: { label: '已截止', tone: 'danger', buttonText: '已截止', buttonType: 'info', enterable: false },
  ongoing: { label: '进行中', tone: 'success', buttonText: '继续作答', buttonType: 'success', enterable: true },
  available: { label: '可参加', tone: 'success', buttonText: '开始考试', buttonType: 'primary', enterable: true }
}

/** 筛选用的归类：未开始与可参加都算「可参加」 */
const FILTER_OF_STATE = {
  done: 'done',
  notStarted: 'available',
  closed: 'closed',
  ongoing: 'ongoing',
  available: 'available'
}

const router = useRouter()

const exams = ref([])
const loading = ref(false)
const keyword = ref('')
const statusFilter = ref('all')

const stateKey = (exam) => {
  const now = Date.now()
  const start = toDate(exam.starttime)?.getTime()
  const end = toDate(exam.endtime)?.getTime()

  if (exam.recordStatus !== null && exam.recordStatus !== undefined && exam.recordStatus >= 1) return 'done'
  if (start && now < start) return 'notStarted'
  if (end && now > end) return 'closed'
  if (exam.recordStatus === 0) return 'ongoing'
  return 'available'
}

const state = (exam) => STATES[stateKey(exam)]

const counts = computed(() => {
  const result = { all: exams.value.length, available: 0, ongoing: 0, done: 0, closed: 0 }
  exams.value.forEach((exam) => { result[FILTER_OF_STATE[stateKey(exam)]] += 1 })
  return result
})

const visibleExams = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return exams.value.filter((exam) => {
    const matchKeyword =
      !kw ||
      (exam.title || '').toLowerCase().includes(kw) ||
      (exam.coursename || '').toLowerCase().includes(kw)
    const matchStatus =
      statusFilter.value === 'all' || FILTER_OF_STATE[stateKey(exam)] === statusFilter.value
    return matchKeyword && matchStatus
  })
})

const load = async () => {
  loading.value = true
  try {
    const res = await listStudentExams().catch(() => null)
    exams.value = res?.data || []
  } finally {
    loading.value = false
  }
}

onMounted(load)

const enter = (exam) => router.push({ path: '/student/exam/take', query: { examid: exam.examid } })
</script>

<style scoped>
.search {
  width: 220px;
}

.list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: var(--zy-space-4);
  min-height: 160px;
}

.list :deep(.el-empty) {
  grid-column: 1 / -1;
}

.card {
  display: flex;
  flex-direction: column;
  padding: var(--zy-space-4);
  background: var(--zy-bg-surface);
  border: 1px solid var(--zy-border-subtle);
  border-radius: var(--zy-radius-surface);
  transition: border-color var(--zy-duration-fast) var(--zy-ease-standard);
}

.card:hover {
  border-color: var(--zy-brand-border);
}

.card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--zy-space-2);
}

.card__title {
  margin: 0;
  font-size: var(--zy-font-size-md);
  font-weight: var(--zy-font-weight-semibold);
  color: var(--zy-text-primary);
}

.card__course {
  margin: var(--zy-space-1) 0 0;
  font-size: var(--zy-font-size-sm);
  color: var(--zy-text-tertiary);
}

.card__meta {
  display: flex;
  gap: var(--zy-space-4);
  margin-top: var(--zy-space-3);
  font-size: var(--zy-font-size-sm);
  color: var(--zy-text-secondary);
}

.card__meta span {
  display: inline-flex;
  align-items: center;
  gap: var(--zy-space-1);
}

.card__window {
  display: flex;
  align-items: center;
  gap: var(--zy-space-1);
  margin: var(--zy-space-2) 0 0;
  font-size: var(--zy-font-size-xs);
  color: var(--zy-text-tertiary);
}

.card__desc {
  margin: var(--zy-space-2) 0 0;
  font-size: var(--zy-font-size-xs);
  line-height: var(--zy-line-height-normal);
  color: var(--zy-text-tertiary);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--zy-space-2);
  margin-top: auto;
  padding-top: var(--zy-space-4);
}
</style>
