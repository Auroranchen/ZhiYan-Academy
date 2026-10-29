<template>
  <PageLayout
    title="提交作业"
    :description="homework?.title || ''"
    :breadcrumb="[{ label: '我的作业', to: '/student/myhomework' }, { label: '提交作业' }]"
  >
    <template #actions>
      <el-button @click="goBack">返回</el-button>
    </template>

    <el-card v-if="homework" class="homework-info">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="作业标题" :span="2">
          {{ homework.title }}
        </el-descriptions-item>
        <el-descriptions-item label="所属课程">{{ homework.coursename }}</el-descriptions-item>
        <el-descriptions-item label="作业类型">{{ homework.type === 1 ? '在线答题' : '文档上传' }}</el-descriptions-item>
        <el-descriptions-item label="截止时间" :span="2">
          <span :class="{ 'expired': isExpired }">{{ formatDate(homework.deadline) }}</span>
          <el-tag v-if="isExpired" type="danger" size="small" style="margin-left: 10px">已过期</el-tag>
        </el-descriptions-item>
        <el-descriptions-item v-if="showRequirement" label="作业要求" :span="2">
          <div v-safe-html="homework.content"></div>
        </el-descriptions-item>
        <el-descriptions-item v-if="homework.attachment" label="附件" :span="2">
          <a :href="`/ZhiYanAcademy/homework/downloadAttachment?homeworkId=${homework.homeworkid}`" target="_blank" rel="noopener noreferrer" style="color: #409eff; text-decoration: none;">
            下载附件
          </a>
        </el-descriptions-item>
        <el-descriptions-item v-if="attachmentContent" label="附件内容" :span="2">
          <div class="attachment-content-preview" v-text="attachmentContent"></div>
        </el-descriptions-item>
        <el-descriptions-item v-if="isExamMode" label="试题数量">{{ examQuestions.length }} 题</el-descriptions-item>
      </el-descriptions>
    </el-card>
    
    <el-card v-if="homework && !isExpired && !gradeResult" class="submit-form">
      <template #header><span>提交作业</span></template>
      
      <!-- 在线答题 -->
      <div v-if="homework.type === 1">
        <div v-if="isExamMode" class="exam-container">
          <div v-for="(question, index) in examQuestions" :key="question.id" class="exam-question">
            <div class="question-header">
              <div class="question-title">
                <span class="question-index">{{ index + 1 }}.</span>
                <el-tag size="small" :type="questionTypeTone(question.type)" class="question-tag">
                  {{ questionTypeLabel(question.type) }}
                </el-tag>
                <span>{{ question.title }}</span>
              </div>
            </div>
            <div v-if="question.type === 'single'" class="question-body">
              <el-radio-group v-model="examAnswers[question.id]">
                <el-radio v-for="(option, optIndex) in question.options" :key="`${question.id}-single-${optIndex}`" :value="optIndex">
                  {{ getOptionLabel(optIndex) }}. {{ option }}
                </el-radio>
              </el-radio-group>
            </div>
            <div v-if="question.type === 'multi'" class="question-body">
              <el-checkbox-group v-model="examAnswers[question.id]">
                <el-checkbox v-for="(option, optIndex) in question.options" :key="`${question.id}-multi-${optIndex}`" :value="optIndex">
                  {{ getOptionLabel(optIndex) }}. {{ option }}
                </el-checkbox>
              </el-checkbox-group>
            </div>
            <div v-if="question.type === 'judge'" class="question-body">
              <el-radio-group v-model="examAnswers[question.id]">
                <el-radio :value="true">正确</el-radio>
                <el-radio :value="false">错误</el-radio>
              </el-radio-group>
            </div>
            <div v-if="question.type === 'short'" class="question-body">
              <el-input v-model="examAnswers[question.id]" type="textarea" :rows="3" placeholder="请输入答案" />
            </div>
            <div v-if="question.type === 'blank'" class="question-body">
              <el-input v-model="examAnswers[question.id]" placeholder="请输入答案" />
            </div>
          </div>
        </div>
        <div v-else class="legacy-answer">
          <el-input v-model="submitContent" type="textarea" :rows="8" placeholder="请输入答案" />
        </div>
        <div class="submit-btn">
          <el-button type="primary" :loading="submitLoading" @click="submitOnline">{{ submitLoading ? 'AI评分中...' : '提交答案' }}</el-button>
        </div>
      </div>
      
      <!-- 文档上传 -->
      <div v-if="homework.type === 2">
        <el-upload
          ref="uploadRef"
          :action="uploadUrl"
          :data="uploadData"
          :before-upload="beforeUpload"
          :on-success="onUploadSuccess"
          :on-error="onUploadError"
          :limit="1"
          accept=".doc,.docx"
          drag
        >
          <el-icon class="el-icon--upload"><upload-filled /></el-icon>
          <div class="el-upload__text">将文件拖到此处，或<em>点击上传</em></div>
          <template #tip>
            <div class="el-upload__tip">只能上传 Word 文档（.doc 或 .docx），且不超过 10MB</div>
          </template>
        </el-upload>
      </div>
    </el-card>
    
    <el-alert v-if="isExpired" title="作业已过截止时间，无法提交" type="error" :closable="false" />
    
    <!-- AI评分结果展示 -->
    <el-card v-if="gradeResult" class="grade-result-card">
      <template #header>
        <div class="grade-result-header">
          <span>🎯 AI评分结果</span>
          <el-tag type="success" size="small">待老师确认</el-tag>
        </div>
      </template>
      <div class="grade-result-body">
        <div class="grade-score">
          <div class="score-label">AI评分</div>
          <div class="score-number">{{ gradeResult.score !== null && gradeResult.score !== undefined ? gradeResult.score : '-' }}</div>
          <div class="score-unit">/ 100</div>
        </div>
        <div class="grade-comment" v-if="gradeResult.comment">
          <div class="comment-label">AI评语</div>
          <pre class="comment-text">{{ gradeResult.comment.replace('[AI自动评分] ', '') }}</pre>
        </div>
        <div class="grade-notice">
          <el-alert title="成绩已提交，待老师确认后将显示为「已评分」" type="info" :closable="false" show-icon />
        </div>
        <div class="grade-actions">
          <el-button type="primary" @click="goBack">返回我的作业</el-button>
        </div>
      </div>
    </el-card>
  </PageLayout>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useStore } from 'vuex'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { PageLayout } from '@/components/ui'
