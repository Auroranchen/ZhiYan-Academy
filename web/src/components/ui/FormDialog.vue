<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    :width="width"
    :top="top"
    :close-on-click-modal="false"
    destroy-on-close
    append-to-body
    @update:model-value="emit('update:modelValue', $event)"
    @closed="handleClosed"
  >
    <el-form
      ref="formRef"
      :model="model"
      :rules="rules"
      :label-width="labelWidth"
      :label-position="labelPosition"
      :disabled="loading"
      class="zy-form"
      :style="gridStyle"
      @submit.prevent="handleSubmit"
    >
      <slot />
    </el-form>

    <template #footer>
      <div class="zy-form__footer">
        <slot name="footer-extra" />
        <el-button @click="close">{{ cancelText }}</el-button>
        <el-button type="primary" :loading="loading" @click="handleSubmit">
          {{ confirmText }}
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
/**
 * 表单弹窗。统一校验、提交按钮的加载态与关闭后的重置行为。
 * 只有校验通过才会向父组件发出 submit，页面里不用再写 validate().catch(() => false)。
 *
 * columns 用 CSS Grid 铺排表单项，替代原先层层嵌套的 el-row / el-col；
 * 需要整行占满的表单项加 class="zy-form__full" 即可。
 */
import { computed, ref } from 'vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, default: '' },
  /** 表单数据对象 */
  model: { type: Object, required: true },
  rules: { type: Object, default: () => ({}) },
  loading: { type: Boolean, default: false },
  width: { type: [String, Number], default: '640px' },
  top: { type: String, default: '8vh' },
  labelWidth: { type: [String, Number], default: '96px' },
  labelPosition: { type: String, default: 'right' },
  /** 表单列数 */
  columns: { type: Number, default: 1 },
  confirmText: { type: String, default: '确定' },
  cancelText: { type: String, default: '取消' }
})

const emit = defineEmits(['update:modelValue', 'submit', 'closed'])

const formRef = ref(null)

const gridStyle = computed(() => ({
  '--zy-form-columns': String(props.columns)
}))

const close = () => emit('update:modelValue', false)

const handleSubmit = async () => {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  emit('submit')
}

const handleClosed = () => {
  formRef.value?.resetFields()
  emit('closed')
}

defineExpose({
  /** 供父组件在特殊场景下手动校验或清除校验态 */
  validate: () => formRef.value?.validate(),
  clearValidate: () => formRef.value?.clearValidate()
})
</script>

<style scoped>
.zy-form {
  display: grid;
  grid-template-columns: repeat(var(--zy-form-columns, 1), minmax(0, 1fr));
  gap: 0 var(--zy-space-5);
}

.zy-form :deep(.el-form-item) {
  margin-bottom: var(--zy-space-5);
}

.zy-form :deep(.zy-form__full) {
  grid-column: 1 / -1;
}

.zy-form__footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--zy-space-2);
}

@media screen and (max-width: 768px) {
  .zy-form {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
