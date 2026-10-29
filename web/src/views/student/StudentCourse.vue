<template>
  <PageLayout title="我的课程" :description="`本学年共 ${courses.length} 门课程`">
    <template #actions>
      <el-select v-model="academicYear" placeholder="选择学年" class="picker">
        <el-option v-for="item in ACADEMIC_YEARS" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-button type="primary" @click="joinVisible = true">输入课程码加入</el-button>
    </template>

    <div v-loading="loading" class="tiles">
      <el-empty v-if="!loading && !courses.length" description="本学年还没有课程" />

      <article v-for="course in courses" :key="course.courseid" class="tile">
        <h3 class="tile__title">{{ course.coursename }}</h3>
        <p class="tile__meta">
          <span class="tile__avatar">{{ teacherInitial(course.teachername) }}</span>
          {{ course.teachername }} · {{ className }}
        </p>
        <p class="tile__desc">{{ course.description || '暂无课程描述' }}</p>
        <div class="tile__action">
          <el-button @click="openDetail(course)">课程详情</el-button>
        </div>
      </article>
    </div>

    <!-- 课程详情 -->
    <el-dialog v-model="detailVisible" :title="detail.coursename" width="720px" destroy-on-close>
      <div v-loading="loadingStats">
        <div class="detail__meta">
          <el-tag size="small" disable-transitions>{{ detail.semester || '未知学期' }}</el-tag>
          <span>任课教师 {{ detail.teachername || '—' }}</span>
        </div>

        <div class="detail__stats">
          <StatTile label="作业总数" :value="stats.totalHomework" />
          <StatTile label="已完成" :value="stats.completedCount" tone="success" />
          <StatTile
            label="待完成"
            :value="stats.pendingCount"
            :tone="stats.pendingCount ? 'warning' : 'default'"
          />
        </div>

        <el-descriptions :column="2" border class="detail__info">
          <el-descriptions-item label="课程代码">{{ detail.coursecode || '—' }}</el-descriptions-item>
          <el-descriptions-item label="课时安排">{{ detail.hours }} 学时</el-descriptions-item>
          <el-descriptions-item v-if="detail.description" label="课程描述" :span="2">
            {{ detail.description }}
          </el-descriptions-item>
        </el-descriptions>

        <SectionCard
          title="下一项作业"
          :empty="!stats.nextHomework"
          empty-text="暂无待办作业，太棒了"
          empty-icon="ph:confetti-duotone"
          class="detail__homework"
        >
          <div v-if="stats.nextHomework" class="next">
            <div class="next__info">
              <p class="next__title">{{ stats.nextHomework.title }}</p>
              <p class="next__deadline">
                截止 {{ formatDate(stats.nextHomework.deadline, 'minute') }}
                <el-tag
                  :type="countdown(stats.nextHomework.deadline).expired ? 'danger' : 'success'"
                  size="small"
                  disable-transitions
                >
                  {{ countdown(stats.nextHomework.deadline).text }}
                </el-tag>
              </p>
            </div>
            <el-button type="primary" @click="goHomework(stats.nextHomework)">去完成</el-button>
          </div>
        </SectionCard>
      </div>

      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 加入课程 -->
    <FormDialog
      v-model="joinVisible"
      title="加入课程"
      :model="joinForm"
      :rules="joinRules"
      :loading="joining"
      width="420px"
      confirm-text="加入"
      @submit="join"
      @closed="joinForm.coursecode = ''"
    >
      <el-form-item label="课程码" prop="coursecode">
        <el-input v-model="joinForm.coursecode" maxlength="20" placeholder="请输入教师提供的课程码" />
      </el-form-item>
    </FormDialog>
  </PageLayout>
</template>

<script setup>
/** 我的课程（学生端） */
import { onMounted, reactive, ref, watch } from 'vue'
import { useStore } from 'vuex'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { FormDialog, PageLayout, SectionCard, StatTile } from '@/components/ui'
import { joinCourseByCode, listCoursesByClass } from '@/api/course'
import { listHomeworkByCourse, listStudentHomeworkByStudent } from '@/api/homework'
import { countdown, formatDate, toDate } from '@/utils/format'
import { getAcademicYearOptions } from '@/utils/semester'

const ACADEMIC_YEARS = getAcademicYearOptions(5)

const store = useStore()
const router = useRouter()
const className = store.state.user?.classname || '—'

const courses = ref([])
const loading = ref(false)
const academicYear = ref(ACADEMIC_YEARS[0]?.value)

const teacherInitial = (name) => String(name || '').trim()[0] || '师'