import {
  getHomework,
  markHomeworkRead as markHomeworkReadApi,
  readAttachmentContent,
  submitStudentHomework
} from '@/api/homework'
import { buildStudentContent, getOptionLabel, parseHomeworkContent, stripQuestionAnswers } from '@/utils/exam'
import { formatDate } from '@/utils/format'
import { questionTypeLabel, questionTypeTone } from '@/utils/questionTypes'

/** 学生提交作业：在线答题与文档上传两种形态 */

/** 与后端一致的附件大小上限 */
const UPLOAD_MAX_BYTES = 10 * 1024 * 1024

const route = useRoute()
const router = useRouter()
const store = useStore()

const homework = ref(null)
const submitContent = ref('')
const submitLoading = ref(false)
const uploadRef = ref(null)
const examQuestions = ref([])
const examAnswers = reactive({})
const hasExamContent = ref(false)
const attachmentContent = ref('')
/** 提交后后端可能直接返回 AI 评分，有值就地展示而不跳转 */
const gradeResult = ref(null)

const homeworkId = computed(() => route.query.id || route.query.homeworkId)
const isExpired = computed(
  () => homework.value?.deadline && new Date() > new Date(homework.value.deadline)
)
const isExamMode = computed(() => homework.value?.type === 1 && examQuestions.value.length > 0)
const showRequirement = computed(() => homework.value?.type === 2 || !hasExamContent.value)

const uploadUrl = '/ZhiYanAcademy/studenthomework/upload'
const uploadData = computed(() => ({
  homeworkid: homeworkId.value,
  studentid: store.state.user?.studentid
}))

