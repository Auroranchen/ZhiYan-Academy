<template>
  <div class="editor">
    <div class="editor__toolbar">
      <el-button size="small" @click="$emit('ai-generate')">AI 出题</el-button>
      <el-button size="small" @click="$emit('ppt-generate')">PPT 出题</el-button>
      <el-button size="small" @click="$emit('paste-import')">复制试题</el-button>
      <el-button type="primary" size="small" @click="addQuestion">添加试题</el-button>
    </div>

    <p v-if="!questions.length" class="editor__empty">还没有试题，点击「添加试题」或用 AI 出题</p>

    <div v-for="(question, index) in questions" :key="question.id" class="question">
      <header class="question__head">
        <span class="question__index">第 {{ index + 1 }} 题</span>
        <el-button link type="danger" @click="removeQuestion(index)">删除</el-button>
      </header>

      <div class="question__type">
        <el-select
          v-model="question.type"
          placeholder="题型"
          class="question__select"
          @change="resetForType(question)"
        >
          <el-option v-for="t in QUESTION_TYPES" :key="t.value" :label="t.label" :value="t.value" />
        </el-select>

        <el-select
          v-if="NEEDS_POLICY.includes(question.type)"
          v-model="question.gradingPolicy"
          placeholder="判分宽严"
          class="question__select"
        >
          <el-option v-for="p in GRADING_POLICIES" :key="p.value" :label="p.label" :value="p.value" />
        </el-select>
      </div>

      <el-input
        v-model="question.title"
        type="textarea"
        :rows="2"
        placeholder="请输入题干"
        class="question__title"
      />

      <div v-if="isChoice(question)" class="options">
        <div v-for="(_, optionIndex) in question.options" :key="optionIndex" class="options__row">
          <span class="options__label">{{ getOptionLabel(optionIndex) }}</span>
          <el-input v-model="question.options[optionIndex]" placeholder="选项内容" />
          <el-button link type="danger" @click="removeOption(question, optionIndex)">删除</el-button>
        </div>
        <el-button link type="primary" @click="addOption(question)">添加选项</el-button>
      </div>

      <div class="answer">
        <span class="answer__label">{{ ANSWER_LABEL[question.type] || '正确答案' }}</span>

        <el-radio-group v-if="question.type === 'single'" v-model="question.answer">
          <el-radio v-for="(_, i) in question.options" :key="i" :value="i">
            {{ getOptionLabel(i) }}
          </el-radio>
        </el-radio-group>

        <el-checkbox-group v-else-if="question.type === 'multi'" v-model="question.answer">
          <el-checkbox v-for="(_, i) in question.options" :key="i" :value="i">
            {{ getOptionLabel(i) }}
          </el-checkbox>
        </el-checkbox-group>

        <el-radio-group v-else-if="question.type === 'judge'" v-model="question.answer">
          <el-radio :value="true">正确</el-radio>
          <el-radio :value="false">错误</el-radio>
        </el-radio-group>

        <el-input
          v-else-if="question.type === 'short'"
          v-model="question.answer"
          type="textarea"
          :rows="3"
          placeholder="参考答案用于 AI 辅助批阅"
        />

        <el-input v-else v-model="question.answer" placeholder="请输入正确答案" />
      </div>
    </div>
  </div>
</template>

<script setup>
/**
 * 在线作业的试题编辑器。
 *
 * 从 TeacherHomework 拆出：题型切换、选项增删、答案下标维护这套逻辑
 * 与作业本身的增删改查无关，混在一起会让主页面难以阅读。
 */
import { computed } from 'vue'
import { createQuestion, resetAnswerForType } from '@/utils/questionEditor'
import { getOptionLabel } from '@/utils/exam'

const QUESTION_TYPES = [
  { value: 'single', label: '单选题' },
  { value: 'multi', label: '多选题' },
  { value: 'judge', label: '判断题' },
  { value: 'blank', label: '填空题' },
  { value: 'short', label: '简答题' }
]

const GRADING_POLICIES = [
  { value: 'strict', label: '严格' },
  { value: 'normal', label: '一般' },
  { value: 'lenient', label: '宽松' }
]

/** 主观题需要指定判分宽严 */
const NEEDS_POLICY = ['blank', 'short']

const ANSWER_LABEL = { short: '参考答案（AI 批阅参考）' }

const props = defineProps({
  modelValue: { type: Array, required: true }
})

const emit = defineEmits(['update:modelValue', 'ai-generate', 'ppt-generate', 'paste-import'])

// 增删题目会改变数组本身，必须通过 v-model 交回父组件；
// 单题内部字段仍由 v-model 直接改对象，属于常规写法
const questions = computed(() => props.modelValue)

const isChoice = (question) => question.type === 'single' || question.type === 'multi'

const addQuestion = () => emit('update:modelValue', [...props.modelValue, createQuestion()])

const removeQuestion = (index) =>
  emit('update:modelValue', props.modelValue.filter((_, i) => i !== index))

const addOption = (question) => {
  if (!Array.isArray(question.options)) question.options = []
  question.options.push('')
}

/** 删掉选项后，答案里记录的是下标，必须跟着前移 */
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

const resetForType = (question) => resetAnswerForType(question)
</script>

<style scoped>
.editor {
  width: 100%;
}

.editor__toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: var(--zy-space-2);
  margin-bottom: var(--zy-space-3);
}

.editor__empty {
  margin: 0;
  padding: var(--zy-space-6) 0;
  text-align: center;
  font-size: var(--zy-font-size-sm);
  color: var(--zy-text-tertiary);
}

.question {
  padding: var(--zy-space-4);
  margin-bottom: var(--zy-space-3);
  background: var(--zy-bg-sunken);
  border-radius: var(--zy-radius-md);
}

.question__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--zy-space-2);
}

.question__index {
  font-size: var(--zy-font-size-sm);
  font-weight: var(--zy-font-weight-semibold);
  color: var(--zy-text-primary);
}

.question__type {
  display: flex;
  gap: var(--zy-space-2);
  margin-bottom: var(--zy-space-2);
}

.question__select {
  width: 160px;
}

.question__title {
  margin-bottom: var(--zy-space-2);
}

.options {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--zy-space-2);
  margin-bottom: var(--zy-space-3);
}

.options__row {
  display: flex;
  align-items: center;
  gap: var(--zy-space-2);
  width: 100%;
}

.options__label {
  width: 18px;
  font-size: var(--zy-font-size-sm);
  font-weight: var(--zy-font-weight-semibold);
  color: var(--zy-text-tertiary);
}

.answer {
  display: flex;
  flex-direction: column;
  gap: var(--zy-space-1);
}

.answer__label {
  font-size: var(--zy-font-size-xs);
  color: var(--zy-text-tertiary);
}

@media screen and (max-width: 768px) {
  .question__type {
    flex-direction: column;
  }

  .question__select {
    width: 100%;
  }
}
</style>
