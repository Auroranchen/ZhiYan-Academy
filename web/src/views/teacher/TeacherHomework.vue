<template>
  <PageLayout
    title="作业管理"
    description="发布在线作业或文档作业，跟踪阅读与提交情况，并可一键生成讲评 PPT。"
    :breadcrumb="[{ label: '教学管理' }, { label: '作业管理' }]"
  >
    <template #actions>
      <el-button type="primary" @click="handleAdd">发布作业</el-button>
      <el-button @click="openExportForAll">导出讲解PPT</el-button>
    </template>

    <template #summary>
      <StatTile label="已发布作业" :value="total" icon="ph:file-text-duotone" />
      <StatTile label="进行中" :value="ongoingCount" icon="ph:pencil-line-duotone" tone="brand" />
    </template>

    <SectionCard title="作业列表" :count="total">
      <FilterBar @search="handleSearch" @reset="handleReset">
        <el-select v-model="queryParams.semester" placeholder="选择学期" clearable @change="handleSearch">
          <el-option v-for="sem in semesterOptions" :key="sem" :label="sem" :value="sem" />
        </el-select>
        <el-select v-model="queryParams.classid" placeholder="选择班级" clearable @change="handleSearch">
          <el-option v-for="cls in classOptions" :key="cls.classid" :label="cls.classname" :value="cls.classid" />
        </el-select>
        <el-input
          v-model="queryParams.keyword"
          placeholder="作业标题"
          clearable
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
        row-key="homeworkid"
        actions-width="330"
        empty-text="还没有发布作业"
        empty-hint="点击右上角「发布作业」创建第一份作业"
        @change="loadData"
      >
        <template #type="{ row }">
          <el-tag :type="row.type === 1 ? 'success' : 'warning'" size="small" disable-transitions>
            {{ row.type === 1 ? '在线' : '文档' }}
          </el-tag>
        </template>

        <template #deadline="{ row }">{{ formatDate(row.deadline, 'minute') }}</template>

        <template #actions="{ row }">
          <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
          <el-button link type="primary" @click="handleViewErrorRate(row)">错题分析</el-button>
          <el-button link type="success" @click="openReadStats(row)">阅读统计</el-button>
          <el-button link type="warning" @click="openUnsubmitted(row)">未提交</el-button>
          <el-button link @click="openExportForRow(row)">导出PPT</el-button>
          <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </DataTable>
    </SectionCard>

    <!-- 发布 / 编辑作业 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="800px"
      :destroy-on-close="false"
      @closed="handleDialogClosed"
    >
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="作业标题" prop="title">
          <el-input v-model="formData.title" placeholder="请输入作业标题" />
        </el-form-item>

        <el-form-item label="所属课程" prop="courseid">
          <el-select v-model="formData.courseid" placeholder="请选择课程" class="full-width">
            <el-option
              v-for="item in currentSemesterCourses"
              :key="item.courseid"
              :label="`${item.coursename} (${item.semester})`"
              :value="item.courseid"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="作业类型" prop="type">
          <el-radio-group v-model="formData.type">
            <el-radio :value="1">在线答题</el-radio>
            <el-radio :value="2">文档上传</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="截止时间" prop="deadline">
          <el-date-picker
            v-model="formData.deadline"
            type="datetime"
            placeholder="请选择截止时间"
            class="full-width"
          />
        </el-form-item>

        <el-form-item v-if="formData.type === 1" label="试题设置">
          <QuestionEditor
            v-model="questionList"
            @ai-generate="showAIGenerateDialog"
            @ppt-generate="showPPTGenerateDialog"
            @paste-import="parseDialogVisible = true"
          />
        </el-form-item>

        <el-form-item v-if="formData.type === 2" label="作业内容" prop="content">
          <div v-if="dialogVisible" class="rich-editor">
            <Toolbar :editor="editorRef" :default-config="toolbarConfig" mode="default" class="rich-editor__bar" />
            <Editor
              v-model="formData.content"
              :default-config="editorConfig"
              mode="default"
              class="rich-editor__body"
              @on-created="handleEditorCreated"
            />
          </div>
        </el-form-item>

        <el-form-item v-if="formData.type === 2" label="附件上传">
          <div class="attachment">
            <div v-if="attachmentFile.name" class="attachment__info">
              <span class="attachment__name">{{ attachmentFile.name }}</span>
              <el-button type="danger" size="small" text @click="removeAttachment">删除</el-button>
            </div>
            <div v-else class="attachment__pick">
              <input
                ref="attachmentInputRef"
                type="file"
                accept=".doc,.docx,.pdf"
                class="attachment__input"
                @change="handleAttachmentChange"
              />
              <el-button type="primary" plain size="small" @click="attachmentInputRef?.click()">
                上传 Word / PDF 文件
              </el-button>
              <span class="attachment__tip">支持 .doc .docx .pdf，最大 20MB</span>
            </div>
            <div v-if="attachmentUploading" class="attachment__uploading">
              <el-icon class="is-loading"><Loading /></el-icon>
              <span>上传中...</span>
            </div>
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="handleCancel">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <HomeworkReadStatsDialog v-model="readStatsVisible" :homework="activeRow" />

    <UnsubmittedDialog v-model="unsubmittedVisible" :homework="activeRow" />

    <ParseQuestionsDialog v-model="parseDialogVisible" @imported="onQuestionsImported" />

    <ExportPptDialog
      v-model="exportDialogVisible"
      :homeworkid="exportHomeworkId"
      :homeworks="tableData"
    />

    <GenerateQuestionsDialog
      v-model="aiGenerateDialogVisible"
      title="🤖 AI智能出题"
      :loading="aiGenerateLoading"
      :result="aiGenerateResult"
      :ready="Boolean(formData.courseid && formData.title)"
      @confirm="handleAIGenerate"
    >
      <template #source>
        <div class="source-info">
          <div>课程：<strong>{{ currentCourseName }}</strong></div>
          <div>作业：<strong>{{ formData.title || '(请先填写作业标题)' }}</strong></div>
        </div>
      </template>
    </GenerateQuestionsDialog>

    <GenerateQuestionsDialog
      v-model="pptDialogVisible"
      title="PPT出题"
      source-label="PPT文件"
      :loading="pptGenerateLoading"
      loading-text="正在根据PPT生成试题，请稍候..."
      :ready="Boolean(pptFile)"
      @confirm="handlePPTGenerate"
    >
      <template #source>
        <div class="source-upload">
          <input type="file" accept=".ppt,.pptx" @change="handlePPTFileChange" />
          <span v-if="pptFile" class="source-upload__name">已选择：{{ pptFile.name }}</span>
        </div>
      </template>
    </GenerateQuestionsDialog>
  </PageLayout>
