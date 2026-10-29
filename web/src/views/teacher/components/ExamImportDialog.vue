<template>
  <el-dialog
    :model-value="modelValue"
    title="导入试题"
    width="760px"
    top="5vh"
    @update:model-value="$emit('update:modelValue', $event)"
    @closed="reset"
  >
    <!-- 步骤一：选择来源 -->
    <template v-if="!parsed.length">
      <el-tabs v-model="tab">
        <el-tab-pane label="粘贴文本" name="paste">
          <el-input
            v-model="pasteText"
            type="textarea"
            :rows="12"
            class="paste"
            :placeholder="PASTE_PLACEHOLDER"
          />
          <div class="actions">
            <el-button type="primary" :disabled="!pasteText.trim()" @click="parsePaste">
              识别试题
            </el-button>
            <span v-if="pasteLineCount" class="hint">已输入 {{ pasteLineCount }} 行</span>
          </div>
        </el-tab-pane>

        <el-tab-pane label="上传文件" name="file">
          <el-upload
            drag
            action=""
            :auto-upload="false"
            :on-change="onFileChange"
            :file-list="fileList"
            accept=".doc,.docx,.pdf"
            :limit="1"
          >
            <div class="el-upload__text">将 Word / PDF 拖到此处，或<em>点击上传</em></div>
            <template #tip>
              <div class="el-upload__tip">支持 .doc / .docx / .pdf</div>
            </template>
          </el-upload>

          <div class="actions">
            <el-button type="primary" :loading="parsing" :disabled="!file" @click="parseFile">
              {{ parsing ? '正在解析…' : '上传并解析试题' }}
            </el-button>
          </div>
        </el-tab-pane>

        <el-tab-pane label="AI 智能出题" name="ai">
          <el-form label-width="92px">
            <el-form-item label="出题主题">
              <el-input
                v-model="aiForm.topic"
                placeholder="如：Java 异常处理；留空则使用所选课程名"
              />
            </el-form-item>

            <el-form-item label="难度">
              <el-radio-group v-model="aiForm.difficulty">
                <el-radio-button v-for="d in DIFFICULTIES" :key="d" :value="d">{{ d }}</el-radio-button>
              </el-radio-group>
            </el-form-item>

            <el-form-item label="题型数量">
              <div class="counts">
                <label v-for="item in AI_TYPE_FIELDS" :key="item.field" class="counts__item">
                  <span>{{ item.label }}</span>
                  <el-input-number
                    v-model="aiForm[item.field]"
                    :min="0"
                    :max="AI_MAX_TOTAL"
                    size="small"
                    controls-position="right"
                  />
                </label>
              </div>
            </el-form-item>
          </el-form>

          <div class="actions">
            <el-button type="primary" :loading="aiLoading" @click="generateByAi">
              {{ aiLoading ? 'AI 正在出题…' : '开始智能出题' }}
            </el-button>
            <span class="hint">共 {{ aiTotal }} 道（单次不超过 {{ AI_MAX_TOTAL }} 道）</span>
          </div>

          <el-alert
            type="info"
            :closable="false"
            show-icon
            class="tip"
            title="AI 出题支持单选、多选、判断、填空与简答，生成后可在预览中修改；程序类题目请手动添加。"
          />
        </el-tab-pane>
      </el-tabs>

      <el-collapse class="guide">
        <el-collapse-item title="格式说明（点击展开）">
          <p>每道题按以下格式书写，系统会自动识别：</p>
          <pre>{{ FORMAT_SAMPLE }}</pre>
          <p class="guide__note">
            题号后也支持「、」「）」等符号，选项可以一行一个，也可以一行多个。
          </p>
        </el-collapse-item>
      </el-collapse>
    </template>

    <!-- 步骤二：预览确认 -->
    <template v-else>
      <div class="summary">
        <el-tag type="success" disable-transitions>解析成功</el-tag>
        <span>共识别 <b>{{ parsed.length }}</b> 道题目</span>
        <el-button link type="primary" @click="reset">重新导入</el-button>
      </div>

      <div class="preview">
        <article v-for="(q, index) in parsed" :key="q.id" class="question">
          <header class="question__head">
            <span class="question__no">{{ index + 1 }}.</span>
            <el-tag size="small" :type="questionTypeTone(q.type)" disable-transitions>
              {{ questionTypeShort(q.type) }}
            </el-tag>
            <span class="question__score">{{ q.score }} 分</span>
          </header>

          <p class="question__title">{{ q.title }}</p>

          <div v-if="q.options?.length" class="options">
            <div
              v-for="(option, oi) in q.options"
              :key="oi"
              :class="{ 'is-correct': isCorrectOption(q, oi) }"
            >
              {{ getOptionLabel(oi) }}. {{ option }}
            </div>
          </div>

          <p v-if="q.answer !== null && q.answer !== undefined" class="question__answer">
            答案：{{ formatAnswer(q) }}
          </p>
        </article>
      </div>
    </template>

    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button v-if="parsed.length" type="primary" @click="confirm">
        确认导入 {{ parsed.length }} 题
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
/**
 * 试题导入弹窗（教师端）
 *
 * 三种来源最终都走同一条「解析 → 归一 → 预览 → 确认」的流程，
 * 从考试管理页拆出后，主页面不必再关心解析细节。
 */
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { generateQuestionsByAi, parseQuestionsByAi } from '@/api/ai'
import { readAttachmentContent, uploadAttachment } from '@/api/homework'
import { getOptionLabel, parseQuestionText } from '@/utils/exam'
import { normalizeQuestionType, questionTypeShort, questionTypeTone } from '@/utils/questionTypes'

