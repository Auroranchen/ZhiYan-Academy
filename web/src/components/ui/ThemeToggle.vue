<template>
  <el-dropdown trigger="click" placement="bottom-end" @command="handleCommand">
    <button class="zy-theme-toggle" type="button" :aria-label="`外观设置，当前${modeLabel}`">
      <Icon :icon="isDark ? 'ph:moon-duotone' : 'ph:sun-duotone'" :width="20" :height="20" />
    </button>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item
          v-for="option in modeOptions"
          :key="option.value"
          :command="`mode:${option.value}`"
        >
          <Icon :icon="option.icon" class="zy-theme-toggle__icon" />
          <span :class="{ 'is-current': mode === option.value }">{{ option.label }}</span>
        </el-dropdown-item>
        <el-dropdown-item divided command="density">
          <Icon icon="ph:rows-duotone" class="zy-theme-toggle__icon" />
          <span>{{ density === 'compact' ? '切换为舒适密度' : '切换为紧凑密度' }}</span>
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<script setup>
/** 外观设置：亮色 / 暗色 / 跟随系统，以及表格与控件的显示密度 */
import { computed } from 'vue'
import { Icon } from '@iconify/vue'
import { useTheme } from '@/composables/useTheme'

const { mode, density, isDark, setMode, toggleDensity } = useTheme()

const modeOptions = [
  { value: 'light', label: '亮色', icon: 'ph:sun-duotone' },
  { value: 'dark', label: '暗色', icon: 'ph:moon-duotone' },
  { value: 'auto', label: '跟随系统', icon: 'ph:desktop-duotone' }
]

const modeLabel = computed(
  () => modeOptions.find((o) => o.value === mode.value)?.label || '亮色'
)

const handleCommand = (command) => {
  if (command === 'density') {
    toggleDensity()
    return
  }
  setMode(command.replace('mode:', ''))
}
</script>

<style scoped>
.zy-theme-toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  padding: 0;
  border: none;
  border-radius: var(--zy-radius-md);
  background: transparent;
  color: var(--zy-text-secondary);
  cursor: pointer;
  transition: background-color var(--zy-duration-fast) var(--zy-ease-standard),
              color var(--zy-duration-fast) var(--zy-ease-standard);
}

.zy-theme-toggle:hover {
  background: var(--zy-bg-hover);
  color: var(--zy-text-primary);
}

.zy-theme-toggle__icon {
  margin-right: var(--zy-space-2);
  font-size: 16px;
}

.is-current {
  color: var(--zy-brand-base);
  font-weight: var(--zy-font-weight-medium);
}
</style>
