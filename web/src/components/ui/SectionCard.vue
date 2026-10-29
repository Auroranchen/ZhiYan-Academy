<template>
  <section class="zy-section">
    <header class="zy-section__head">
      <h2 class="zy-section__title">
        {{ title }}
        <span v-if="count !== null" class="zy-section__count">{{ count }}</span>
      </h2>
      <button v-if="actionLabel" class="zy-section__action" type="button" @click="handleAction">
        {{ actionLabel }}
      </button>
      <slot name="action" />
    </header>

    <div class="zy-section__body">
      <el-skeleton v-if="loading" :rows="skeletonRows" animated />
      <div v-else-if="empty" class="zy-section__empty">
        <Icon :icon="emptyIcon" class="zy-section__empty-icon" />
        <span>{{ emptyText }}</span>
      </div>
      <slot v-else />
    </div>
  </section>
</template>

<script setup>
/**
 * 内容分区卡片。三端首页与详情页反复出现「标题 + 查看全部 + 列表/空状态」这个结构，
 * 原先每个页面各写一份 .panel 样式与骨架屏逻辑。
 */
import { Icon } from '@iconify/vue'
import { useRouter } from 'vue-router'

const props = defineProps({
  title: { type: String, required: true },
  /** 标题后的计数徽标，传 null 则不显示 */
  count: { type: [Number, String], default: null },
  /** 右上角操作文案，为空则不渲染 */
  actionLabel: { type: String, default: '' },
  /** 点击操作后跳转的路由；不传则只发出 action 事件 */
  actionTo: { type: [String, Object], default: null },
  loading: { type: Boolean, default: false },
  skeletonRows: { type: Number, default: 4 },
  empty: { type: Boolean, default: false },
  emptyText: { type: String, default: '暂无数据' },
  emptyIcon: { type: String, default: 'ph:tray-duotone' }
})

const emit = defineEmits(['action'])
const router = useRouter()

const handleAction = () => {
  if (props.actionTo) router.push(props.actionTo)
  else emit('action')
}
</script>

<style scoped>
.zy-section {
  display: flex;
  flex-direction: column;
  background: var(--zy-bg-surface);
  border: 1px solid var(--zy-border-subtle);
  border-radius: var(--zy-radius-surface);
  overflow: hidden;
}

.zy-section__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--zy-space-3);
  padding: var(--zy-space-4) var(--zy-space-5);
  border-bottom: 1px solid var(--zy-border-subtle);
}

.zy-section__title {
  display: flex;
  align-items: center;
  gap: var(--zy-space-2);
  margin: 0;
  font-size: var(--zy-font-size-md);
  font-weight: var(--zy-font-weight-semibold);
  color: var(--zy-text-primary);
}

.zy-section__count {
  padding: 1px 7px;
  border-radius: var(--zy-radius-full);
  background: var(--zy-bg-sunken);
  color: var(--zy-text-tertiary);
  font-size: var(--zy-font-size-xs);
  font-weight: var(--zy-font-weight-medium);
  font-variant-numeric: tabular-nums;
}

.zy-section__action {
  padding: 0;
  border: none;
  background: none;
  color: var(--zy-text-tertiary);
  font-family: inherit;
  font-size: var(--zy-font-size-sm);
  cursor: pointer;
  transition: color var(--zy-duration-fast) var(--zy-ease-standard);
}

.zy-section__action:hover {
  color: var(--zy-brand-base);
}

.zy-section__body {
  flex: 1;
  padding: var(--zy-space-2) var(--zy-space-5) var(--zy-space-4);
}

.zy-section__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--zy-space-2);
  padding: var(--zy-space-8) 0;
  color: var(--zy-text-tertiary);
  font-size: var(--zy-font-size-sm);
}

.zy-section__empty-icon {
  font-size: 36px;
  opacity: 0.45;
}
</style>