const AI_MAX_TOTAL = 20
const DEFAULT_SCORE = 5
const DIFFICULTIES = ['简单', '中等', '困难']

const AI_TYPE_FIELDS = [
  { field: 'singleCount', label: '单选' },
  { field: 'multiCount', label: '多选' },
  { field: 'judgeCount', label: '判断' },
  { field: 'blankCount', label: '填空' },
  { field: 'shortCount', label: '简答' }
]

const PASTE_PLACEHOLDER = `将试题文本粘贴到此处，系统会自动识别题目、选项和答案。

示例：
1. 以下哪个不是 Java 的基本数据类型？
A. int
B. String
C. boolean
D. double
答案：B`

const FORMAT_SAMPLE = `1. 以下哪个不是 Java 的基本数据类型？
A. int
B. String
C. boolean
D. double
答案：B

2. 以下哪些是面向对象的特征？（多选）
A. 封装  B. 继承  C. 编译  D. 多态
答案：ABD

3. Java 是一种面向对象的编程语言。（判断）
答案：正确

4. Java 中用于定义常量的关键字是____。
答案：final

5. 简述 Java 中抽象类和接口的区别。`

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** AI 出题在未填主题时用它兜底 */
  courseName: { type: String, default: '' }
})

const emit = defineEmits(['update:modelValue', 'imported'])

const tab = ref('paste')
const parsed = ref([])

const pasteText = ref('')
const file = ref(null)
const fileList = ref([])
const parsing = ref(false)

const aiLoading = ref(false)
const aiForm = reactive({
  topic: '',
  difficulty: '中等',
  singleCount: 5,
  multiCount: 2,
  judgeCount: 3,
  blankCount: 0,
  shortCount: 0
})

const aiTotal = computed(() =>
  AI_TYPE_FIELDS.reduce((sum, item) => sum + (Number(aiForm[item.field]) || 0), 0)
)

const pasteLineCount = computed(
  () => pasteText.value.trim().split('\n').filter((line) => line.trim()).length
)

/* ---------- 归一与预览 ---------- */

let idSeq = 0

