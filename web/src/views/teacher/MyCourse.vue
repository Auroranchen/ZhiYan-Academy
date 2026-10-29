<template>
  <PageLayout
    title="我的课程"
    description="集中查看课程、班级与作业安排，保持本学期教学节奏一目了然。"
  >
    <template #actions>
      <el-select v-model="semester" placeholder="全部学期" clearable class="filter">
        <el-option v-for="s in semesterOptions" :key="s" :label="s" :value="s" />
      </el-select>
      <el-select v-model="classid" placeholder="全部班级" clearable class="filter">
        <el-option v-for="c in classOptions" :key="c.classid" :label="c.classname" :value="c.classid" />
      </el-select>
      <el-button type="primary" @click="openCreate">创建课程</el-button>
    </template>

    <template #summary>
      <StatTile label="课程总数" :value="courses.length" tone="brand" icon="ph:books-duotone" />
      <StatTile label="当前学期" :value="semester || '全部学期'" icon="ph:calendar-duotone" />
      <StatTile label="筛选班级" :value="selectedClassName" icon="ph:users-three-duotone" />
    </template>

    <el-empty
      v-if="!courses.length"
      :description="allCourses.length ? '当前筛选条件下暂无课程，请切换学期或班级' : '还没有课程，点击右上角创建'"
    />

    <div v-else class="grid">
      <article v-for="(course, index) in courses" :key="course.courseid" class="card">
        <div class="card__accent" :class="`is-tone-${index % TONE_COUNT}`"></div>

        <header class="card__head">
          <span class="card__eyebrow">COURSE</span>
          <h3 class="card__title">{{ course.coursename }}</h3>
        </header>

        <p class="card__meta">
          <span class="card__avatar">{{ teacherInitial }}</span>
          {{ teacherName }} · {{ course.classNamesStr || '未分配班级' }} · {{ course.hours }} 课时
        </p>

        <div class="card__tags">
          <el-tag size="small" effect="plain" disable-transitions>
            {{ course.semester || '未设置学期' }}
          </el-tag>
          <el-tag size="small" effect="plain" disable-transitions>
            课程码 {{ course.coursecode || '—' }}
          </el-tag>
        </div>

        <p class="card__desc">{{ course.description || '暂无描述' }}</p>

        <el-button class="card__action" @click="openDetail(course)">查看详情</el-button>
      </article>
    </div>

    <!-- 课程详情 -->
    <el-dialog v-model="detail.visible" title="课程详情" width="900px" top="6vh">
      <header class="detail__head">
        <div>
          <span class="detail__kicker">COURSE OVERVIEW</span>
          <h3 class="detail__title">{{ detail.course.coursename }}</h3>
        </div>
        <span class="detail__code">课程码 {{ detail.course.coursecode || '—' }}</span>
      </header>

      <div class="detail__pills">
        <el-tag effect="plain" disable-transitions>学期：{{ detail.course.semester || '—' }}</el-tag>
        <el-tag effect="plain" disable-transitions>课时：{{ detail.course.hours || '—' }}</el-tag>
        <el-tag effect="plain" disable-transitions>
          班级：{{ detail.course.classNamesStr || '—' }}
        </el-tag>
      </div>

      <div class="detail__metrics">
        <StatTile label="作业数量" :value="detail.homework.length" tone="brand" />
        <StatTile label="开课班级数" :value="detailClasses.length" tone="warning" />
        <StatTile label="最近作业" :value="latestHomeworkTitle" />
        <StatTile label="当前学期" :value="detail.course.semester || '—'" />
      </div>

      <SectionCard title="课程作业列表" :count="detail.homework.length">
        <template #action>
          <el-select
            v-model="detail.gradingClassid"
            placeholder="批改时定位班级"
            clearable
            class="filter"
          >
            <el-option
              v-for="c in detailClasses"
              :key="c.classid"
              :label="c.classname"
              :value="c.classid"
            />
          </el-select>
        </template>

        <DataTable
          :columns="homeworkColumns"
          :data="detail.homework"
          :loading="detail.loading"
          :show-pagination="false"
          row-key="homeworkid"
          empty-text="该课程还没有布置作业"
          actions-width="180"
        >
          <template #classes>{{ detail.course.classNamesStr || '—' }}</template>
          <template #deadline="{ row }">{{ formatDate(row.deadline, 'minute') }}</template>
          <template #actions="{ row }">
            <el-button link type="primary" @click="goGrade(row)">去批改</el-button>
            <el-button link @click="router.push({ name: 'TeacherHomework' })">作业管理</el-button>
          </template>
        </DataTable>
      </SectionCard>

      <template #footer>
        <el-button @click="detail.visible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 创建课程 -->
    <FormDialog
      v-model="create.visible"
      title="创建课程"
      width="520px"
      :model="create.form"
      :rules="createRules"
      :loading="create.saving"
      @submit="submitCreate"
    >
      <el-form-item label="课程名称" prop="coursename">
        <el-input v-model="create.form.coursename" placeholder="请输入课程名称" />
      </el-form-item>
      <el-form-item label="学期" prop="semester">
        <el-select v-model="create.form.semester" placeholder="请选择学期" class="full">
          <el-option v-for="t in terms" :key="t.value" :label="t.label" :value="t.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="班级" prop="classIds">
        <el-select v-model="create.form.classIds" multiple placeholder="请选择班级" class="full">
          <el-option
            v-for="c in allClasses"
            :key="c.classid"
            :label="c.classname"
            :value="c.classid"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="课时" prop="hours">
        <el-input-number v-model="create.form.hours" :min="1" :max="200" />
      </el-form-item>
      <el-form-item label="课程描述" prop="description">
        <el-input v-model="create.form.description" type="textarea" :rows="3" placeholder="选填" />
      </el-form-item>
    </FormDialog>
  </PageLayout>
