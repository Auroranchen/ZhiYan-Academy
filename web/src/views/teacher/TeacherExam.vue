<template>
  <PageLayout
    title="在线考试"
    description="组卷、发布与批改。客观题自动判分，主观题与程序题支持 AI 辅助评阅。"
    :breadcrumb="[{ label: '教学管理' }, { label: '在线考试' }]"
  >
    <template #actions>
      <el-button type="primary" :icon="Plus" @click="handleAdd">新建考试</el-button>
    </template>

    <SectionCard title="考试列表" :count="total">
      <FilterBar @search="handleSearch" @reset="handleReset">
        <el-input
          v-model="queryParams.keyword"
          placeholder="搜索考试 / 课程"
          clearable
          class="filter-keyword"
          @keyup.enter="handleSearch"
        />
      </FilterBar>

      <DataTable
        v-model:page="queryParams.page"
        v-model:page-size="queryParams.pageSize"
        :columns="columns"
        :data="tableData"
        :loading="loading"
        :total="total"
        row-key="examid"
        actions-width="200"
        empty-text="还没有考试"
        empty-hint="点击右上角「新建考试」开始组卷"
        @change="loadData"
      >
        <template #exam="{ row }">
          <div class="exam-cell">
            <span class="exam-cell__name">{{ row.title }}</span>
            <span class="exam-cell__meta">
              {{ row.coursename }} · {{ row.duration }} 分钟 · {{ row.totalScore }} 分
            </span>
          </div>
        </template>

        <template #status="{ row }">
          <el-tag :type="STATUS_TONE[row.status] || 'info'" size="small" disable-transitions>
            {{ STATUS_LABEL[row.status] || '未知' }}
          </el-tag>
        </template>

        <template #submitted="{ row }">
          <span class="submit-count">{{ row.submittedCount || 0 }}</span>
        </template>

        <template #actions="{ row }">
          <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
          <el-button link type="success" @click="openResults(row)">成绩/批改</el-button>
          <el-dropdown trigger="click" @command="(cmd) => handleCommand(cmd, row)">
            <el-button link>更多</el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="clone">复制考试</el-dropdown-item>
                <el-dropdown-item v-if="row.status !== 1" command="publish">发布考试</el-dropdown-item>
                <el-dropdown-item v-else command="end">结束考试</el-dropdown-item>
                <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
      </DataTable>
    </SectionCard>

    <!-- 新建 / 编辑考试 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="860px" top="5vh" @closed="resetForm">
      <el-form :model="form" label-width="92px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="考试标题" required>
              <el-input v-model="form.title" placeholder="请输入考试标题" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="所属课程" required>
              <el-select v-model="form.courseid" placeholder="请选择课程" class="full-width">
                <el-option
                  v-for="c in currentSemesterCourses"
                  :key="c.courseid"
                  :label="c.coursename"
                  :value="c.courseid"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="时长(分钟)">
              <el-input-number v-model="form.duration" :min="1" :max="300" class="full-width" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="开考时间">
              <el-date-picker
                v-model="form.starttime"
                type="datetime"
                placeholder="不限则留空"
                class="full-width"
                value-format="YYYY-MM-DD HH:mm:ss"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="截止时间">
              <el-date-picker
                v-model="form.endtime"
                type="datetime"
                placeholder="不限则留空"
                class="full-width"
                value-format="YYYY-MM-DD HH:mm:ss"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="考试说明">
              <el-input v-model="form.description" placeholder="选填" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="防作弊">
              <el-switch v-model="form.antiCheat" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="发布状态">
              <el-select v-model="form.status" class="full-width">
                <el-option label="草稿" :value="0" />
                <el-option label="发布" :value="1" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">试题（共 {{ totalScore }} 分）</el-divider>

        <ExamQuestionEditor ref="editorRef" v-model="form.questions" />

        <div class="editor-actions">
          <el-button :icon="Plus" plain @click="editorRef?.add()">添加题目</el-button>
          <el-button :icon="Upload" @click="importDialogVisible = true">导入试题</el-button>
        </div>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 成绩单与未提交名单 -->
    <el-dialog v-model="resultVisible" title="考试成绩与批改" width="920px" top="5vh">
      <el-tabs v-model="resultTab">
        <el-tab-pane label="已提交成绩" name="submitted">
          <div class="result-summary">
            <StatTile label="已交卷" :value="stats.submitted || 0" />
            <StatTile label="平均分" :value="stats.avgScore || 0" />
            <StatTile label="最高分" :value="stats.maxScore || 0" />
            <StatTile
              label="切屏可疑"
              :value="stats.cheatSuspect || 0"
              :tone="stats.cheatSuspect ? 'danger' : 'success'"
            />
          </div>

          <DataTable
            :columns="recordColumns"
            :data="records"
            :show-pagination="false"
            max-height="380"
            row-key="id"
            actions-width="80"
            empty-text="还没有学生交卷"
          >
            <template #score="{ row }">
              <span :class="{ 'score-low': isFailing(row.score) }">{{ row.score }}</span>
            </template>
            <template #used="{ row }">{{ formatDuration(row.usedSeconds) }}</template>
            <template #switch="{ row }">
              <el-tag v-if="row.switchCount > 0" type="danger" size="small" disable-transitions>
                {{ row.switchCount }}
              </el-tag>
              <span v-else>0</span>
            </template>
            <template #way="{ row }">
              <el-tag
                :type="row.autoSubmit === 1 ? 'warning' : 'success'"
                size="small"
                disable-transitions
              >
                {{ row.autoSubmit === 1 ? '自动' : '手动' }}
              </el-tag>
            </template>
            <template #time="{ row }">{{ formatDate(row.submittime, 'minute') }}</template>
            <template #actions="{ row }">
              <el-button link type="primary" @click="openReview(row)">评阅</el-button>
            </template>
          </DataTable>

          <div class="result-export">
            <el-button plain size="small" :icon="Download" :disabled="!records.length" @click="exportResults">
              导出成绩(CSV)
            </el-button>
          </div>
        </el-tab-pane>

        <el-tab-pane name="unsubmitted">
          <template #label>
            未提交
            <el-tag v-if="unsubmittedList.length" type="danger" size="small" round class="tab-badge">
              {{ unsubmittedList.length }}
            </el-tag>
          </template>

          <el-alert
            v-if="!unsubmittedList.length"
            title="所有学生均已提交"
            type="success"
            :closable="false"
            show-icon
          />
          <DataTable
            v-else
            :columns="unsubmittedColumns"
            :data="unsubmittedList"
            :show-pagination="false"
            show-index
            max-height="420"
          />
        </el-tab-pane>
      </el-tabs>

      <template #footer>
        <el-button @click="resultVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <ExamReviewDialog
      v-model="reviewVisible"
      :record="reviewRecord"
      :exam-total-score="resultExam.totalScore"
      @reviewed="onReviewed"
    />

    <ExamImportDialog
      v-model="importDialogVisible"
      :course-name="editingCourseName"
      @imported="onQuestionsImported"
    />
  </PageLayout>
