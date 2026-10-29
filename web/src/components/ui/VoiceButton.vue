<template>
  <button
    v-if="supported"
    type="button"
    class="zy-voice"
    :class="{ 'is-recording': recording }"
    :disabled="disabled"
    :title="recording ? '点击停止语音输入' : '点击开始语音输入'"
    :aria-label="recording ? '停止语音输入' : '开始语音输入'"
    :aria-pressed="recording"
    @click="$emit('toggle')"
  >
    <Icon :icon="recording ? 'ph:microphone-duotone' : 'ph:microphone'" :width="18" :height="18" />
  </button>
</template>

<script setup>
/**
 * 语音输入按钮。配合 useVoiceInput 使用，不支持语音的浏览器上自动不渲染。
 */
import { Icon } from '@iconify/vue'

defineProps({
  supported: { type: Boolean, default: false },
  recording: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false }
})

defineEmits(['toggle'])
</script>

<style scoped>
.zy-voice {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 36px;
  height: var(--zy-control-height);
  color: var(--zy-text-secondary);
  background: var(--zy-bg-surface);
  border: 1px solid var(--zy-border-default);
  border-radius: var(--zy-radius-control);
  cursor: pointer;
  transition: color var(--zy-duration-fast) var(--zy-ease-standard),
    border-color var(--zy-duration-fast) var(--zy-ease-standard),
    background-color var(--zy-duration-fast) var(--zy-ease-standard);
}

.zy-voice:hover:not(:disabled) {
  color: var(--zy-brand-base);
  background: var(--zy-brand-soft);
  border-color: var(--zy-brand-base);
}

.zy-voice:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.zy-voice.is-recording {
  color: var(--zy-text-inverse);
  background: var(--zy-danger-base);
  border-color: var(--zy-danger-base);
  animation: zy-voice-pulse 1.2s infinite ease-in-out;
}

@keyframes zy-voice-pulse {
  0%,
  100% {
    box-shadow: 0 0 0 0 rgb(220 38 38 / 45%);
  }

  50% {
    box-shadow: 0 0 0 6px rgb(220 38 38 / 0%);
  }
}

/* 录音时的呼吸动画对偏好减少动态效果的用户不友好 */
@media (prefers-reduced-motion: reduce) {
  .zy-voice.is-recording {
    animation: none;
  }
}
</style>