</template>

<script setup>
/** 我的课程（教师端） */
import { computed, onMounted, reactive, ref } from 'vue'
import { useStore } from 'vuex'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { DataTable, FormDialog, PageLayout, SectionCard, StatTile } from '@/components/ui'
import { createCourse, listCoursesByTeacher } from '@/api/course'
import { listHomeworkByCourse } from '@/api/homework'
import { listAllClasses } from '@/api/classes'
import { listTermOptions } from '@/api/academicTerm'
import { getCurrentSemester } from '@/utils/semester'
import { formatDate } from '@/utils/format'

/** 课程卡片轮换的强调色数量，与样式里的 is-tone-* 保持一致 */
const TONE_COUNT = 4
const DEFAULT_HOURS = 32

const homeworkColumns = [
  { prop: 'homeworkid', label: 'ID', width: 80 },
  { prop: 'title', label: '标题', minWidth: 200 },
  { label: '班级', minWidth: 160, slot: 'classes' },
  { label: '截止时间', width: 170, slot: 'deadline' }
]

const createRules = {
  coursename: [{ required: true, message: '请输入课程名称', trigger: 'blur' }],
  semester: [{ required: true, message: '请选择学期', trigger: 'change' }],
  classIds: [{ required: true, message: '请选择班级', trigger: 'change' }],
  hours: [{ required: true, message: '请输入课时', trigger: 'blur' }]
}

const store = useStore()
const router = useRouter()
const teacherid = store.state.user?.teacherid

const allCourses = ref([])
const allClasses = ref([])
const terms = ref([])

const semester = ref(getCurrentSemester())
const classid = ref(null)

const teacherName = computed(() => store.state.user?.teachername || '教师')
const teacherInitial = computed(() => teacherName.value.trim().charAt(0) || '师')

/** 课程与班级的关联只有逗号分隔的班级名，只能按名称比对 */
const classNamesOf = (course) =>
  String(course.classNamesStr || '')
    .split(/[，,]/)
    .map((s) => s.trim())
    .filter(Boolean)

const semesterOptions = computed(() =>
  [...new Set(allCourses.value.map((c) => c.semester).filter(Boolean))].sort()
)

/** 班级表加载失败时，退而用课程里出现过的班级名兜底 */
const classOptions = computed(() => {
  if (allClasses.value.length) {
    return allClasses.value.map((c) => ({ classid: c.classid, classname: c.classname }))
  }
  const names = new Set(allCourses.value.flatMap(classNamesOf))
  return [...names].map((name) => ({ classid: name, classname: name }))
})

const selectedClassName = computed(
  () => classOptions.value.find((c) => c.classid === classid.value)?.classname || '全部班级'
)

const courses = computed(() => {
  const className = classid.value ? selectedClassName.value : null

  return allCourses.value.filter((course) => {
    if (semester.value && course.semester !== semester.value) return false
    if (className && !classNamesOf(course).includes(className)) return false
    return true
  })
})

const loadCourses = async () => {
  if (!teacherid) return
  const res = await listCoursesByTeacher(teacherid).catch(() => null)
  allCourses.value = res?.data || []
}

onMounted(async () => {
  const [, classRes, termRes] = await Promise.all([
    loadCourses(),
    listAllClasses().catch(() => null),
    listTermOptions().catch(() => null)
  ])

  allClasses.value = classRes?.data || []
  terms.value = (termRes?.data || []).map((item) => ({
    label: item.current ? `${item.termName}（当前）` : item.termName,
    value: item.termCode,
    current: Boolean(item.current)
  }))

  // 教务配置的当前学期优先于按日期推算的结果
  const current = terms.value.find((t) => t.current)
  if (current) semester.value = current.value
})

/* ---------- 课程详情 ---------- */

const detail = reactive({
  visible: false,
  loading: false,
  course: {},
  homework: [],
  gradingClassid: null
})

const detailClasses = computed(() => {
  const names = new Set(classNamesOf(detail.course))
  return allClasses.value
    .filter((c) => names.has(c.classname))
    .map((c) => ({ classid: c.classid, classname: c.classname }))
})

/** 后端按创建时间倒序返回，第一条即最新 */
const latestHomeworkTitle = computed(() => detail.homework[0]?.title || '—')

