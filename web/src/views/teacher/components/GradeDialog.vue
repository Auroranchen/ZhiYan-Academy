<template>
  <el-dialog
    :model-value="modelValue"
    title="批改作业"
    width="760px"
    top="6vh"
    @update:model-value="$emit('update:modelValue', $event)"
    @opened="bindAiAnalysisButtons"
  >
    <header class="head">
      <h3 class="head__name">{{ detail.studentname }}</h3>
      <el-tag type="info" size="small" disable-transitions>{{ detail.studentno }}</el-tag>
    </header>

    <div class="head__meta">
      <span>{{ detail.homeworktitle }}</span>
      <span>提交于 {{ formatDate(detail.submittime, 'minute') }}</span>
    </div>

    <el-alert
      v-if="objectiveScored"
      type="success"
      :closable="false"
      :title="`客观题已自动评分：${detail.score} 分，简答题需你确认`"
      class="alert"
    />

    <el-alert
      v-else-if="aiGraded"
      type="info"
      :closable="false"
      title="该作业已由 AI 自动评分，可直接确认或修改评分结果"
      class="alert"
    />

    <SectionCard title="作业内容">
      <div v-if="detail.homeworktype === 1" class="answers" v-safe-html="answerHtml"></div>

      <template v-else>
        <el-button type="primary" @click="download">下载作业文件</el-button>
        <div v-if="detail.content" class="document">
          <p class="document__label">文档内容预览</p>
          <pre class="document__text">{{ detail.content }}</pre>
        </div>
      </template>
    </SectionCard>

    <SectionCard title="评分">
      <el-form :model="form" label-width="72px">
        <el-form-item label="成绩">
          <el-input-number v-model="form.score" :min="0" :max="100" size="large" />
          <span class="unit">分</span>
        </el-form-item>
        <el-form-item label="评语">
          <el-input
            v-model="form.comment"
            type="textarea"
            :rows="3"
            placeholder="给学生一些具体的建议和鼓励…"
          />
        </el-form-item>
      </el-form>
    </SectionCard>

    <SectionCard title="作业答疑" :count="comments.length">
      <div v-loading="commentsLoading" class="comments">
        <el-empty v-if="!comments.length" description="暂无提问" :image-size="80" />

        <article v-for="comment in comments" :key="comment.id" class="comment">
          <header class="comment__head">
            <span class="comment__author">{{ comment.studentname || '学生' }}</span>
            <span class="comment__time">{{ formatDate(comment.createtime, 'minute') }}</span>
            <el-button link type="danger" @click="removeComment(comment)">删除</el-button>
          </header>

          <p class="comment__content">{{ comment.content }}</p>

          <div v-for="reply in comment.replies || []" :key="reply.id" class="reply">
            <span class="reply__author">{{ reply.teachername || '教师' }}：</span>
            <span>{{ reply.content }}</span>
            <span class="reply__time">{{ formatDate(reply.createtime, 'minute') }}</span>
            <el-button link type="danger" @click="removeComment(reply)">删除</el-button>
          </div>

          <div class="reply-box">
            <el-input
              v-model="replies[comment.id]"
              type="textarea"
              :rows="2"
              maxlength="500"
              show-word-limit
              placeholder="回复学生问题…"
            />
            <el-button type="primary" :loading="replying" @click="reply(comment)">回复</el-button>
          </div>
        </article>
      </div>
    </SectionCard>

    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">提交批改</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
/**
 * 作业批改弹窗（教师端）
 *
 * 从批改列表页拆出：作业详情、评分表单、答疑回复三块各有自己的数据与请求，
 * 和列表的筛选分页放在同一个组件里会让两边都难以修改。
 */
import { computed, reactive, ref, watch } from 'vue'
import { useStore } from 'vuex'
import { ElMessage } from 'element-plus'
import { SectionCard } from '@/components/ui'
import {
  createHomeworkComment,
  deleteHomeworkComment,
  getStudentHomework,
  gradeStudentHomework,
  listHomeworkComments
} from '@/api/homework'
import { mergeStudentAnswers, renderExamResultHtml } from '@/utils/exam'
import { bindAiAnalysisButtons } from '@/utils/aiAnalysis'
import { confirmDelete } from '@/utils/confirm'
import { formatDate } from '@/utils/format'