</template>

<script setup>
/** 在线考试管理（教师端） */
import { computed, onMounted, reactive, ref } from 'vue'
import { useStore } from 'vuex'
import { ElMessage } from 'element-plus'
import { Download, Plus, Upload } from '@element-plus/icons-vue'
import { DataTable, FilterBar, PageLayout, SectionCard, StatTile } from '@/components/ui'
import {
  changeExamStatus,
  createExam,
  deleteExam,
  getExam,
  getExamResults,
  listExamUnsubmitted,
  listTeacherExams,
  updateExam
} from '@/api/exam'
import { listCoursesByTeacher } from '@/api/course'
import { confirmDelete } from '@/utils/confirm'
import { downloadCsv } from '@/utils/excel'
import { formatDate } from '@/utils/format'
import { getCurrentSemester } from '@/utils/semester'
import {
  sumExamScore,
  toSavedExamQuestions,
  validateExamQuestions
} from '@/utils/examQuestion'
import ExamImportDialog from './components/ExamImportDialog.vue'
import ExamQuestionEditor from './components/ExamQuestionEditor.vue'
import ExamReviewDialog from './components/ExamReviewDialog.vue'

const STATUS_LABEL = { 0: '草稿', 1: '已发布', 2: '已结束' }
const STATUS_TONE = { 0: 'info', 1: 'success', 2: 'warning' }

/** 低于总分六成标红，方便教师一眼看到需要关注的学生 */
const PASS_RATIO = 0.6

const columns = [
  { label: '考试标题', slot: 'exam', minWidth: 220 },
  { label: '状态', slot: 'status', width: 90, align: 'center' },
  { label: '交卷', slot: 'submitted', width: 70, align: 'center' }
]