</template>

<script setup>
/** 教师作业管理 */
import { computed, onBeforeUnmount, onMounted, reactive, ref, shallowRef, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useStore } from 'vuex'
import { ElMessage } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import '@wangeditor/editor/dist/css/style.css'
import { Editor, Toolbar } from '@wangeditor/editor-for-vue'
import { DataTable, FilterBar, PageLayout, SectionCard, StatTile } from '@/components/ui'
import {
  createHomework,
  deleteHomework,
  listHomeworkByTeacher,
  readAttachmentContent,
  updateHomework,
  uploadAttachment
} from '@/api/homework'
import { generateQuestionsByAi, generateQuestionsFromPpt } from '@/api/ai'
import { listCoursesByTeacher } from '@/api/course'
import { listAllClasses } from '@/api/classes'
import { buildAiQuestions } from '@/utils/aiQuestions'
import { confirmDelete } from '@/utils/confirm'
import { buildHomeworkContent, parseHomeworkContent } from '@/utils/exam'
import { formatDate } from '@/utils/format'
import { normalizeQuestion, validateQuestions } from '@/utils/questionEditor'
import { getCurrentSemester } from '@/utils/semester'
import QuestionEditor from './components/QuestionEditor.vue'
import GenerateQuestionsDialog from './components/GenerateQuestionsDialog.vue'
import HomeworkReadStatsDialog from './components/HomeworkReadStatsDialog.vue'
import UnsubmittedDialog from './components/UnsubmittedDialog.vue'
import ParseQuestionsDialog from './components/ParseQuestionsDialog.vue'
import ExportPptDialog from './components/ExportPptDialog.vue'

