<template>
  <ul class="zy-feed">
    <li
      v-for="item in items"
      :key="item[itemKey]"
      class="zy-feed__item"
      :class="{ 'is-clickable': clickable }"
      @click="clickable && emit('select', item)"
    >
      <span class="zy-feed__main">
        <span class="zy-feed__title">
          <slot name="title" :item="item">{{ item.title }}</slot>
        </span>
        <span v-if="$slots.sub" class="zy-feed__sub">
          <slot name="sub" :item="item" />
        </span>
      </span>
      <span v-if="$slots.meta" class="zy-feed__meta">
        <slot name="meta" :item="item" />
      </span>
    </li>
  </ul>
</template>

<script setup>
/**
 * 首页与详情页的信息流列表：一行一条，左侧标题加副标题，右侧元信息或状态标签。
 * 通知、作业、消息等列表共用这一套排版。
 */
defineProps({
  items: { type: Array, required: true },
  /** 用作 key 的字段名 */
  itemKey: { type: String, default: 'id' },
  clickable: { type: Boolean, default: true }
})

const emit = defineEmits(['select'])
</script>

<style scoped>
.zy-feed {
  margin: 0;
  padding: 0;
  list-style: none;
}

.zy-feed__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--zy-space-3);
  padding: var(--zy-space-3) 0;
  border-bottom: 1px solid var(--zy-border-subtle);
}

.zy-feed__item:last-child {
  border-bottom: none;
}

.zy-feed__item.is-clickable {
  cursor: pointer;
}

.zy-feed__item.is-clickable:hover .zy-feed__title {
  color: var(--zy-brand-base);
}

.zy-feed__main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.zy-feed__title {
  font-size: var(--zy-font-size-base);
  color: var(--zy-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color var(--zy-duration-fast) var(--zy-ease-standard);
}

.zy-feed__sub {
  font-size: var(--zy-font-size-xs);
  color: var(--zy-text-tertiary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.zy-feed__meta {
  flex-shrink: 0;
  font-size: var(--zy-font-size-xs);
  color: var(--zy-text-tertiary);
  font-variant-numeric: tabular-nums;
}
</style>
