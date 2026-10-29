<template>
  <div class="zy-md" :class="`zy-md--${size}`" v-safe-html="html"></div>
</template>

<script setup>
/**
 * Markdown 渲染视图。
 *
 * AI 讲解、周报、思维导图详解等页面都要渲染 Markdown，
 * 原先各自复制了一份排版 CSS，且颜色写死导致深色模式不可读。
 */
import { computed } from 'vue'
import { renderMarkdown } from '@/utils/miniMarkdown'

const props = defineProps({
  content: { type: String, default: '' },
  /** compact 用于抽屉、侧栏等窄容器 */
  size: { type: String, default: 'base', validator: (v) => ['base', 'compact'].includes(v) }
})

const html = computed(() => renderMarkdown(props.content))
</script>

<style scoped>
.zy-md {
  color: var(--zy-text-secondary);
  font-size: var(--zy-font-size-base);
  line-height: var(--zy-line-height-relaxed);
  word-break: break-word;
}

.zy-md--compact {
  font-size: var(--zy-font-size-sm);
}

.zy-md :deep(h1),
.zy-md :deep(h2) {
  margin: var(--zy-space-5) 0 var(--zy-space-2);
  padding-left: var(--zy-space-2);
  font-size: var(--zy-font-size-lg);
  font-weight: var(--zy-font-weight-semibold);
  color: var(--zy-text-primary);
  border-left: 3px solid var(--zy-brand-base);
}

.zy-md :deep(h3),
.zy-md :deep(h4) {
  margin: var(--zy-space-4) 0 var(--zy-space-2);
  font-size: var(--zy-font-size-md);
  font-weight: var(--zy-font-weight-semibold);
  color: var(--zy-text-primary);
}

.zy-md--compact :deep(h1),
.zy-md--compact :deep(h2),
.zy-md--compact :deep(h3),
.zy-md--compact :deep(h4) {
  margin: var(--zy-space-3) 0 var(--zy-space-1);
  font-size: var(--zy-font-size-base);
}

.zy-md :deep(p) {
  margin: var(--zy-space-2) 0;
}

.zy-md :deep(ul),
.zy-md :deep(ol) {
  margin: var(--zy-space-2) 0;
  padding-left: var(--zy-space-6);
}

.zy-md :deep(li) {
  margin: var(--zy-space-1) 0;
}

.zy-md :deep(strong) {
  color: var(--zy-brand-base);
  font-weight: var(--zy-font-weight-semibold);
}

.zy-md :deep(a) {
  color: var(--zy-brand-base);
}

.zy-md :deep(blockquote) {
  margin: var(--zy-space-3) 0;
  padding: var(--zy-space-1) var(--zy-space-4);
  color: var(--zy-text-tertiary);
  border-left: 3px solid var(--zy-border-strong);
}

.zy-md :deep(code) {
  padding: 1px 6px;
  font-family: var(--zy-font-mono);
  font-size: var(--zy-font-size-sm);
  color: var(--zy-danger-base);
  background: var(--zy-bg-sunken);
  border-radius: var(--zy-radius-sm);
}

.zy-md :deep(pre) {
  margin: var(--zy-space-3) 0;
  padding: var(--zy-space-3);
  overflow-x: auto;
  color: var(--zy-palette-neutral-100);
  background: var(--zy-palette-neutral-950);
  border-radius: var(--zy-radius-md);
}

.zy-md :deep(pre code) {
  padding: 0;
  color: inherit;
  background: none;
}

.zy-md :deep(table) {
  width: 100%;
  margin: var(--zy-space-3) 0;
  border-collapse: collapse;
  font-size: var(--zy-font-size-sm);
}

.zy-md :deep(th),
.zy-md :deep(td) {
  padding: var(--zy-space-2) var(--zy-space-3);
  text-align: left;
  border: 1px solid var(--zy-border-subtle);
}

.zy-md :deep(th) {
  color: var(--zy-text-primary);
  background: var(--zy-bg-sunken);
  font-weight: var(--zy-font-weight-semibold);
}

.zy-md :deep(hr) {
  margin: var(--zy-space-4) 0;
  border: none;
  border-top: 1px dashed var(--zy-border-default);
}

.zy-md :deep(img) {
  max-width: 100%;
  border-radius: var(--zy-radius-md);
}
</style>
