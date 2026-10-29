<template>
  <el-dialog
    :model-value="modelValue"
    title="作业详情"
    width="760px"
    top="6vh"
    @update:model-value="$emit('update:modelValue', $event)"
    @opened="bindAiAnalysisButtons"
    @closed="stopPolling"
  >
    <header class="head">
      <h3 class="head__title">{{ detail.homeworktitle }}</h3>
      <el-tag :type="STATUS_TONE[gradeState]" size="small" disable-transitions>
        {{ STATUS_TEXT[gradeState] }}
      </el-tag>
    </header>

    <div class="head__meta">
      <span>{{ detail.coursename }}</span>
      <span>{{ detail.homeworktype === 1 ? '在线答题' : '文档上传' }}</span>
      <span>提交于 {{ formatDate(detail.submittime, 'minute') }}</span>
    </div>

    <SectionCard :title="detail.homeworktype === 1 ? '答题详情' : '作业内容'">
      <div v-if="detail.homeworktype === 1" class="answers" v-safe-html="answerHtml"></div>

      <template v-else>
        <el-button type="primary" @click="download">下载作业文件</el-button>
        <div v-if="detail.content" class="document">
          <p class="document__label">文档内容预览</p>
          <pre class="document__text">{{ detail.content }}</pre>
        </div>
      </template>
    </SectionCard>

    <SectionCard title="评分信息">
      <div class="score">
        <div class="score__value">
          <span class="score__label">成绩</span>
          <b :class="{ 'is-graded': detail.score != null }">{{ detail.score ?? '—' }}</b>
        </div>

        <div class="score__comment">
          <span class="score__label">评语</span>

          <el-tag v-if="gradeBadge" :type="gradeBadge.tone" size="small" disable-transitions>
            {{ gradeBadge.text }}
          </el-tag>

          <pre class="score__text">{{ commentText }}</pre>

          <el-button v-if="detail.gradingStatus === 'FAILED'" type="warning" @click="retry">
            重新评分
          </el-button>
        </div>
      </div>
    </SectionCard>

    <SectionCard title="作业答疑" :count="comments.length">
      <div v-loading="commentsLoading" class="comments">
        <el-empty
          v-if="!comments.length"
          description="暂无问题，有疑问可以在下方提问"
          :image-size="80"
        />

        <article v-for="comment in comments" :key="comment.id" class="comment">
          <header class="comment__head">
            <span class="comment__author">{{ comment.studentname || '我' }}</span>
            <span class="comment__time">{{ formatDate(comment.createtime, 'minute') }}</span>
            <el-button
              v-if="comment.studentid === studentid"
              link
              type="danger"
              @click="removeComment(comment)"
            >
              删除
            </el-button>
          </header>

          <p class="comment__content">{{ comment.content }}</p>

          <div v-for="reply in comment.replies || []" :key="reply.id" class="reply">
            <span class="reply__author">{{ reply.teachername }} 老师：</span>
            <span>{{ reply.content }}</span>
            <span class="reply__time">{{ formatDate(reply.createtime, 'minute') }}</span>
          </div>
        </article>
      </div>

      <div class="ask">
        <el-input
          v-model="question"
          type="textarea"
          :rows="3"
          maxlength="500"
          show-word-limit
          placeholder="请输入你的问题…"
        />
        <el-button type="primary" :loading="submitting" @click="ask">提交问题</el-button>
      </div>
    </SectionCard>
  </el-dialog>
</template>

<script setup>
/**
 * 作业详情弹窗（学生端）
 *
 * 从「我的作业」列表页拆出：答题详情、评分状态轮询、作业答疑各自有独立的
 * 数据与生命周期，和列表的筛选分页混在一个组件里会互相干扰。
 */
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useStore } from 'vuex'
import { ElMessage } from 'element-plus'
import { SectionCard } from '@/components/ui'
import {
  createHomeworkComment,
  deleteHomeworkComment,
  getStudentHomework,
  listHomeworkComments,
  markHomeworkRead,
  retryGrade
} from '@/api/homework'
import { mergeStudentAnswers, renderExamResultHtml } from '@/utils/exam'
import { bindAiAnalysisButtons } from '@/utils/aiAnalysis'
import { confirmDelete } from '@/utils/confirm'
import { formatDate } from '@/utils/format'

/** AI 判分是异步的，弹窗打开时若还在评就轮询 */
const POLL_INTERVAL = 3000
const GRADING_STATUSES = ['PENDING', 'GRADING']

const STATUS_TEXT = { graded: '已评分', pending: '待老师确认', ungraded: '待批改' }
const STATUS_TONE = { graded: 'success', pending: 'primary', ungraded: 'warning' }