const goBack = () => router.push('/student/myhomework')

const markRead = async () => {
  const studentid = store.state.user?.studentid
  const homeworkid = homework.value?.homeworkid
  if (!homeworkid || !studentid) return
  try {
    await markHomeworkReadApi(homeworkid, studentid)
  } catch (error) {
    // 阅读状态失败不影响提交流程
  }
}

const loadAttachmentContent = async (filepath) => {
  try {
    const res = await readAttachmentContent(filepath, homeworkId.value)
    const text = String(res?.data || '').trim()
    if (text) attachmentContent.value = text
  } catch (error) {
    console.error('读取附件内容失败:', error)
  }
}

const normalizeQuestion = (question, index) => ({
  id: question?.id || `legacy_${index}`,
  type: question?.type,
  title: question?.title || '',
  score: Number(question?.score) || 0,
  options: Array.isArray(question?.options) ? question.options : [],
  answer: question?.answer
})

/** 各题型的作答初值不同，多选是数组、单选与判断是空值 */
const resetAnswers = () => {
  Object.keys(examAnswers).forEach((key) => delete examAnswers[key])
  examQuestions.value.forEach((question) => {
    if (question.type === 'multi') examAnswers[question.id] = []
    else if (question.type === 'single' || question.type === 'judge') examAnswers[question.id] = null
    else examAnswers[question.id] = ''
  })
}

/** 给 AI 助手的上下文只带标题与题型概况，不泄漏题目原文 */
const buildAssistantBrief = (hw) => {
  if (hw.type !== 1 || !examQuestions.value.length) {
    return `当前作业：${hw.title || ''}（文档上传作业）`
  }
  const counts = {}
  examQuestions.value.forEach((q) => { counts[q.type] = (counts[q.type] || 0) + 1 })
  const desc = Object.entries(counts)
    .map(([type, count]) => `${questionTypeLabel(type)}${count}道`)
    .join('、')
  return `当前作业：${hw.title || ''}（在线答题，共${examQuestions.value.length}道题：${desc}）`
}

const loadHomework = async () => {
  try {
    const res = await getHomework(homeworkId.value)
    homework.value = res.data
    await markRead()

    if (homework.value?.attachment) loadAttachmentContent(homework.value.attachment)

    if (homework.value?.type === 1) {
      const parsed = parseHomeworkContent(homework.value.content)
      hasExamContent.value = Boolean(parsed)
      examQuestions.value = Array.isArray(parsed?.questions)
        ? parsed.questions.map(normalizeQuestion)
        : []
    } else {
      examQuestions.value = []
      hasExamContent.value = false
    }
    resetAnswers()

    if (homework.value) {
      store.commit('setHomeworkContext', buildAssistantBrief(homework.value))
      // 作业加固依据：服务端拿这个 ID 查库判定是否已提交。
      // 前端不再自报「当前处于作业模式」——那是可以被绕过的。
      store.commit('setHomeworkId', homework.value.homeworkid || homeworkId.value)
      store.commit('setAiCourseId', homework.value.courseid)
    }
  } catch (error) {
    console.error('加载作业失败:', error)
    ElMessage.error('加载作业失败')
  }
}

/** 返回第一道未完成题目的提示，全部完成则返回空串 */
const validateAnswers = () => {
  for (let i = 0; i < examQuestions.value.length; i += 1) {
    const question = examQuestions.value[i]
    const answer = examAnswers[question.id]
    const missing =
      question.type === 'multi'
        ? !Array.isArray(answer) || !answer.length
        : question.type === 'judge'
          ? typeof answer !== 'boolean'
          : answer === null || answer === undefined || !String(answer).trim()
    if (missing) return `请完成第${i + 1}题`
  }
  return ''
}

/** 提交与上传都可能带回评分，出口统一 */
const applyGradeResult = (data) => {
  if (data && (data.score !== null || data.comment)) {
    gradeResult.value = { score: data.score, comment: data.comment }
  } else {
    router.push('/student/myhomework')
  }
}