const recordColumns = [
  { prop: 'studentno', label: '学号', width: 110 },
  { prop: 'studentname', label: '姓名', width: 90 },
  { label: '得分', slot: 'score', width: 70, align: 'center' },
  { label: '用时', slot: 'used', width: 80, align: 'center' },
  { label: '切屏', slot: 'switch', width: 65, align: 'center' },
  { label: '方式', slot: 'way', width: 75, align: 'center' },
  { label: '交卷时间', slot: 'time', minWidth: 140 }
]

const unsubmittedColumns = [
  { prop: 'studentno', label: '学号', width: 140 },
  { prop: 'studentname', label: '姓名', width: 120 },
  { prop: 'classname', label: '班级', minWidth: 150 }
]

const emptyForm = () => ({
  examid: null,
  title: '',
  courseid: null,
  description: '',
  duration: 60,
  starttime: null,
  endtime: null,
  antiCheat: 1,
  status: 1,
  questions: []
})

const store = useStore()

const tableData = ref([])
const total = ref(0)
const loading = ref(false)
const courseList = ref([])
const queryParams = reactive({ keyword: '', page: 1, pageSize: 10 })

const dialogVisible = ref(false)
const dialogTitle = ref('新建考试')
const submitting = ref(false)
const form = reactive(emptyForm())
const editorRef = ref(null)

const resultVisible = ref(false)
const resultTab = ref('submitted')
const records = ref([])
const unsubmittedList = ref([])
const stats = reactive({})
const resultExam = reactive({})

const reviewVisible = ref(false)
const reviewRecord = ref({})
const importDialogVisible = ref(false)

// 新建考试时只能选当前学期的课程，避免误挂到往期课程上
const CURRENT_SEMESTER = getCurrentSemester()
const currentSemesterCourses = computed(() =>
  courseList.value.filter((c) => c.semester === CURRENT_SEMESTER)
)

const totalScore = computed(() => sumExamScore(form.questions))

/** AI 出题在未填主题时用所选课程名兜底 */
const editingCourseName = computed(
  () => currentSemesterCourses.value.find((c) => c.courseid === form.courseid)?.coursename || ''
)

const isFailing = (score) => Number(score) < (resultExam.totalScore || 100) * PASS_RATIO

const formatDuration = (seconds) => {
  if (!seconds) return '—'
  return `${Math.floor(seconds / 60)}分${seconds % 60}秒`
}

const loadCourses = async () => {
  try {
    const res = await listCoursesByTeacher(store.state.user?.teacherid)
    courseList.value = res.data || []
  } catch (e) {
    console.error('加载课程失败:', e)
  }
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await listTeacherExams(queryParams)
    tableData.value = res.data || []
    total.value = res.total || 0
  } catch (e) {
    console.error('加载考试失败:', e)
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  queryParams.page = 1
  loadData()
}

const handleReset = () => {
  queryParams.keyword = ''
  queryParams.page = 1
  loadData()
}

const resetForm = () => Object.assign(form, emptyForm())

/** 后端把题目存在 content 的 JSON 里 */
const readQuestions = (exam) => {
  try {
    return JSON.parse(exam.content || '{}').questions || []
  } catch (e) {
    return []
  }
}

const handleAdd = () => {
  resetForm()
  dialogTitle.value = '新建考试'
  dialogVisible.value = true
}

const handleEdit = async (row) => {
  try {
    const { data } = await getExam(row.examid)
    Object.assign(form, {
      examid: data.examid,
      title: data.title,
      courseid: data.courseid,
      description: data.description,
      duration: data.duration,
      starttime: data.starttime,
      endtime: data.endtime,
      antiCheat: data.antiCheat,
      // 已结束的考试重新编辑后回到已发布，否则学生仍然进不去
      status: data.status === 2 ? 1 : data.status,
      questions: readQuestions(data)
    })
    dialogTitle.value = '编辑考试'
    dialogVisible.value = true
  } catch (e) {
    console.error('加载考试详情失败:', e)
  }
}

const handleClone = async (row) => {
  try {
    const { data } = await getExam(row.examid)
    Object.assign(form, emptyForm(), {
      title: `${data.title || ''} (副本)`,
      courseid: data.courseid,
      description: data.description,
      duration: data.duration,
      antiCheat: data.antiCheat,
      status: 0,
      questions: readQuestions(data)
    })
    dialogTitle.value = '复制考试（另存为新考试）'
    dialogVisible.value = true
    ElMessage.info('已载入副本，可修改后保存为新考试')
  } catch (e) {
    console.error('复制考试失败:', e)
  }
}

