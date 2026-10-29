<template>
  <form class="zy-filter" @submit.prevent="emit('search')">
    <div class="zy-filter__fields">
      <slot />
    </div>
    <div class="zy-filter__actions">
      <el-button type="primary" native-type="submit" :loading="loading">查询</el-button>
      <el-button :disabled="loading" @click="emit('reset')">重置</el-button>
      <slot name="extra" />
    </div>
  </form>
</template>

<script setup>
/**
 * 查询条件栏。业务页只声明条件控件，查询与重置按钮由本组件统一提供，
 * 顺带把回车提交也接管了 —— 原先各页面要在每个输入框上挂 @keyup.enter。
 */
defineProps({
  loading: { type: Boolean, default: false }
})

const emit = defineEmits(['search', 'reset'])
</script>

<style scoped>
.zy-filter {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--zy-space-4);
  flex-wrap: wrap;
  padding: var(--zy-space-4);
  background: var(--zy-bg-surface);
  border: 1px solid var(--zy-border-subtle);
  border-radius: var(--zy-radius-surface);
}

.zy-filter__fields {
  display: flex;
  align-items: center;
  gap: var(--zy-space-2);
  flex-wrap: wrap;
  flex: 1 1 auto;
  min-width: 0;
}

.zy-filter__actions {
  display: flex;
  align-items: center;
  gap: var(--zy-space-2);
  flex-shrink: 0;
}

/* 条件控件统一宽度，避免各页面各自写 style="width: 200px" */
.zy-filter__fields :deep(.el-input),
.zy-filter__fields :deep(.el-select),
.zy-filter__fields :deep(.el-date-editor) {
  width: 180px;
}

.zy-filter__fields :deep(.el-date-editor--daterange) {
  width: 260px;
}

@media screen and (max-width: 768px) {
  .zy-filter__fields,
  .zy-filter__actions {
    width: 100%;
  }

  .zy-filter__fields :deep(.el-input),
  .zy-filter__fields :deep(.el-select),
  .zy-filter__fields :deep(.el-date-editor) {
    width: 100%;
  }

  .zy-filter__actions :deep(.el-button) {
    flex: 1;
  }
}
</style>