const load = async () => {
  const classid = store.state.user?.classid
  if (!classid) return

  loading.value = true
  try {
    const res = await listCoursesByClass(classid, { semester: academicYear.value }).catch(() => null)
    courses.value = res?.data || []
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(academicYear, load)

/* ---------- 课程详情 ---------- */

const detailVisible = ref(false)
const loadingStats = ref(false)
const detail = ref({})
const stats = reactive({ totalHomework: 0, completedCount: 0, pendingCount: 0, nextHomework: null })

const openDetail = (course) => {
  detail.value = course
  detailVisible.value = true
  loadStats(course.courseid)
}

/**
 * 挑出「下一项作业」：未提交的里优先取最近一个还没到期的；
 * 若全部逾期，则取逾期时间离现在最近的那一个。
 */
const pickNextHomework = (pending) => {
  if (!pending.length) return null

  const sorted = [...pending].sort(
    (a, b) => (toDate(a.deadline)?.getTime() || 0) - (toDate(b.deadline)?.getTime() || 0)
  )
  const now = Date.now()
  return sorted.find((h) => (toDate(h.deadline)?.getTime() || 0) > now) || sorted[sorted.length - 1]
}

const loadStats = async (courseId) => {
  const studentId = store.state.user?.studentid
  Object.assign(stats, { totalHomework: 0, completedCount: 0, pendingCount: 0, nextHomework: null })
  if (!studentId) return

  loadingStats.value = true
  try {
    const [homeworkRes, submissionRes] = await Promise.all([
      listHomeworkByCourse(courseId).catch(() => null),
      listStudentHomeworkByStudent(studentId, { courseid: courseId, pageSize: 100 }).catch(() => null)
    ])

    const homeworks = homeworkRes?.data || []
    const submitted = new Set(
      (submissionRes?.data || []).filter((s) => s.status > 0).map((s) => s.homeworkid)
    )

    const pending = homeworks.filter((h) => !submitted.has(h.homeworkid))

    stats.totalHomework = homeworks.length
    stats.completedCount = submitted.size
    stats.pendingCount = pending.length
    stats.nextHomework = pickNextHomework(pending)
  } finally {
    loadingStats.value = false
  }
}

const goHomework = (homework) => {
  detailVisible.value = false
  router.push({ path: '/student/homework/submit', query: { id: homework.homeworkid } })
}

/* ---------- 加入课程 ---------- */

const joinVisible = ref(false)
const joining = ref(false)
const joinForm = reactive({ coursecode: '' })

const joinRules = {
  coursecode: [
    { required: true, message: '请输入课程码', trigger: 'blur' },
    { min: 4, message: '课程码至少 4 位', trigger: 'blur' }
  ]
}

const join = async () => {
  const classid = store.state.user?.classid
  if (!classid) {
    ElMessage.warning('当前账号未归属班级，无法加入课程')
    return
  }

  joining.value = true
  try {
    const res = await joinCourseByCode(joinForm.coursecode.trim(), classid)
    ElMessage.success(res?.msg || '已加入课程')
    joinVisible.value = false
    await load()
  } catch {
    // 课程码不存在等业务错误由 http 拦截器提示
  } finally {
    joining.value = false
  }
}
</script>

<style scoped>
.picker {
  width: 200px;
}

.tiles {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: var(--zy-space-4);
  min-height: 160px;
}

.tiles :deep(.el-empty) {
  grid-column: 1 / -1;
}

.tile {
  display: flex;
  flex-direction: column;
  gap: var(--zy-space-2);
  padding: var(--zy-space-4);
  background: var(--zy-bg-surface);
  border: 1px solid var(--zy-border-subtle);
  border-radius: var(--zy-radius-surface);
  transition: border-color var(--zy-duration-fast) var(--zy-ease-standard);
}

.tile:hover {
  border-color: var(--zy-brand-border);
}

.tile__title {
  margin: 0;
  font-size: var(--zy-font-size-lg);
  font-weight: var(--zy-font-weight-semibold);
  color: var(--zy-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tile__meta {
  display: flex;
  align-items: center;
  gap: var(--zy-space-2);
  margin: 0;
  font-size: var(--zy-font-size-sm);
  color: var(--zy-text-secondary);
}

.tile__avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: var(--zy-radius-full);
  background: var(--zy-brand-soft);
  color: var(--zy-brand-base);
  font-size: var(--zy-font-size-xs);
  font-weight: var(--zy-font-weight-semibold);
}

.tile__desc {
  margin: 0;
  font-size: var(--zy-font-size-sm);
  color: var(--zy-text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tile__action {
  margin-top: auto;
  padding-top: var(--zy-space-2);
}

/* ===== 详情 ===== */
.detail__meta {
  display: flex;
  align-items: center;
  gap: var(--zy-space-3);
  margin-bottom: var(--zy-space-4);
  font-size: var(--zy-font-size-sm);
  color: var(--zy-text-secondary);
}

.detail__stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--zy-space-3);
  margin-bottom: var(--zy-space-4);
}

.detail__info {
  margin-bottom: var(--zy-space-4);
}

.next {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--zy-space-3);
  flex-wrap: wrap;
}

.next__title {
  margin: 0;
  font-weight: var(--zy-font-weight-medium);
  color: var(--zy-text-primary);
}

.next__deadline {
  display: flex;
  align-items: center;
  gap: var(--zy-space-2);
  margin: var(--zy-space-1) 0 0;
  font-size: var(--zy-font-size-sm);
  color: var(--zy-text-tertiary);
}

@media screen and (max-width: 768px) {
  .picker {
    width: 100%;
  }
}
</style>
