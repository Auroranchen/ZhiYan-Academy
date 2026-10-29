<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    :width="width"
    append-to-body
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-descriptions :column="columns" border>
      <el-descriptions-item
        v-for="field in fields"
        :key="field.prop || field.label"
        :label="field.label"
        :span="field.span || 1"
      >
        <slot :name="field.slot" :row="data" :value="data?.[field.prop]">
          {{ display(field) }}
        </slot>
      </el-descriptions-item>
    </el-descriptions>

    <template #footer>
      <slot name="footer">
        <el-button @click="emit('update:modelValue', false)">关闭</el-button>
      </slot>
    </template>
  </el-dialog>
</template>

<script setup>
/**
 * 只读详情弹窗。fields 形如
 * [{ label: '课程名称', prop: 'coursename', formatter?, slot?, span? }]
 * 需要自定义渲染的字段声明 slot，再由父组件提供同名插槽。
 */
const props = defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, default: '详情' },
  fields: { type: Array, required: true },
  data: { type: Object, default: () => ({}) },
  columns: { type: Number, default: 1 },
  width: { type: [String, Number], default: '560px' },
  /** 空值占位 */
  placeholder: { type: String, default: '—' }
})

const emit = defineEmits(['update:modelValue'])

const display = (field) => {
  const raw = props.data?.[field.prop]
  if (field.formatter) return field.formatter(raw, props.data)
  return raw === null || raw === undefined || raw === '' ? props.placeholder : raw
}
</script>
