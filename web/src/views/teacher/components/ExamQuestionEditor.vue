<!--
  试卷题目编辑器

  与作业的 QuestionEditor 分开：考试要给每题单独配分、支持上下调序，
  还多出程序填空、程序题、代码解释三种代码类题型，硬塞进一个组件会让两边都变复杂。
-->
<template>
  <div class="editor">
    <div v-for="(question, index) in questions" :key="question.id || index" class="question">
      <header class="question__head">
        <span class="question__index">第 {{ index + 1 }} 题</span>

        <el-select
          v-model="question.type"
          size="small"
          class="question__type"
          @change="resetForType(question)"
        >
          <el-option v-for="t in QUESTION_TYPES" :key="t.value" :label="t.label" :value="t.value" />
        </el-select>

        <el-input-number
          v-model="question.score"
          :min="0"
          :max="100"
          size="small"
          controls-position="right"
          class="question__score"
        />
        <span class="muted">分</span>

        <span class="question__reorder">
          <el-button size="small" text :icon="Top" :disabled="index === 0" @click="move(index, -1)" />
          <el-button
            size="small"
            text
            :icon="Bottom"
            :disabled="index === questions.length - 1"
            @click="move(index, 1)"
          />
        </span>

        <el-button size="small" text type="danger" @click="remove(index)">删除题目</el-button>
      </header>

      <el-input
        v-model="question.title"
        type="textarea"
        :rows="2"
        placeholder="请输入题干"
        class="question__title"
      />

      <div v-if="isChoice(question)" class="options">
        <div v-for="(_, oi) in question.options" :key="oi" class="options__row">
          <span class="options__letter">{{ getOptionLabel(oi) }}</span>
          <el-input v-model="question.options[oi]" size="small" placeholder="选项内容" />
          <el-radio
            v-if="question.type === 'single'"
            :model-value="question.answer"
            :value="oi"
            @update:model-value="question.answer = oi"
          >
            正确
          </el-radio>
          <el-checkbox
            v-else
            :model-value="Array.isArray(question.answer) && question.answer.includes(oi)"
            @change="toggleMulti(question, oi)"
          >
            正确
          </el-checkbox>
          <el-button size="small" text type="danger" @click="removeOption(question, oi)">删</el-button>
        </div>
        <el-button size="small" text type="primary" @click="addOption(question)">+ 添加选项</el-button>
      </div>

      <div v-else-if="question.type === 'judge'">
        <el-radio-group v-model="question.answer">
          <el-radio :value="true">正确</el-radio>
          <el-radio :value="false">错误</el-radio>
        </el-radio-group>
      </div>

      <div v-else-if="question.type === 'blank'" class="blank">
        <el-input v-model="question.answer" size="small" placeholder="标准答案" class="blank__answer" />
        <el-select v-model="question.gradingPolicy" size="small" class="blank__policy">
          <el-option v-for="p in GRADING_POLICIES" :key="p.value" :label="p.label" :value="p.value" />
        </el-select>
      </div>

      <div v-else-if="question.type === 'prog_blank'">
        <el-input
          v-model="question.code"
          type="textarea"
          :rows="6"
          placeholder="程序代码（在需要填空的位置用 ① ② 或 ___ 等标注，按顺序对应下方各空）"
          class="code-area"
        />
        <div class="blanks">
          <div v-for="(_, ai) in question.answer" :key="ai" class="blanks__row">
            <span class="blanks__label">空 {{ ai + 1 }}</span>
            <el-input v-model="question.answer[ai]" size="small" placeholder="该空的标准答案" />
            <el-button
              size="small"
              text
              type="danger"
              :disabled="question.answer.length <= 1"
              @click="question.answer.splice(ai, 1)"
            >
              删
            </el-button>
          </div>
          <div class="blanks__actions">
            <el-button size="small" text type="primary" @click="question.answer.push('')">
              + 添加空
            </el-button>
            <span class="muted">判分策略</span>
            <el-select v-model="question.gradingPolicy" size="small" class="blank__policy">
              <el-option v-for="p in GRADING_POLICIES" :key="p.value" :label="p.label" :value="p.value" />
            </el-select>
          </div>
        </div>
      </div>

      <div v-else-if="question.type === 'prog'">
        <el-input
          v-model="question.code"
          type="textarea"
          :rows="4"
          placeholder="初始代码 / 代码框架（选填，学生作答时可见）"
          class="code-area"
        />
        <el-input
          v-model="question.answer"
          type="textarea"
          :rows="5"
          placeholder="参考答案代码（程序题需教师评阅）"
          class="code-area code-area--stacked"
        />
      </div>

      <div v-else-if="question.type === 'code_explain'">
        <el-input
          v-model="question.code"
          type="textarea"
          :rows="6"
          placeholder="待解释的代码（必填，学生作答时可见）"
          class="code-area"
        />
        <el-input
          v-model="question.answer"
          type="textarea"
          :rows="3"
          placeholder="参考解释（需教师评阅）"
          class="code-area--stacked"
        />
      </div>

      <div v-else>
        <el-input
          v-model="question.answer"
          type="textarea"
          :rows="2"
          size="small"
          placeholder="参考答案（简答题需教师评阅）"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { Bottom, Top } from '@element-plus/icons-vue'