const ANSWER_LABELS = {
  answerLabel: '我的答案',
  correctAnswerLabel: '正确答案',
  analysisLabel: '解析',
  trueLabel: '正确',
  falseLabel: '错误',
  emptyLabel: '未作答'
}

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** 列表行，只需要 id；详情由本组件自行拉取 */
  submissionId: { type: [Number, String], default: null }
})

const emit = defineEmits(['update:modelValue', 'graded'])

const store = useStore()
const studentid = computed(() => store.state.user?.studentid)

const detail = ref({})

const gradeState = computed(() => {
  if (detail.value.status === 2) return 'graded'
  return detail.value.score != null ? 'pending' : 'ungraded'
})

const gradeBadge = computed(() => {
  const status = detail.value.gradingStatus
  if (GRADING_STATUSES.includes(status)) return { text: 'AI 评分中…', tone: 'warning' }
  if (status === 'REVIEW_REQUIRED') return { text: 'AI 预评分 · 待教师复核', tone: 'warning' }
  if (status === 'FAILED') return { text: '评分失败', tone: 'danger' }
  if (detail.value.comment?.startsWith('[AI自动评分]')) return { text: 'AI 评分', tone: 'info' }
  return null
})

const commentText = computed(() => {
  if (GRADING_STATUSES.includes(detail.value.gradingStatus)) return 'AI 正在评分中，请稍候…'
  const comment = detail.value.comment
  if (!comment) return '暂无评语'
  // 状态已经用标签表达，评语正文里的前缀是重复信息
  return comment.replace('[AI自动评分] ', '').replace('[AI预评分·待教师复核] ', '')
})

/** 学生提交的内容不含标准答案，展示批改结果前要回填 */
const answerHtml = computed(() => {
  if (detail.value.homeworktype !== 1) return detail.value.content || ''

  const merged = mergeStudentAnswers(detail.value.content, detail.value.homeworkcontent)
  if (!merged) return detail.value.content || ''

  return renderExamResultHtml(merged.questions, merged.answers, null, {
    ...ANSWER_LABELS,
    showAnalysisButton: true
  })
})

/* ---------- 加载与轮询 ---------- */

let pollTimer = null
let pollingActive = false
let pollingGeneration = 0
let disposed = false

const stopPolling = () => {
  pollingActive = false
  pollingGeneration += 1
  clearTimeout(pollTimer)
  pollTimer = null
}

const startPollingIfGrading = () => {
  if (disposed) return
  stopPolling()
  if (!GRADING_STATUSES.includes(detail.value.gradingStatus)) return

  pollingActive = true
  const generation = pollingGeneration
  const poll = async () => {
    if (!pollingActive || generation !== pollingGeneration) return

    const res = await getStudentHomework(props.submissionId).catch(() => null)
    try {
      if (generation !== pollingGeneration) return
      if (!res?.data) {
        stopPolling()
        return
      }

      detail.value = res.data
      if (!GRADING_STATUSES.includes(res.data.gradingStatus)) {
        stopPolling()
        // 分数变了，让列表页刷新
        emit('graded')
      }
    } finally {
      if (pollingActive && generation === pollingGeneration) {
        pollTimer = setTimeout(poll, POLL_INTERVAL)
      }
    }
  }

  pollTimer = setTimeout(poll, POLL_INTERVAL)
}

const load = async () => {
  const res = await getStudentHomework(props.submissionId).catch(() => null)
  if (disposed || !res?.data) return

  detail.value = res.data
  startPollingIfGrading()

  if (res.data.homeworkid && studentid.value) {
    markHomeworkRead(res.data.homeworkid, studentid.value).catch(() => {})
  }
  loadComments()
}

watch(
  () => [props.modelValue, props.submissionId],
  ([visible]) => {
    if (visible && props.submissionId) load()
    else stopPolling()
  },
  { immediate: true }
)

onBeforeUnmount(() => {
  disposed = true
  stopPolling()
})

const retry = async () => {
  await retryGrade(detail.value.id)
  ElMessage.success('已重新提交评分')
  detail.value.gradingStatus = 'PENDING'
  startPollingIfGrading()
}

const download = () =>
  window.open(`/ZhiYanAcademy/studenthomework/download/${detail.value.id}`, '_blank')

/* ---------- 答疑 ---------- */

const comments = ref([])
const commentsLoading = ref(false)
const question = ref('')
const submitting = ref(false)

const loadComments = async () => {
  const homeworkid = detail.value.homeworkid
  if (!homeworkid) return

  commentsLoading.value = true
  try {
    const res = await listHomeworkComments(homeworkid).catch(() => null)
    comments.value = (res?.data || []).map((c) => ({ ...c, replies: c.replies || [] }))
  } finally {
    commentsLoading.value = false
  }
}