const submitOnline = async () => {
  let content = submitContent.value

  if (isExamMode.value) {
    const invalid = validateAnswers()
    if (invalid) return ElMessage.warning(invalid)

    const answerPayload = {}
    examQuestions.value.forEach((question) => {
      answerPayload[question.id] = examAnswers[question.id]
    })
    content = buildStudentContent(stripQuestionAnswers(examQuestions.value), answerPayload)
  } else if (!submitContent.value.trim()) {
    return ElMessage.warning('请输入答案内容')
  }

  submitLoading.value = true
  try {
    const res = await submitStudentHomework({
      homeworkid: homeworkId.value,
      studentid: store.state.user?.studentid,
      content
    })
    ElMessage.success('提交成功')
    applyGradeResult(res.data)
  } catch (error) {
    console.error('提交失败:', error)
  } finally {
    submitLoading.value = false
  }
}

const beforeUpload = (file) => {
  if (!/\.docx?$/i.test(file.name)) {
    ElMessage.error('只能上传 Word 文档')
    return false
  }
  if (file.size > UPLOAD_MAX_BYTES) {
    ElMessage.error('文件大小不能超过 10MB')
    return false
  }
  return true
}

const onUploadSuccess = (response) => {
  if (response.code !== 200) {
    ElMessage.error(response.msg || '上传失败')
    return
  }
  ElMessage.success('上传成功')
  applyGradeResult(response.data)
}

const onUploadError = () => ElMessage.error('上传失败')

onMounted(() => {
  if (homeworkId.value) {
    loadHomework()
  } else {
    ElMessage.warning('请先选择要提交的作业')
    router.replace('/student/homework/course')
  }
})

onUnmounted(() => {
  store.commit('clearHomeworkContext')
})
</script>

<style scoped>
.expired { color: var(--zy-danger-base); }
.question-tag { margin-right: var(--zy-space-2); }
.exam-container { display: flex; flex-direction: column; gap: var(--zy-space-4); }
.exam-question,
.legacy-answer {
  border: 1px solid var(--zy-border-subtle);
  padding: var(--zy-space-3);
  border-radius: var(--zy-radius-sm);
}
.question-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: var(--zy-space-3); }
.question-title { font-weight: var(--zy-font-weight-semibold); color: var(--zy-text-primary); }
.question-index { margin-right: var(--zy-space-2); }
.question-body { margin-top: var(--zy-space-2); }

.attachment-content-preview {
  max-height: 300px;
  overflow-y: auto;
  padding: var(--zy-space-3);
  background: var(--zy-bg-sunken);
  border: 1px solid var(--zy-border-subtle);
  border-radius: var(--zy-radius-sm);
  line-height: var(--zy-line-height-relaxed);
  color: var(--zy-text-primary);
  white-space: pre-wrap;
}

.grade-result-header { display: flex; justify-content: space-between; align-items: center; font-weight: var(--zy-font-weight-semibold); }
.grade-result-body { text-align: center; }
.grade-score { display: flex; align-items: baseline; justify-content: center; margin: var(--zy-space-5) 0; }
.score-label { color: var(--zy-text-tertiary); margin-right: var(--zy-space-3); }
.score-number { font-size: var(--zy-font-size-4xl); font-weight: var(--zy-font-weight-bold); color: var(--zy-brand-base); }
.score-unit { color: var(--zy-text-tertiary); margin-left: var(--zy-space-1); }

.grade-comment {
  text-align: left;
  margin: var(--zy-space-5) 0;
  padding: var(--zy-space-4);
  background: var(--zy-bg-sunken);
  border-radius: var(--zy-radius-md);
}
.comment-label { color: var(--zy-text-tertiary); margin-bottom: var(--zy-space-2); }
.comment-text {
  white-space: pre-wrap;
  line-height: var(--zy-line-height-relaxed);
  color: var(--zy-text-primary);
  font-family: inherit;
  margin: 0;
}
.grade-notice { margin: var(--zy-space-4) 0; }
.grade-actions { margin-top: var(--zy-space-5); }
</style>