const openDetail = async (course) => {
  detail.course = course
  detail.homework = []
  detail.gradingClassid = null
  detail.visible = true
  detail.loading = true

  try {
    const res = await listHomeworkByCourse(course.courseid).catch(() => null)
    detail.homework = res?.data || []
  } finally {
    detail.loading = false
  }
}

const goGrade = (row) =>
  router.push({
    name: 'TeacherStudentHomework',
    query: {
      courseid: row.courseid || detail.course.courseid,
      homeworkid: row.homeworkid,
      ...(detail.gradingClassid ? { classid: detail.gradingClassid } : {})
    }
  })

/* ---------- 创建课程 ---------- */

const create = reactive({
  visible: false,
  saving: false,
  form: { coursename: '', hours: DEFAULT_HOURS, description: '', semester: '', classIds: [] }
})

const openCreate = () => {
  const current = terms.value.find((t) => t.current)
  Object.assign(create.form, {
    coursename: '',
    hours: DEFAULT_HOURS,
    description: '',
    semester: current?.value || semester.value || getCurrentSemester(),
    classIds: []
  })
  create.visible = true
}

const submitCreate = async () => {
  create.saving = true
  try {
    const res = await createCourse({ ...create.form, teacherid })
    create.visible = false
    await loadCourses()

    const code = res?.data?.coursecode
    if (code) {
      // 课程码是学生加入课程的唯一入口，用弹窗强提示避免被忽略
      ElMessageBox.alert(`课程码：${code}\n\n请将课程码告知学生，学生输入课程码即可加入课程。`, '创建成功', {
        confirmButtonText: '我知道了'
      })
    } else {
      ElMessage.success('课程创建成功')
    }
  } catch {
    // 失败提示由 http 拦截器统一处理
  } finally {
    create.saving = false
  }
}
</script>

<style scoped>
.filter {
  width: 180px;
}

.full {
  width: 100%;
}

/* ===== 课程卡片 ===== */
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: var(--zy-space-4);
}

.card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: var(--zy-space-3);
  padding: var(--zy-space-5);
  overflow: hidden;
  background: var(--zy-bg-surface);
  border: 1px solid var(--zy-border-subtle);
  border-radius: var(--zy-radius-surface);
  transition: border-color var(--zy-duration-fast), box-shadow var(--zy-duration-fast);
}

.card:hover {
  border-color: var(--zy-brand-border);
  box-shadow: var(--zy-shadow-sm);
}

/* 顶部色条区分课程，比整块渐变底色更克制也更易读 */
.card__accent {
  position: absolute;
  inset: 0 0 auto;
  height: 4px;
}

.card__accent.is-tone-0 {
  background: var(--zy-chart-1);
}

.card__accent.is-tone-1 {
  background: var(--zy-chart-2);
}

.card__accent.is-tone-2 {
  background: var(--zy-chart-4);
}

.card__accent.is-tone-3 {
  background: var(--zy-chart-3);
}

.card__eyebrow {
  font-size: var(--zy-font-size-xs);
  font-weight: var(--zy-font-weight-medium);
  letter-spacing: var(--zy-letter-spacing-wide);
  color: var(--zy-text-tertiary);
}

.card__title {
  margin: var(--zy-space-1) 0 0;
  font-size: var(--zy-font-size-lg);
  font-weight: var(--zy-font-weight-semibold);
  color: var(--zy-text-primary);
}

.card__meta {
  display: flex;
  align-items: center;
  gap: var(--zy-space-2);
  margin: 0;
  font-size: var(--zy-font-size-sm);
  color: var(--zy-text-secondary);
}

.card__avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  font-size: var(--zy-font-size-sm);
  font-weight: var(--zy-font-weight-semibold);
  color: var(--zy-brand-base);
  background: var(--zy-brand-soft);
  border-radius: var(--zy-radius-full);
}

.card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--zy-space-1);
}

.card__desc {
  flex: 1;
  margin: 0;
  font-size: var(--zy-font-size-sm);
  line-height: var(--zy-line-height-normal);
  color: var(--zy-text-tertiary);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card__action {
  align-self: flex-start;
}

/* ===== 详情弹窗 ===== */
.detail__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--zy-space-4);
}

.detail__kicker {
  font-size: var(--zy-font-size-xs);
  letter-spacing: var(--zy-letter-spacing-wide);
  color: var(--zy-text-tertiary);
}

.detail__title {
  margin: var(--zy-space-1) 0 0;
  font-size: var(--zy-font-size-xl);
  font-weight: var(--zy-font-weight-semibold);
  color: var(--zy-text-primary);
}

.detail__code {
  font-size: var(--zy-font-size-sm);
  color: var(--zy-text-secondary);
}

.detail__pills {
  display: flex;
  flex-wrap: wrap;
  gap: var(--zy-space-2);
  margin: var(--zy-space-3) 0;
}

.detail__metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--zy-space-3);
  margin-bottom: var(--zy-space-4);
}

@media screen and (max-width: 768px) {
  .filter {
    width: 100%;
  }

  .detail__metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
