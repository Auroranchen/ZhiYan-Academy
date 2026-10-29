<template>
  <!-- 代码展示面板（暗色主题 + 语法高亮） -->
  <div class="code-panel" v-show="visible">
    <div class="code-panel-header">
      <span class="code-lang-tag">{{ language }}</span>
    </div>
    <div class="code-panel-body" ref="codeBodyRef">
      <pre class="code-pre"><code v-safe-html="highlighted"></code></pre>
    </div>
  </div>
</template>

<script setup>
import { computed, watch, ref, nextTick } from 'vue'
import { highlightCode } from '@/utils/codePanelLogic'

const props = defineProps({
  code: {
    type: String,
    default: ''
  },
  language: {
    type: String,
    default: 'plaintext'
  },
  visible: {
    type: Boolean,
    default: false
  }
})

const codeBodyRef = ref(null)

// 计算高亮后的HTML
const highlighted = computed(() => highlightCode(props.code, props.language))

// 代码变化时自动滚动到顶部
watch(() => props.code, () => {
  nextTick(() => {
    if (codeBodyRef.value) {
      codeBodyRef.value.scrollTop = 0
    }
  })
})
</script>

<style scoped>
/* 代码面板容器 */
.code-panel {
  background: #1e1e2e;
  border-radius: 12px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  border: 1px solid rgba(255,255,255,0.08);
}

/* 顶部语言标签栏 */
.code-panel-header {
  padding: 8px 16px;
  background: rgba(0,0,0,0.3);
  display: flex;
  align-items: center;
}

.code-lang-tag {
  font-size: 12px;
  color: #a0a0b0;
  text-transform: uppercase;
  font-weight: 600;
  letter-spacing: 0.5px;
}

/* 代码内容区域 */
.code-panel-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

.code-pre {
  margin: 0;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 14px;
  line-height: 1.6;
  color: #cdd6f4;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>

<style>
/* 非scoped样式，使v-html中的span能被着色 */
.code-pre .hl-keyword { color: #cba6f7; font-weight: 600; }
.code-pre .hl-string { color: #a6e3a1; }
.code-pre .hl-comment { color: #6c7086; font-style: italic; }
.code-pre .hl-number { color: #fab387; }
</style>