const ask = async () => {
  const content = question.value.trim()
  if (!content) {
    ElMessage.warning('请输入问题内容')
    return
  }

  submitting.value = true
  try {
    await createHomeworkComment({
      homeworkid: detail.value.homeworkid,
      studentid: studentid.value,
      content,
      parentid: 0
    })
    ElMessage.success('提交成功')
    question.value = ''
    loadComments()
  } catch {
    // 失败提示由 http 拦截器统一处理
  } finally {
    submitting.value = false
  }
}

const removeComment = async (comment) => {
  const ok = await confirmDelete('确定删除该问题吗？')
  if (!ok) return

  await deleteHomeworkComment(comment.id)
  ElMessage.success('删除成功')
  loadComments()
}
</script>

<style scoped>
.head {
  display: flex;
  align-items: center;
  gap: var(--zy-space-3);
}

.head__title {
  margin: 0;
  font-size: var(--zy-font-size-lg);
  font-weight: var(--zy-font-weight-semibold);
  color: var(--zy-text-primary);
}

.head__meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--zy-space-4);
  margin: var(--zy-space-2) 0 var(--zy-space-4);
  font-size: var(--zy-font-size-sm);
  color: var(--zy-text-tertiary);
}

.answers {
  max-height: 420px;
  overflow-y: auto;
}

.document {
  margin-top: var(--zy-space-3);
}

.document__label {
  margin: 0 0 var(--zy-space-1);
  font-size: var(--zy-font-size-xs);
  color: var(--zy-text-tertiary);
}

.document__text {
  max-height: 240px;
  margin: 0;
  padding: var(--zy-space-3);
  overflow: auto;
  font-family: var(--zy-font-mono);
  font-size: var(--zy-font-size-sm);
  color: var(--zy-text-secondary);
  background: var(--zy-bg-sunken);
  border-radius: var(--zy-radius-md);
  white-space: pre-wrap;
}

/* ===== 评分 ===== */
.score {
  display: flex;
  gap: var(--zy-space-6);
}

.score__label {
  display: block;
  margin-bottom: var(--zy-space-1);
  font-size: var(--zy-font-size-xs);
  color: var(--zy-text-tertiary);
}

.score__value b {
  font-size: var(--zy-font-size-4xl);
  font-weight: var(--zy-font-weight-semibold);
  color: var(--zy-text-disabled);
  font-variant-numeric: tabular-nums;
}

.score__value b.is-graded {
  color: var(--zy-brand-base);
}

.score__comment {
  flex: 1;
  min-width: 0;
}

.score__text {
  margin: var(--zy-space-2) 0 0;
  font-family: inherit;
  font-size: var(--zy-font-size-sm);
  line-height: var(--zy-line-height-relaxed);
  color: var(--zy-text-secondary);
  white-space: pre-wrap;
}

/* ===== 答疑 ===== */
.comments {
  max-height: 320px;
  overflow-y: auto;
}

.comment {
  padding: var(--zy-space-3) 0;
  border-bottom: 1px solid var(--zy-border-subtle);
}

.comment:last-child {
  border-bottom: none;
}

.comment__head {
  display: flex;
  align-items: center;
  gap: var(--zy-space-2);
}

.comment__author {
  font-size: var(--zy-font-size-sm);
  font-weight: var(--zy-font-weight-semibold);
  color: var(--zy-text-primary);
}

.comment__time {
  margin-right: auto;
  font-size: var(--zy-font-size-xs);
  color: var(--zy-text-tertiary);
}

.comment__content {
  margin: var(--zy-space-1) 0 0;
  font-size: var(--zy-font-size-sm);
  line-height: var(--zy-line-height-relaxed);
  color: var(--zy-text-secondary);
}

.reply {
  display: flex;
  flex-wrap: wrap;
  gap: var(--zy-space-2);
  margin-top: var(--zy-space-2);
  padding: var(--zy-space-2) var(--zy-space-3);
  font-size: var(--zy-font-size-sm);
  color: var(--zy-text-secondary);
  background: var(--zy-brand-soft);
  border-radius: var(--zy-radius-md);
}

.reply__author {
  font-weight: var(--zy-font-weight-semibold);
  color: var(--zy-brand-base);
}

.reply__time {
  margin-left: auto;
  font-size: var(--zy-font-size-xs);
  color: var(--zy-text-tertiary);
}

.ask {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: var(--zy-space-2);
  margin-top: var(--zy-space-3);
}

@media screen and (max-width: 768px) {
  .score {
    flex-direction: column;
    gap: var(--zy-space-4);
  }
}
</style>
