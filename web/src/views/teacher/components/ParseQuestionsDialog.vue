<!--
  复制试题：粘贴文本让 AI 识别成结构化题目

  从 TeacherHomework 拆出：格式说明、识别结果预览这一整套只服务于这个入口，
  识别与清洗逻辑统一走 utils/aiQuestions。
-->
<template>
  <el-dialog
    :model-value="modelValue"
    title="复制试题"
    width="700px"
    :destroy-on-close="true"
    @update:model-value="$emit('update:modelValue', $event)"
    @open="reset"
  >
    <div class="parse">
      <div class="tips">
        <p class="tips__title">格式说明</p>
        <ul class="tips__list">
          <li>题目以数字开头，如 1. / 1、/ (1)</li>
          <li>选项以字母开头，如 A. / A、</li>
          <li>答案可写在题干末尾的括号里，如（A），或单独一行「答案：A」</li>
        </ul>
      </div>

      <el-input v-model="text" type="textarea" :rows="10" placeholder="请粘贴试题内容..." />

      <div class="parse__actions">
        <el-button type="primary" :loading="loading" :disabled="!text.trim()" @click="run">
          AI 智能识别
        </el-button>
        <el-button :disabled="!text" @click="text = ''">清空</el-button>
      </div>

      <div v-if="result.message" :class="['parse__result', result.success ? 'is-success' : 'is-error']">
        {{ result.message }}
      </div>

      <div v-if="result.questions.length" class="preview">
        <p class="preview__title">识别结果预览</p>
        <article v-for="(q, index) in result.questions" :key="q.id || index" class="preview__item">
          <header class="preview__head">
            <span class="preview__index">第 {{ index + 1 }} 题</span>
            <el-tag size="small" :type="questionTypeTone(q.type)" disable-transitions>
              {{ questionTypeShort(q.type) }}
            </el-tag>
          </header>
          <p class="preview__stem">{{ q.title }}</p>
          <ul v-if="q.options && q.options.length" class="preview__options">
            <li v-for="(option, oi) in q.options" :key="oi">{{ getOptionLabel(oi) }}. {{ option }}</li>
          </ul>
          <p v-if="q.answer !== null && q.answer !== undefined" class="preview__answer">
            答案：{{ previewAnswer(q) }}
          </p>
        </article>
      </div>
    </div>

    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :disabled="!result.questions.length" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { parseQuestionsByAi } from '@/api/ai'
import { buildAiQuestions } from '@/utils/aiQuestions'
import { getOptionLabel } from '@/utils/exam'
import { questionTypeShort, questionTypeTone } from '@/utils/questionTypes'

defineProps({
  modelValue: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'imported'])

const text = ref('')
const loading = ref(false)
const result = reactive({ success: false, questions: [], message: '' })

const reset = () => {
  text.value = ''
  result.success = false
  result.questions = []
  result.message = ''
}

const run = async () => {
  loading.value = true
  result.success = false
  result.questions = []
  result.message = '正在识别中...'

  try {
    const res = await parseQuestionsByAi(text.value)
    const built = buildAiQuestions(res?.data, { tidyTitle: true })
    result.success = built.success
    result.questions = built.questions
    result.message = built.message
  } catch (e) {
    console.error('AI 识别失败:', e)
    result.message = `AI 识别失败：${e?.message || '请稍后重试'}`
  } finally {
    loading.value = false
  }
}

const save = () => {
  emit('imported', result.questions)
  ElMessage.success(`已添加 ${result.questions.length} 道试题`)
  emit('update:modelValue', false)
}

/** 选项下标要还原成字母，教师才对得上原题 */
const previewAnswer = (question) => {
  if (question.type === 'single' && typeof question.answer === 'number') {
    return getOptionLabel(question.answer)
  }
  if (question.type === 'multi' && Array.isArray(question.answer)) {
    return question.answer.map(getOptionLabel).join('、')
  }
  if (question.type === 'judge') return question.answer ? '正确' : '错误'
  return question.answer || '-'
}
</script>

<style scoped>
.parse {
  display: flex;
  flex-direction: column;
  gap: var(--zy-space-3);
}

.tips {
  background: var(--zy-bg-sunken);
  padding: var(--zy-space-3);
  border-radius: var(--zy-radius-sm);
  font-size: var(--zy-font-size-sm);
}

.tips__title {
  margin: 0 0 var(--zy-space-2);
  font-weight: var(--zy-font-weight-semibold);
  color: var(--zy-text-primary);
}

.tips__list {
  margin: 0;
  padding-left: var(--zy-space-5);
  color: var(--zy-text-secondary);
  line-height: var(--zy-line-height-relaxed);
}

.parse__actions {
  display: flex;
  gap: var(--zy-space-2);
}

.parse__result {
  padding: var(--zy-space-3);
  border-radius: var(--zy-radius-sm);
  font-size: var(--zy-font-size-base);
}

.parse__result.is-success {
  background: var(--zy-success-soft);
  color: var(--zy-success-base);
}

.parse__result.is-error {
  background: var(--zy-danger-soft);
  color: var(--zy-danger-base);
}

.preview {
  border: 1px solid var(--zy-border-subtle);
  border-radius: var(--zy-radius-sm);
  padding: var(--zy-space-3);
  max-height: 300px;
  overflow-y: auto;
}

.preview__title {
  margin: 0 0 var(--zy-space-3);
  font-weight: var(--zy-font-weight-semibold);
  color: var(--zy-text-primary);
}

.preview__item {
  border-bottom: 1px dashed var(--zy-border-subtle);
  padding: var(--zy-space-3) 0;
}

.preview__item:last-child {
  border-bottom: none;
}

.preview__head {
  display: flex;
  align-items: center;
  gap: var(--zy-space-2);
  margin-bottom: var(--zy-space-2);
}

.preview__index {
  font-weight: var(--zy-font-weight-semibold);
  color: var(--zy-brand-base);
}

.preview__stem {
  margin: 0 0 var(--zy-space-2);
  color: var(--zy-text-primary);
}

.preview__options {
  margin: 0;
  padding-left: var(--zy-space-5);
  list-style: none;
  color: var(--zy-text-secondary);
  font-size: var(--zy-font-size-sm);
  line-height: var(--zy-line-height-relaxed);
}

.preview__answer {
  margin: var(--zy-space-2) 0 0;
  color: var(--zy-success-base);
  font-size: var(--zy-font-size-sm);
}
</style>