const ATTACHMENT_MAX_BYTES = 20 * 1024 * 1024
const ATTACHMENT_ACCEPT = /\.(docx?|pdf)$/i

const columns = [
  { prop: 'title', label: '作业标题', minWidth: 170 },
  { prop: 'coursename', label: '课程', minWidth: 160 },
  { prop: 'classNamesStr', label: '班级', minWidth: 140 },
  { label: '类型', slot: 'type', width: 80, align: 'center' },
  { label: '截止时间', slot: 'deadline', width: 170, align: 'center' },
  { prop: 'submitCount', label: '提交', width: 70, align: 'center' }
]

const emptyForm = () => ({
  homeworkid: null,
  title: '',
  courseid: null,
  type: 1,
  deadline: null,
  content: '',
  attachment: ''
})

const formRules = {
  title: [{ required: true, message: '请输入作业标题', trigger: 'blur' }],
  courseid: [{ required: true, message: '请选择课程', trigger: 'change' }],
  type: [{ required: true, message: '请选择作业类型', trigger: 'change' }],
  deadline: [{ required: true, message: '请选择截止时间', trigger: 'change' }]
}

const CURRENT_SEMESTER = getCurrentSemester()

const store = useStore()
const router = useRouter()

const tableData = ref([])
const total = ref(0)
const loading = ref(false)
const courseList = ref([])
const classList = ref([])
const queryParams = reactive({
  keyword: '',
  semester: CURRENT_SEMESTER,
  classid: null,
  page: 1,
  pageSize: 10
})

const dialogVisible = ref(false)
const dialogTitle = ref('发布作业')
const formRef = ref(null)
const formData = reactive(emptyForm())
const questionList = ref([])
const submitLoading = ref(false)
const isEdit = ref(false)

const attachmentInputRef = ref(null)
const attachmentFile = reactive({ name: '', filepath: '', url: '' })
const attachmentUploading = ref(false)

const editorRef = shallowRef()
const toolbarConfig = {}
const editorConfig = { placeholder: '请输入作业内容/要求...' }

/** 各子弹窗共用当前操作的这一行 */
const activeRow = ref({})
const readStatsVisible = ref(false)
const unsubmittedVisible = ref(false)
const parseDialogVisible = ref(false)

const exportDialogVisible = ref(false)
const exportHomeworkId = ref(null)

const aiGenerateDialogVisible = ref(false)
const aiGenerateLoading = ref(false)
const aiGenerateResult = reactive({ success: false, message: '' })

const pptDialogVisible = ref(false)
const pptGenerateLoading = ref(false)
const pptFile = ref(null)

const semesterOptions = computed(() =>
  Array.from(new Set(courseList.value.map((c) => c.semester).filter(Boolean))).sort()
)

const currentSemesterCourses = computed(() =>
  courseList.value.filter((c) => c.semester === CURRENT_SEMESTER)
)

/**
 * 班级下拉的候选：列表与课程都只回传了班级名称字符串，
 * 这里按名称反查出 classid 才能作为查询条件。
 */
const classOptions = computed(() => {
  const picked = new Map()
  const collect = (source) => {
    for (const item of source || []) {
      const names = String(item.classNamesStr || '')
        .split(/[，,]/)
        .map((s) => s.trim())
        .filter(Boolean)
      for (const name of names) {
        const matched = classList.value.find((c) => c.classname === name)
        if (matched && !picked.has(matched.classid)) {
          picked.set(matched.classid, { classid: matched.classid, classname: matched.classname })
        }
      }
    }
  }
  collect(tableData.value)
  collect(courseList.value)
  return Array.from(picked.values())
})