const ANSWER_LABELS = {
  answerLabel: '学生答案',
  correctAnswerLabel: '正确答案',
  trueLabel: '正确',
  falseLabel: '错误',
  emptyLabel: '未作答'
}

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  submissionId: { type: [Number, String], default: null }
})

const emit = defineEmits(['update:modelValue', 'graded'])

const store = useStore()

const detail = ref({})
const form = reactive({ id: null, score: 0, comment: '' })
const saving = ref(false)

const aiGraded = computed(() => detail.value.comment?.startsWith('[AI自动评分]'))

/** status 为 1 表示已提交未定稿，此时的分数是客观题自动判出来的 */
const objectiveScored = computed(
  () => detail.value.homeworktype === 1 && detail.value.status === 1 && detail.value.score != null
)

const answerHtml = computed(() => {
  if (detail.value.homeworktype !== 1) return detail.value.content || ''

  const merged = mergeStudentAnswers(detail.value.content, detail.value.homeworkcontent)
  if (!merged) return detail.value.content || ''

  return renderExamResultHtml(merged.questions, merged.answers, null, {
    ...ANSWER_LABELS,
    showAnalysisButton: true
  })
})

const load = async () => {
  const res = await getStudentHomework(props.submissionId).catch(() => null)
  if (!res?.data) return

  detail.value = res.data
  Object.assign(form, {
    id: res.data.id,
    score: res.data.score ?? 0,
    comment: res.data.comment || ''
  })
  loadComments()
}

watch(
  () => [props.modelValue, props.submissionId],
  ([visible]) => {
    if (visible && props.submissionId) load()
  },
  { immediate: true }
)

const download = () =>
  window.open(`/ZhiYanAcademy/studenthomework/download/${detail.value.id}`, '_blank')

const submit = async () => {
  saving.value = true
  try {
    await gradeStudentHomework({ ...form })
    ElMessage.success('批改成功')
    emit('update:modelValue', false)
    emit('graded')
  } catch {
    // 失败提示由 http 拦截器统一处理
  } finally {
    saving.value = false
  }
}

/* ---------- 答疑 ---------- */

const comments = ref([])
const commentsLoading = ref(false)
const replies = reactive({})
const replying = ref(false)

const loadComments = async () => {
  const homeworkid = detail.value.homeworkid
  if (!homeworkid) return

  commentsLoading.value = true
  try {
    const res = await listHomeworkComments(homeworkid).catch(() => null)
    comments.value = (res?.data || []).map((c) => ({ ...c, replies: c.replies || [] }))
    comments.value.forEach((c) => {
      if (replies[c.id] === undefined) replies[c.id] = ''
    })
  } finally {
    commentsLoading.value = false
  }
}

const reply = async (comment) => {
  const content = String(replies[comment.id] || '').trim()
  if (!content) {
    ElMessage.warning('请输入回复内容')
    return
  }

  replying.value = true
  try {
    await createHomeworkComment({
      homeworkid: detail.value.homeworkid,
      teacherid: store.state.user?.teacherid,
      parentid: comment.id,
      content
    })
    ElMessage.success('回复成功')
    replies[comment.id] = ''
    loadComments()
  } catch {
    // 失败提示由 http 拦截器统一处理
  } finally {
    replying.value = false
  }
}

const removeComment = async (comment) => {
  const ok = await confirmDelete('确定删除该答疑内容吗？')
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
  gap: var(--zy-space-2);
}

.head__name {
  margin: 0;
  font-size: var(--zy-font-size-lg);
  font-weight: var(--zy-font-weight-semibold);
  color: var(--zy-text-primary);
}

.head__meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--zy-space-4);
  margin: var(--zy-space-2) 0 var(--zy-space-3);
  font-size: var(--zy-font-size-sm);
  color: var(--zy-text-tertiary);
}

.alert {
  margin-bottom: var(--zy-space-3);
}

.unit {
  margin-left: var(--zy-space-2);
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

/* ===== 答疑 ===== */
.comments {
  max-height: 340px;
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
  align-items: center;
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

.reply-box {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: var(--zy-space-2);
  margin-top: var(--zy-space-2);
}
</style>