/** 三种来源给出的字段名与完整度都不同，统一成编辑器可用的结构 */
const normalize = (question) => ({
  id: `q${Date.now()}_${(idSeq += 1)}`,
  type: normalizeQuestionType(question.type),
  title: String(question.title || question.stem || '').trim(),
  score: Number(question.score) || DEFAULT_SCORE,
  options: Array.isArray(question.options) ? question.options : [],
  answer: question.answer ?? null,
  gradingPolicy: question.gradingPolicy || 'normal'
})

const accept = (questions, message) => {
  parsed.value = questions.map(normalize)
  ElMessage.success(`${message} ${parsed.value.length} 道题目`)
}

const isCorrectOption = (question, index) => {
  if (question.type === 'single') return question.answer === index
  if (question.type === 'multi') return question.answer?.includes?.(index) ?? false
  return false
}

const formatAnswer = (question) => {
  if (question.type === 'single' && typeof question.answer === 'number') {
    return getOptionLabel(question.answer)
  }
  if (question.type === 'multi' && Array.isArray(question.answer)) {
    return question.answer.map(getOptionLabel).join('')
  }
  if (question.type === 'judge') return question.answer ? '正确' : '错误'
  return String(question.answer ?? '')
}

/* ---------- 三种来源 ---------- */

const parsePaste = () => {
  const result = parseQuestionText(pasteText.value.trim())
  if (!result.success || !result.questions?.length) {
    ElMessage.warning(result.message || '未能识别出试题，请检查格式')
    return
  }
  accept(result.questions, '成功识别')
}

const onFileChange = (uploaded) => {
  file.value = uploaded.raw
  fileList.value = [uploaded]
}

const parseFile = async () => {
  if (!file.value) return

  parsing.value = true
  try {
    const form = new FormData()
    form.append('file', file.value)

    const uploaded = await uploadAttachment(form)
    const path = uploaded?.data?.filepath || uploaded?.data
    if (!path) {
      ElMessage.error('上传失败，未获取到文件路径')
      return
    }

    const text = await readAttachmentContent(path)
    if (!text?.data) {
      ElMessage.error('无法提取文档内容，或文档为空')
      return
    }

    // 优先用 AI 解析，它对排版混乱的文档更宽容；不可用时退回本地正则
    let questions = []
    const ai = await parseQuestionsByAi(text.data).catch(() => null)
    if (ai?.data) {
      let payload = ai.data
      if (typeof payload === 'string') {
        try {
          payload = JSON.parse(payload)
        } catch {
          payload = null
        }
      }
      questions = payload?.questions || (Array.isArray(payload) ? payload : [])
    }

    if (!questions.length) {
      const local = parseQuestionText(text.data)
      if (local.success) questions = local.questions || []
    }

    if (!questions.length) {
      ElMessage.warning('未能识别出试题，请检查文档格式')
      return
    }

    accept(questions, '成功识别')
  } catch (e) {
    ElMessage.error(`解析失败：${e?.msg || e?.message || '请稍后重试'}`)
  } finally {
    parsing.value = false
  }
}

