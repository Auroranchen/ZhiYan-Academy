<template>
  <div class="zy-page">
    <header class="zy-page__header">
      <el-breadcrumb v-if="breadcrumb.length" class="zy-page__crumb" separator="/">
        <el-breadcrumb-item v-for="item in breadcrumb" :key="item.label" :to="item.to">
          {{ item.label }}
        </el-breadcrumb-item>
      </el-breadcrumb>

      <div class="zy-page__heading">
        <div class="zy-page__title-group">
          <h1 class="zy-page__title">
            {{ title }}
            <el-tag v-if="badge" size="small" :type="badgeType" disable-transitions>{{ badge }}</el-tag>
          </h1>
          <p v-if="description" class="zy-page__description">{{ description }}</p>
        </div>
        <div v-if="$slots.actions" class="zy-page__actions">
          <slot name="actions" />
        </div>
      </div>

      <div v-if="$slots.summary" class="zy-page__summary">
        <slot name="summary" />
      </div>
    </header>

    <div class="zy-page__body">
      <slot />
    </div>
  </div>
</template>

<script setup>
/**
 * 页面外壳：统一所有业务页的标题区、面包屑、操作区与内容间距。
 * 页面自身不再各写一套 .page-header，避免 64 个页面的标题样式各不相同。
 */
defineProps({
  title: { type: String, required: true },
  description: { type: String, default: '' },
  /** 面包屑，形如 [{ label: '基础数据', to: '/admin/major' }] */
  breadcrumb: { type: Array, default: () => [] },
  /** 标题右侧的状态标签 */
  badge: { type: String, default: '' },
  badgeType: { type: String, default: 'info' }
})
</script>

<style scoped>
.zy-page {
  display: flex;
  flex-direction: column;
  gap: var(--zy-stack-gap);
}

.zy-page__header {
  display: flex;
  flex-direction: column;
  gap: var(--zy-space-3);
}

/* 面包屑用等宽字体 + 字母间距，呼应官网首页的 // SECTION 小标题。
   这是「品牌位」处理：只有页头这类非数据区域用它，表格表单不铺。 */
.zy-page__crumb {
  font-family: var(--zy-font-mono);
  font-size: var(--zy-font-size-xs);
  letter-spacing: var(--zy-letter-spacing-wide);
}

.zy-page__heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--zy-space-4);
  flex-wrap: wrap;
}

.zy-page__title-group {
  min-width: 0;
}

.zy-page__title {
  display: flex;
  align-items: center;
  gap: var(--zy-space-2);
  margin: 0;
  font-size: var(--zy-font-size-2xl);
  font-weight: var(--zy-font-weight-bold);
  line-height: var(--zy-line-height-tight);
  letter-spacing: var(--zy-letter-spacing-tight);
  color: var(--zy-text-primary);
}

.zy-page__description {
  margin: var(--zy-space-1) 0 0;
  font-size: var(--zy-font-size-sm);
  color: var(--zy-text-tertiary);
  max-width: 72ch;
}

.zy-page__actions {
  display: flex;
  align-items: center;
  gap: var(--zy-space-2);
  flex-wrap: wrap;
  flex-shrink: 0;
}

.zy-page__summary {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: var(--zy-space-3);
}

.zy-page__body {
  display: flex;
  flex-direction: column;
  gap: var(--zy-stack-gap);
}

@media screen and (max-width: 768px) {
  .zy-page__title {
    font-size: var(--zy-font-size-xl);
  }

  .zy-page__actions {
    width: 100%;
  }

  .zy-page__summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