/** 截止时间未到的作业数，用于顶部概览 */
const ongoingCount = computed(
  () => tableData.value.filter((row) => row.deadline && new Date(row.deadline) > new Date()).length
)

const currentCourseName = computed(
  () => courseList.value.find((c) => c.courseid === formData.courseid)?.coursename || '(请先选择课程)'
)

const loadData = async () => {
  loading.value = true
  try {
    const res = await listHomeworkByTeacher(store.state.user?.teacherid, queryParams)
    tableData.value = res.data || []
    total.value = res.total || 0
  } catch (e) {
    console.error('加载作业失败:', e)
  } finally {
    loading.value = false
  }
}

const loadCourses = async () => {
  try {
    const res = await listCoursesByTeacher(store.state.user?.teacherid)
    courseList.value = res.data || []
  } catch (e) {
    console.error('加载课程失败:', e)
  }
}

const loadClasses = async () => {
  try {
    const res = await listAllClasses()
    classList.value = res.data || []
  } catch (e) {
    console.error('加载班级失败:', e)
  }
}

const handleSearch = () => {
  queryParams.page = 1
  loadData()
}

const handleReset = () => {
  Object.assign(queryParams, {
    keyword: '',
    semester: CURRENT_SEMESTER,
    classid: null,
    page: 1
  })
  loadData()
}

/* ---------- 附件 ---------- */

const removeAttachment = () => {
  attachmentFile.name = ''
  attachmentFile.filepath = ''
  attachmentFile.url = ''
  formData.attachment = ''
}

/** 提取失败不影响发布流程，静默跳过 */
const loadAttachmentContent = async (filepath) => {
  try {
    const res = await readAttachmentContent(filepath)
    const text = String(res?.data || '').trim()
    if (!text) return

    const html = text
      .split('\n')
      .filter((line) => line.trim())
      .map((line) => `<p>${line}</p>`)
      .join('')

    if (editorRef.value) editorRef.value.setHtml(html)
    else formData.content = html
    ElMessage.success('已自动提取附件内容')
  } catch (e) {
    console.error('读取附件内容失败:', e)
  }
}

const handleAttachmentChange = async (event) => {
  const file = event?.target?.files?.[0]
  if (!file) return

  if (!ATTACHMENT_ACCEPT.test(file.name)) {
    ElMessage.warning('仅支持 Word(.doc/.docx) 和 PDF 文件')
    return
  }
  if (file.size > ATTACHMENT_MAX_BYTES) {
    ElMessage.warning('文件大小不能超过 20MB')
    return
  }

  attachmentUploading.value = true
  try {
    const payload = new FormData()
    payload.append('file', file)
    const res = await uploadAttachment(payload)
    if (res.data) {
      attachmentFile.name = res.data.originalName || file.name
      attachmentFile.filepath = res.data.filepath
      attachmentFile.url = res.data.url
      formData.attachment = res.data.filepath
      ElMessage.success('附件上传成功')
      loadAttachmentContent(res.data.filepath)
    }
  } catch (e) {
    console.error('附件上传失败:', e)
  } finally {
    attachmentUploading.value = false
    if (event.target) event.target.value = ''
  }
}

/* ---------- 富文本编辑器 ---------- */

const handleEditorCreated = (editor) => {
  editorRef.value = editor
}

/** wangEditor 需要手动销毁，否则切换作业类型或关闭弹窗后会残留实例 */
const destroyEditor = () => {
  if (!editorRef.value) return
  try {
    editorRef.value.destroy()
  } catch (e) {
    console.error('销毁编辑器失败:', e)
  }
  editorRef.value = null
}

watch(
  () => formData.type,
  (type) => {
    if (type !== 2) destroyEditor()
  }
)

onBeforeUnmount(destroyEditor)

/* ---------- 增删改 ---------- */

const handleAdd = () => {
  isEdit.value = false
  dialogTitle.value = '发布作业'
  removeAttachment()
  dialogVisible.value = true
}

