<template>
  <PageLayout title="待完成作业" description="按课程查看班级作业，未完成的排在前面。">
    <template #actions>
      <el-select v-model="courseid" placeholder="全部课程" clearable class="course-select">
        <el-option
          v-for="c in courses"
          :key="c.courseid"
          :label="c.coursename"
          :value="c.courseid"
        />
      </el-select>
    </template>

    <div v-if="homework.length" class="stats">
      <el-tag type="success" size="small" disable-transitions>已提交 {{ stats.submitted }}</el-tag>
      <el-tag type="warning" size="small" disable-transitions>待完成 {{ stats.pending }}</el-tag>
      <el-tag v-if="stats.urgent" type="danger" size="small" effect="dark" disable-transitions>
        48 小时内截止 {{ stats.urgent }}
      </el-tag>
    </div>

    <div v-loading="loading" class="grid">
      <HomeworkCard
        v-for="item in pageRows"
        :key="item.homeworkid"
        :homework="item"
        mode="pending"
        @submit="goSubmit"
        @view="goDetail"
      />
      <el-empty v-if="!loading && !homework.length" description="暂无作业数据" />
    </div>

    <Pagination
      v-model:page="page"
      v-model:size="pageSize"
      :total="homework.length"
      @change="scrollToTop"
    />
  </PageLayout>
</template>

<script setup>
/** 课程作业（学生端） */
import { computed, onMounted, ref, watch } from 'vue'
import { useStore } from 'vuex'
import { useRouter } from 'vue-router'
import Pagination from '@/components/Pagination.vue'
import HomeworkCard from '@/components/HomeworkCard.vue'
import { PageLayout } from '@/components/ui'
import { listCoursesByClass } from '@/api/course'
import { listHomeworkByCourse, listStudentHomeworkByStudent } from '@/api/homework'
import { getCurrentSemester } from '@/utils/semester'
import { toDate } from '@/utils/format'

/** 一门课的作业量远小于此，一次取完便于在前端统一排序和统计 */
const BULK_PAGE = { page: 1, pageSize: 1000 }
const URGENT_MS = 48 * 3600 * 1000

const store = useStore()
const router = useRouter()

const courses = ref([])
const courseid = ref(null)
const homework = ref([])
const loading = ref(false)

const page = ref(1)
const pageSize = ref(10)

const pageRows = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return homework.value.slice(start, start + pageSize.value)
})

/**
 * 统计要基于全部作业而不是当前页，否则翻页时数字会跟着变。
 */
const stats = computed(() => {
  const now = Date.now()
  const submitted = homework.value.filter((h) => h.submitted).length
  const urgent = homework.value.filter((h) => {
    if (h.submitted) return false
    const deadline = toDate(h.deadline)?.getTime()
    return deadline && deadline > now && deadline - now < URGENT_MS
  }).length

  return { submitted, pending: homework.value.length - submitted, urgent }
})

const loadCourses = async () => {
  const classid = store.state.user?.classid
  if (!classid) return
  const res = await listCoursesByClass(classid, { semester: getCurrentSemester() }).catch(() => null)
  courses.value = res?.data || []
}

const loadHomework = async () => {
  const studentid = store.state.user?.studentid
  const scope = courseid.value ? courses.value.filter((c) => c.courseid === courseid.value) : courses.value

  if (!scope.length) {
    homework.value = []
    return
  }

  loading.value = true
  try {
    const [submissions, ...lists] = await Promise.all([
      studentid ? listStudentHomeworkByStudent(studentid, BULK_PAGE).catch(() => null) : null,
      ...scope.map((c) => listHomeworkByCourse(c.courseid, BULK_PAGE).catch(() => null))
    ])

    const statusByHomework = new Map(
      (submissions?.data || []).map((s) => [s.homeworkid, s.status])
    )

    homework.value = lists
      .flatMap((res) => res?.data || [])
      .map((h) => ({
        ...h,
        submitted: statusByHomework.has(h.homeworkid),
        status: statusByHomework.get(h.homeworkid)
      }))
      // 未完成的排前面，同组内按发布时间倒序
      .sort((a, b) => {
        if (a.submitted !== b.submitted) return a.submitted ? 1 : -1
        return (toDate(b.createtime)?.getTime() || 0) - (toDate(a.createtime)?.getTime() || 0)
      })
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await loadCourses()
  loadHomework()
})

watch(courseid, () => {
  page.value = 1
  loadHomework()
})

const scrollToTop = () => window.scrollTo({ top: 0, behavior: 'smooth' })

const goSubmit = (row) =>
  router.push({ path: '/student/homework/submit', query: { id: row.homeworkid } })

const goDetail = (row) =>
  router.push({ path: '/student/myhomework', query: { homeworkid: row.homeworkid } })
</script>

<style scoped>
.course-select {
  width: 240px;
}

.stats {
  display: flex;
  align-items: center;
  gap: var(--zy-space-2);
  padding: var(--zy-space-2) var(--zy-space-3);
  margin-bottom: var(--zy-space-4);
  background: var(--zy-bg-sunken);
  border-radius: var(--zy-radius-md);
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: var(--zy-space-3);
  min-height: 200px;
}

@media screen and (max-width: 768px) {
  .course-select {
    width: 100%;
  }

  .grid {
    grid-template-columns: 1fr;
  }
}
</style>
