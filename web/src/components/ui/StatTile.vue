<template>
  <component
    :is="to ? 'button' : 'div'"
    class="zy-stat"
    :class="{ 'zy-stat--clickable': !!to }"
    :type="to ? 'button' : undefined"
    @click="handleClick"
  >
    <span class="zy-stat__label">
      <Icon v-if="icon" :icon="icon" class="zy-stat__icon" />
      {{ label }}
    </span>
    <span class="zy-stat__value" :class="`zy-stat__value--${tone}`">
      {{ displayValue }}<span v-if="unit" class="zy-stat__unit">{{ unit }}</span>
    </span>
    <span v-if="hint" class="zy-stat__hint">{{ hint }}</span>
  </component>
</template>

<script setup>
/**
 * 指标卡。企业中后台的首页与统计页大量重复这个结构，
 * 原先每个页面各写一份 .stat-card 样式。
 */
import { computed } from 'vue'
import { Icon } from '@iconify/vue'
import { useRouter } from 'vue-router'

const props = defineProps({
  label: { type: String, required: true },
  value: { type: [Number, String], default: 0 },
  unit: { type: String, default: '' },
  hint: { type: String, default: '' },
  /** Iconify 图标名，例如 ph:student-duotone */
  icon: { type: String, default: '' },
  /** 语义色，仅作用于数值 */
  tone: {
    type: String,
    default: 'default',
    validator: (v) => ['default', 'brand', 'success', 'warning', 'danger'].includes(v)
  },
  /** 传入后整块可点击并跳转 */
  to: { type: [String, Object], default: null }
})

const router = useRouter()

const displayValue = computed(() => {
  const v = props.value
  return typeof v === 'number' ? v.toLocaleString('zh-CN') : v
})

const handleClick = () => {
  if (props.to) router.push(props.to)
}
</script>

<style scoped>
.zy-stat {
  display: flex;
  flex-direction: column;
  gap: var(--zy-space-1);
  padding: var(--zy-space-4);
  background: var(--zy-bg-surface);
  border: 1px solid var(--zy-border-subtle);
  border-radius: var(--zy-radius-surface);
  text-align: left;
  font-family: inherit;
}

.zy-stat--clickable {
  cursor: pointer;
  transition: border-color var(--zy-duration-fast) var(--zy-ease-standard),
              background-color var(--zy-duration-fast) var(--zy-ease-standard);
}

.zy-stat--clickable:hover {
  border-color: var(--zy-brand-border);
  background: var(--zy-bg-hover);
}

.zy-stat__label {
  display: flex;
  align-items: center;
  gap: var(--zy-space-1);
  font-size: var(--zy-font-size-xs);
  font-weight: var(--zy-font-weight-medium);
  letter-spacing: var(--zy-letter-spacing-wide);
  color: var(--zy-text-tertiary);
}

.zy-stat__icon {
  font-size: 14px;
}

/* 数字用衬线加粗，复刻官网首页 .stat__num 的处理。
   这是「品牌位」：只给统计数字，表格里的数值不走这套。 */
.zy-stat__value {
  font-family: var(--zy-font-serif);
  font-size: var(--zy-font-size-3xl);
  font-weight: var(--zy-font-weight-bold);
  line-height: var(--zy-line-height-tight);
  letter-spacing: var(--zy-letter-spacing-tight);
  color: var(--zy-text-primary);
  font-variant-numeric: tabular-nums;
}

.zy-stat__value--brand { color: var(--zy-brand-base); }
.zy-stat__value--success { color: var(--zy-success-base); }
.zy-stat__value--warning { color: var(--zy-warning-base); }
.zy-stat__value--danger { color: var(--zy-danger-base); }

.zy-stat__unit {
  margin-left: 2px;
  font-size: var(--zy-font-size-sm);
  font-weight: var(--zy-font-weight-medium);
  color: var(--zy-text-tertiary);
}

.zy-stat__hint {
  font-size: var(--zy-font-size-xs);
  color: var(--zy-text-tertiary);
}
</style>