const generateByAi = async () => {
  const subject = aiForm.topic.trim() || props.courseName
  if (!subject) {
    ElMessage.warning('请填写出题主题，或先在上方选择所属课程')
    return
  }
  if (aiTotal.value <= 0) {
    ElMessage.warning('请至少设置一种题型的数量')
    return
  }
  if (aiTotal.value > AI_MAX_TOTAL) {
    ElMessage.warning(`单次出题数量不能超过 ${AI_MAX_TOTAL} 道`)
    return
  }

  aiLoading.value = true
  try {
    const res = await generateQuestionsByAi({ ...aiForm, subject })

    let payload = res?.data
    if (typeof payload === 'string') {
      // 模型偶尔会多转义一层，去掉后才能解析
      payload = payload.replace(/\\([^nrt"\\/bfu])/g, '$1')
      try {
        payload = JSON.parse(payload)
      } catch {
        payload = null
      }
    }

    const questions = payload?.questions
    if (!Array.isArray(questions) || !questions.length) {
      ElMessage.warning(payload?.message || 'AI 未能生成试题，请调整主题或重试')
      return
    }

    // 模型常把选项前缀一并写进内容，去掉以免和渲染出的序号重复
    questions.forEach((q) => {
      if (Array.isArray(q.options)) {
        q.options = q.options.map((option) =>
          typeof option === 'string' ? option.replace(/^[A-Da-d][.、．:：\s]\s*/, '').trim() : option
        )
      }
    })

    accept(questions, 'AI 已生成')
  } catch (e) {
    ElMessage.error(`AI 出题失败：${e?.msg || e?.message || '请稍后重试'}`)
  } finally {
    aiLoading.value = false
  }
}

/* ---------- 收尾 ---------- */

const reset = () => {
  parsed.value = []
  pasteText.value = ''
  file.value = null
  fileList.value = []
  parsing.value = false
  aiLoading.value = false
}

const confirm = () => {
  if (!parsed.value.length) return
  emit('imported', parsed.value)
  ElMessage.success(`已导入 ${parsed.value.length} 道题目`)
  emit('update:modelValue', false)
}
</script>

<style scoped>
.paste {
  font-family: var(--zy-font-mono);
}

.actions {
  display: flex;
  align-items: center;
  gap: var(--zy-space-3);
  margin-top: var(--zy-space-3);
}

.hint {
  font-size: var(--zy-font-size-xs);
  color: var(--zy-text-tertiary);
}

.tip {
  margin-top: var(--zy-space-3);
}

.counts {
  display: flex;
  flex-wrap: wrap;
  gap: var(--zy-space-3);
}

.counts__item {
  display: flex;
  align-items: center;
  gap: var(--zy-space-2);
  font-size: var(--zy-font-size-sm);
  color: var(--zy-text-secondary);
}

.guide {
  margin-top: var(--zy-space-4);
}

.guide pre {
  margin: var(--zy-space-2) 0;
  padding: var(--zy-space-3);
  font-family: var(--zy-font-mono);
  font-size: var(--zy-font-size-sm);
  line-height: var(--zy-line-height-relaxed);
  color: var(--zy-text-secondary);
  background: var(--zy-bg-sunken);
  border-radius: var(--zy-radius-md);
}

.guide__note {
  margin: 0;
  font-size: var(--zy-font-size-xs);
  color: var(--zy-text-tertiary);
}

/* ===== 预览 ===== */
.summary {
  display: flex;
  align-items: center;
  gap: var(--zy-space-3);
  margin-bottom: var(--zy-space-3);
  font-size: var(--zy-font-size-sm);
  color: var(--zy-text-secondary);
}

.preview {
  max-height: 420px;
  overflow-y: auto;
}

.question {
  padding: var(--zy-space-3);
  margin-bottom: var(--zy-space-2);
  border: 1px solid var(--zy-border-subtle);
  border-radius: var(--zy-radius-md);
}

.question__head {
  display: flex;
  align-items: center;
  gap: var(--zy-space-2);
}

.question__no {
  font-weight: var(--zy-font-weight-semibold);
  color: var(--zy-text-primary);
}

.question__score {
  margin-left: auto;
  font-size: var(--zy-font-size-xs);
  color: var(--zy-text-tertiary);
}

.question__title {
  margin: var(--zy-space-2) 0;
  font-size: var(--zy-font-size-sm);
  line-height: var(--zy-line-height-relaxed);
  color: var(--zy-text-primary);
}

.options {
  display: flex;
  flex-direction: column;
  gap: var(--zy-space-1);
  font-size: var(--zy-font-size-sm);
  color: var(--zy-text-secondary);
}

.options .is-correct {
  font-weight: var(--zy-font-weight-semibold);
  color: var(--zy-success-base);
}

.question__answer {
  margin: var(--zy-space-2) 0 0;
  font-size: var(--zy-font-size-sm);
  color: var(--zy-success-base);
}
</style>