const handleEdit = (row) => {
  isEdit.value = true
  dialogTitle.value = '编辑作业'
  Object.assign(formData, row)

  const parsed = row.type === 1 ? parseHomeworkContent(row.content) : null
  questionList.value = Array.isArray(parsed?.questions)
    ? parsed.questions.map(normalizeQuestion)
    : []

  if (row.attachment) {
    attachmentFile.name = row.attachment.split('/').pop() || '已上传附件'
    attachmentFile.filepath = row.attachment
    attachmentFile.url = `/ZhiYanAcademy/files/download?path=${encodeURIComponent(row.attachment)}`
  } else {
    removeAttachment()
  }

  dialogVisible.value = true
}

const handleDelete = async (row) => {
  if (!(await confirmDelete(`确定要删除作业「${row.title}」吗？学生的提交记录将一并移除。`))) return
  try {
    await deleteHomework(row.homeworkid)
    ElMessage.success('删除成功')
    loadData()
  } catch (e) {
    console.error('删除作业失败:', e)
  }
}

/** 后端要求 yyyy-MM-dd HH:mm:ss，日期选择器给的是 Date */
const toBackendTime = (value) => {
  if (!value) return null
  const d = new Date(value)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

const handleSubmit = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  if (formData.type === 1) {
    const invalid = validateQuestions(questionList.value)
    if (invalid) return ElMessage.warning(invalid)
  }

  submitLoading.value = true
  try {
    const payload = {
      ...formData,
      teacherid: store.state.user?.teacherid,
      deadline: toBackendTime(formData.deadline)
    }
    if (formData.type === 1) {
      payload.content = buildHomeworkContent(questionList.value)
    }

    if (isEdit.value) {
      await updateHomework(payload)
      ElMessage.success('更新成功')
    } else {
      await createHomework(payload)
      ElMessage.success('发布成功')
    }

    destroyEditor()
    dialogVisible.value = false
    loadData()
  } catch (e) {
    console.error('保存作业失败:', e)
  } finally {
    submitLoading.value = false
  }
}

const handleCancel = () => {
  destroyEditor()
  dialogVisible.value = false
}

const handleDialogClosed = () => {
  Object.assign(formData, emptyForm())
  questionList.value = []
  removeAttachment()
  formRef.value?.resetFields()
}

const handleViewErrorRate = (row) => {
  router.push({ path: '/teacher/statistics', query: { homeworkid: row.homeworkid } })
}

/* ---------- 子弹窗 ---------- */

const openReadStats = (row) => {
  activeRow.value = row
  readStatsVisible.value = true
}

const openUnsubmitted = (row) => {
  activeRow.value = row
  unsubmittedVisible.value = true
}

const openExportForRow = (row) => {
  exportHomeworkId.value = row.homeworkid
  exportDialogVisible.value = true
}

const openExportForAll = () => {
  exportHomeworkId.value = null
  exportDialogVisible.value = true
}

const onQuestionsImported = (questions) => {
  questionList.value.push(...questions)
}

/* ---------- AI 出题 ---------- */

/** 出题依据是课程与标题，缺一不可 */
const requireCourseAndTitle = () => {
  if (!formData.courseid) {
    ElMessage.warning('请先选择所属课程')
    return false
  }
  if (!formData.title?.trim()) {
    ElMessage.warning('请先填写作业标题')
    return false
  }
  return true
}

const showAIGenerateDialog = () => {
  if (!requireCourseAndTitle()) return
  aiGenerateResult.success = false
  aiGenerateResult.message = ''
  aiGenerateDialogVisible.value = true
}

// 课程、标题与题量的前置校验由 GenerateQuestionsDialog 在按钮上把关
const handleAIGenerate = async (config) => {
  aiGenerateLoading.value = true
  aiGenerateResult.success = false
  aiGenerateResult.message = ''

  try {
    const res = await generateQuestionsByAi({
      subject: `${currentCourseName.value} - ${formData.title}`,
      ...config
    })
    const built = buildAiQuestions(res?.data, {
      scoringStandard: config.scoringStandard,
      blankCount: config.blankCount
    })
    if (!built.success) {
      aiGenerateResult.message = built.message
      return
    }

    questionList.value.push(...built.questions)
    aiGenerateResult.success = true
    aiGenerateResult.message = `成功生成 ${built.questions.length} 道试题`
    ElMessage.success(`AI已生成 ${built.questions.length} 道试题`)
    // 留一秒让教师看到成功提示，再收起对话框
    setTimeout(() => {
      aiGenerateDialogVisible.value = false
    }, 1000)
  } catch (e) {
    console.error('AI出题失败:', e)
    aiGenerateResult.message = `AI出题失败：${e?.message || '请稍后重试'}`
  } finally {
    aiGenerateLoading.value = false
  }
}