import { getOptionLabel } from '@/utils/exam'
import { createExamQuestion, resetExamAnswerForType } from '@/utils/examQuestion'

const QUESTION_TYPES = [
  { value: 'single', label: '单选题' },
  { value: 'multi', label: '多选题' },
  { value: 'judge', label: '判断题' },
  { value: 'blank', label: '填空题' },
  { value: 'short', label: '简答题' },
  { value: 'prog_blank', label: '程序填空题' },
  { value: 'prog', label: '程序题' },
  { value: 'code_explain', label: '代码解释题' }
]

const GRADING_POLICIES = [
  { value: 'strict', label: '严格匹配' },
  { value: 'normal', label: '常规匹配' },
  { value: 'loose', label: '宽松匹配' }
]

const props = defineProps({
  modelValue: { type: Array, required: true }
})

const emit = defineEmits(['update:modelValue'])

// 增删与调序会改变数组本身，交回父组件；单题字段仍由 v-model 直接改对象
const questions = computed(() => props.modelValue)

const isChoice = (question) => question.type === 'single' || question.type === 'multi'

const add = () => emit('update:modelValue', [...props.modelValue, createExamQuestion()])

const remove = (index) =>
  emit('update:modelValue', props.modelValue.filter((_, i) => i !== index))

const move = (index, offset) => {
  const target = index + offset
  if (target < 0 || target >= props.modelValue.length) return
  const next = [...props.modelValue]
  ;[next[index], next[target]] = [next[target], next[index]]
  emit('update:modelValue', next)
}

const addOption = (question) => {
  if (!Array.isArray(question.options)) question.options = []
  question.options.push('')
}

/** 删掉选项后，答案里记的是下标，必须跟着前移 */
const removeOption = (question, index) => {
  question.options.splice(index, 1)

  if (question.type === 'single' && typeof question.answer === 'number') {
    if (question.answer === index) question.answer = null
    else if (question.answer > index) question.answer -= 1
  }

  if (question.type === 'multi' && Array.isArray(question.answer)) {
    question.answer = question.answer
      .filter((item) => item !== index)
      .map((item) => (item > index ? item - 1 : item))
  }
}

const toggleMulti = (question, index) => {
  if (!Array.isArray(question.answer)) question.answer = []
  const at = question.answer.indexOf(index)
  if (at >= 0) question.answer.splice(at, 1)
  else question.answer.push(index)
  question.answer.sort((a, b) => a - b)
}

const resetForType = (question) => resetExamAnswerForType(question)

defineExpose({ add })
</script>

<style scoped>
.editor {
  width: 100%;
}

.muted {
  color: var(--zy-text-tertiary);
  font-size: var(--zy-font-size-sm);
}

.question {
  border: 1px solid var(--zy-border-default);
  border-radius: var(--zy-radius-md);
  padding: var(--zy-space-3);
  margin-bottom: var(--zy-space-3);
  background: var(--zy-bg-sunken);
}

.question__head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--zy-space-2);
  margin-bottom: var(--zy-space-2);
}

.question__index {
  font-weight: var(--zy-font-weight-semibold);
  color: var(--zy-brand-base);
}

.question__type {
  width: 130px;
}

.question__score {
  width: 110px;
}

.question__reorder {
  display: inline-flex;
}

.question__title {
  margin-bottom: var(--zy-space-2);
}

.options {
  display: flex;
  flex-direction: column;
  gap: var(--zy-space-2);
}

.options__row {
  display: flex;
  align-items: center;
  gap: var(--zy-space-2);
}

.options__letter {
  width: 20px;
  font-weight: var(--zy-font-weight-semibold);
  color: var(--zy-text-secondary);
}

.blank {
  display: flex;
  align-items: center;
  gap: var(--zy-space-2);
}

.blank__answer {
  width: 50%;
}

.blank__policy {
  width: 140px;
}

.blanks {
  margin-top: var(--zy-space-3);
  display: flex;
  flex-direction: column;
  gap: var(--zy-space-2);
}

.blanks__row {
  display: flex;
  align-items: center;
  gap: var(--zy-space-2);
}

.blanks__label {
  width: 44px;
  flex-shrink: 0;
  font-size: var(--zy-font-size-sm);
  font-weight: var(--zy-font-weight-semibold);
  color: var(--zy-brand-base);
}

.blanks__actions {
  display: flex;
  align-items: center;
  gap: var(--zy-space-2);
}

/* 代码类题型用深色编辑区，和普通文本框区分开 */
.code-area :deep(.el-textarea__inner) {
  font-family: var(--zy-font-mono);
  font-size: var(--zy-font-size-sm);
  line-height: var(--zy-line-height-relaxed);
  background: var(--zy-code-bg);
  color: var(--zy-code-text);
}

.code-area :deep(.el-textarea__inner)::placeholder {
  color: var(--zy-code-placeholder);
}

.code-area--stacked {
  margin-top: var(--zy-space-2);
}
</style>