const handleSubmit = async () => {
  if (!form.title.trim()) return ElMessage.warning('请填写考试标题')
  if (!form.courseid) return ElMessage.warning('请选择课程')

  const invalid = validateExamQuestions(form.questions)
  if (invalid) return ElMessage.warning(invalid)

  submitting.value = true
  try {
    const payload = {
      examid: form.examid,
      title: form.title,
      courseid: form.courseid,
      description: form.description,
      duration: form.duration,
      starttime: form.starttime,
      endtime: form.endtime,
      antiCheat: form.antiCheat,
      status: form.status,
      totalScore: totalScore.value,
      content: JSON.stringify({ questions: toSavedExamQuestions(form.questions) })
    }

    if (form.examid) {
      await updateExam(payload)
      ElMessage.success('更新成功')
    } else {
      await createExam(payload)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadData()
  } catch (e) {
    console.error('保存考试失败:', e)
  } finally {
    submitting.value = false
  }
}

const changeStatus = async (row, status) => {
  try {
    await changeExamStatus(row.examid, status)
    ElMessage.success('状态已更新')
    loadData()
  } catch (e) {
    console.error('更新状态失败:', e)
  }
}

const handleDelete = async (row) => {
  if (!(await confirmDelete(`确定删除考试「${row.title}」吗？该考试的成绩与作答记录将一并移除。`))) return
  try {
    await deleteExam(row.examid)
    ElMessage.success('删除成功')
    loadData()
  } catch (e) {
    console.error('删除考试失败:', e)
  }
}

const handleCommand = (command, row) => {
  const actions = {
    clone: () => handleClone(row),
    publish: () => changeStatus(row, 1),
    end: () => changeStatus(row, 2),
    delete: () => handleDelete(row)
  }
  actions[command]?.()
}

const openResults = async (row) => {
  Object.assign(resultExam, row)
  resultTab.value = 'submitted'
  try {
    const [resultRes, unsubmittedRes] = await Promise.all([
      getExamResults(row.examid),
      listExamUnsubmitted(row.examid)
    ])
    records.value = resultRes.data?.records || []
    Object.assign(stats, resultRes.data?.stats || {})
    unsubmittedList.value = unsubmittedRes.data || []
    resultVisible.value = true
  } catch (e) {
    console.error('加载成绩失败:', e)
  }
}

const exportResults = () => {
  downloadCsv({
    headers: ['学号', '姓名', '得分', '用时(秒)', '切屏次数', '提交方式', '交卷时间'],
    rows: records.value.map((r) => [
      r.studentno,
      r.studentname,
      r.score,
      r.usedSeconds || 0,
      r.switchCount || 0,
      r.autoSubmit === 1 ? '超时自动' : '手动',
      formatDate(r.submittime, 'datetime')
    ]),
    fileName: `${resultExam.title || '考试'}_成绩单.csv`
  })
}

const openReview = (record) => {
  reviewRecord.value = record
  reviewVisible.value = true
}

/** 评阅弹窗提交后会带回最新成绩，避免这里再查一次 */
const onReviewed = (data) => {
  if (!data) return
  records.value = data.records || []
  Object.assign(stats, data.stats || {})
}

const onQuestionsImported = (questions) => {
  form.questions.push(...questions)
}

onMounted(() => {
  loadCourses()
  loadData()
})
</script>

<style scoped>
.filter-keyword {
  width: 220px;
}

.full-width {
  width: 100%;
}

.exam-cell {
  display: flex;
  flex-direction: column;
  gap: var(--zy-space-1);
}

.exam-cell__name {
  font-weight: var(--zy-font-weight-semibold);
  color: var(--zy-text-primary);
}

.exam-cell__meta {
  font-size: var(--zy-font-size-xs);
  color: var(--zy-text-tertiary);
}

.submit-count {
  font-weight: var(--zy-font-weight-semibold);
  color: var(--zy-brand-base);
  font-variant-numeric: tabular-nums;
}

.editor-actions {
  display: flex;
  gap: var(--zy-space-2);
  align-items: center;
  margin-top: var(--zy-space-2);
}

.result-summary {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: var(--zy-space-3);
  margin-bottom: var(--zy-space-4);
}

.result-export {
  margin-top: var(--zy-space-3);
  text-align: right;
}

.score-low {
  color: var(--zy-danger-base);
  font-weight: var(--zy-font-weight-semibold);
}

.tab-badge {
  margin-left: var(--zy-space-1);
}
</style>