const showPPTGenerateDialog = () => {
  if (!requireCourseAndTitle()) return
  pptFile.value = null
  pptDialogVisible.value = true
}

const handlePPTFileChange = (event) => {
  const file = event?.target?.files?.[0]
  if (file && !/\.pptx?$/i.test(file.name)) {
    ElMessage.warning('仅支持 PPT 文件（.ppt 或 .pptx）')
    pptFile.value = null
    event.target.value = ''
    return
  }
  pptFile.value = file || null
}

const handlePPTGenerate = async (config) => {
  if (!pptFile.value) return
  pptGenerateLoading.value = true
  try {
    const payload = new FormData()
    payload.append('file', pptFile.value)
    Object.entries(config).forEach(([key, value]) => payload.append(key, value))

    const res = await generateQuestionsFromPpt(payload)
    const built = buildAiQuestions(res?.data, {
      scoringStandard: config.scoringStandard,
      blankCount: config.blankCount
    })
    if (!built.success) {
      ElMessage.error(built.message)
      return
    }

    questionList.value.push(...built.questions)
    ElMessage.success(`已根据PPT生成 ${built.questions.length} 道试题`)
    pptDialogVisible.value = false
  } catch (e) {
    console.error('PPT出题失败:', e)
    const timeout = String(e?.message || '').includes('timeout')
    ElMessage.error(timeout ? 'PPT出题超时，请减少题目数量或稍后重试' : 'PPT出题失败，请稍后重试')
  } finally {
    pptGenerateLoading.value = false
  }
}

onMounted(() => {
  loadClasses()
  loadCourses()
  loadData()
})
</script>

<style scoped>
.full-width {
  width: 100%;
}

.rich-editor {
  width: 100%;
  border: 1px solid var(--zy-border-default);
  border-radius: var(--zy-radius-sm);
  overflow: hidden;
}

.rich-editor__bar {
  border-bottom: 1px solid var(--zy-border-default);
}

.rich-editor__body {
  height: 300px;
  overflow-y: hidden;
}

.attachment {
  width: 100%;
}

.attachment__info {
  display: flex;
  align-items: center;
  gap: var(--zy-space-2);
  padding: var(--zy-space-2) var(--zy-space-3);
  background: var(--zy-bg-sunken);
  border-radius: var(--zy-radius-sm);
}

.attachment__name {
  color: var(--zy-text-link);
  word-break: break-all;
}

.attachment__pick {
  display: flex;
  align-items: center;
  gap: var(--zy-space-2);
  flex-wrap: wrap;
}

.attachment__input {
  display: none;
}

.attachment__tip,
.attachment__uploading {
  font-size: var(--zy-font-size-xs);
  color: var(--zy-text-tertiary);
}

.attachment__uploading {
  display: flex;
  align-items: center;
  gap: var(--zy-space-1);
  color: var(--zy-brand-base);
  margin-top: var(--zy-space-1);
}

.source-info {
  background: var(--zy-bg-sunken);
  padding: var(--zy-space-2) var(--zy-space-3);
  border-radius: var(--zy-radius-sm);
  color: var(--zy-text-secondary);
  line-height: var(--zy-line-height-relaxed);
}

.source-info strong {
  color: var(--zy-text-primary);
}

.source-upload {
  display: flex;
  flex-direction: column;
  gap: var(--zy-space-2);
}

.source-upload__name {
  font-size: var(--zy-font-size-xs);
  color: var(--zy-text-secondary);
}
</style>
