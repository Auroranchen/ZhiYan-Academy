<!--
  AI 出题对话框

  文本出题与 PPT 出题除了「出题依据」不同（一个是课程标题，一个是上传的课件），
  难度、改分标准、各题型配额和总数校验完全一致，原先在 TeacherHomework.vue 里
  各写了一份九十来行的模板。差异部分交给 source 插槽，其余在这里统一。

  配额由本组件自己持有，每次打开重置为默认值，确认时随 confirm 事件交给父组件。
-->
<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    width="520px"
    :destroy-on-close="true"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-form :model="form" label-width="100px">
      <el-form-item :label="sourceLabel">
        <slot name="source" />
      </el-form-item>
      <el-form-item label="难度等级">
        <el-radio-group v-model="form.difficulty">
          <el-radio value="简单">简单</el-radio>
          <el-radio value="中等">中等</el-radio>
          <el-radio value="困难">困难</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="改分标准">
        <el-select v-model="form.scoringStandard" placeholder="选择改分标准">
          <el-option label="严格" value="严格" />
          <el-option label="一般" value="一般" />
          <el-option label="宽松" value="宽松" />
        </el-select>
      </el-form-item>
      <el-form-item v-for="item in QUOTA_FIELDS" :key="item.key" :label="item.label">
        <el-input-number v-model="form[item.key]" :min="0" :max="item.max" />
      </el-form-item>
      <el-form-item>
        <div class="generate-tip">
          <span>总计：{{ totalCount }} 道题</span>
          <span v-if="overLimit" class="generate-tip__over">（单次最多 {{ maxTotal }} 道）</span>
        </div>
      </el-form-item>
    </el-form>

    <div v-if="loading" class="generate-loading">
      <el-icon class="is-loading"><Loading /></el-icon>
      <span>{{ loadingText }}</span>
    </div>

    <div
      v-if="result && result.message"
      :class="['generate-result', result.success ? 'is-success' : 'is-error']"
    >
      {{ result.message }}
    </div>

    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="loading" :disabled="!canSubmit" @click="submit">
        开始出题
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, watch } from 'vue'
import { Loading } from '@element-plus/icons-vue'

/** 各题型的配额上限：简答题最费批改时间，单独收紧 */
const QUOTA_FIELDS = [
  { key: 'singleCount', label: '单选题数量', max: 10 },
  { key: 'multiCount', label: '多选题数量', max: 10 },
  { key: 'judgeCount', label: '判断题数量', max: 10 },
  { key: 'blankCount', label: '填空题数量', max: 10 },
  { key: 'shortCount', label: '简答题数量', max: 5 }
]

const DEFAULTS = {
  difficulty: '中等',
  scoringStandard: '一般',
  singleCount: 3,
  multiCount: 2,
  judgeCount: 2,
  blankCount: 2,
  shortCount: 0
}

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, required: true },
  sourceLabel: { type: String, default: '出题依据' },
  loading: { type: Boolean, default: false },
  loadingText: { type: String, default: 'AI 正在出题中，请稍候...' },
  /** 形如 { success, message }，为空则不展示结果区 */
  result: { type: Object, default: null },
  /** 父组件的额外前置条件，如「必须先选课程」「必须先传课件」 */
  ready: { type: Boolean, default: true },
  maxTotal: { type: Number, default: 20 }
})

const emit = defineEmits(['update:modelValue', 'confirm'])

const form = reactive({ ...DEFAULTS })

// 每次打开都回到默认配额，避免上一次的设置残留误导教师
watch(
  () => props.modelValue,
  (visible) => {
    if (visible) Object.assign(form, DEFAULTS)
  }
)

const totalCount = computed(() =>
  QUOTA_FIELDS.reduce((sum, item) => sum + (Number(form[item.key]) || 0), 0)
)

const overLimit = computed(() => totalCount.value > props.maxTotal)

const canSubmit = computed(() => props.ready && totalCount.value > 0 && !overLimit.value)

const submit = () => emit('confirm', { ...form })
</script>

<style scoped>
.generate-tip {
  color: var(--zy-text-secondary);
  font-size: var(--zy-font-size-sm);
}

.generate-tip__over {
  color: var(--zy-danger-base);
  margin-left: var(--zy-space-2);
}

.generate-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--zy-space-2);
  padding: var(--zy-space-5);
  color: var(--zy-brand-base);
}

.generate-result {
  padding: var(--zy-space-3);
  border-radius: var(--zy-radius-sm);
  font-size: var(--zy-font-size-sm);
  margin-top: var(--zy-space-3);
}

.generate-result.is-success {
  background: var(--zy-success-soft);
  color: var(--zy-success-base);
}

.generate-result.is-error {
  background: var(--zy-danger-soft);
  color: var(--zy-danger-base);
}
</style>
