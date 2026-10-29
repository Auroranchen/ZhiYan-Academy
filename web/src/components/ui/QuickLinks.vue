<template>
  <nav class="zy-quick" :style="{ '--zy-quick-min': `${minWidth}px` }">
    <button
      v-for="item in items"
      :key="item.label"
      class="zy-quick__item"
      type="button"
      @click="router.push(item.to)"
    >
      <Icon :icon="item.icon" class="zy-quick__icon" />
      <span class="zy-quick__label">{{ item.label }}</span>
      <span v-if="item.badge" class="zy-quick__badge">{{ item.badge > 99 ? '99+' : item.badge }}</span>
    </button>
  </nav>
</template>

<script setup>
/** 快捷入口宫格。items 形如 [{ label, icon, to, badge }] */
import { Icon } from '@iconify/vue'
import { useRouter } from 'vue-router'

defineProps({
  items: { type: Array, required: true },
  /** 单个入口的最小宽度，决定每行放几个 */
  minWidth: { type: Number, default: 96 }
})

const router = useRouter()
</script>

<style scoped>
.zy-quick {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(var(--zy-quick-min), 1fr));
  gap: var(--zy-space-2);
}

.zy-quick__item {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--zy-space-2);
  padding: var(--zy-space-4) var(--zy-space-2);
  border: 1px solid transparent;
  border-radius: var(--zy-radius-md);
  background: var(--zy-bg-subtle);
  color: var(--zy-text-secondary);
  font-family: inherit;
  font-size: var(--zy-font-size-sm);
  cursor: pointer;
  transition: background-color var(--zy-duration-fast) var(--zy-ease-standard),
              border-color var(--zy-duration-fast) var(--zy-ease-standard),
              color var(--zy-duration-fast) var(--zy-ease-standard);
}

.zy-quick__item:hover {
  background: var(--zy-brand-soft);
  border-color: var(--zy-brand-border);
  color: var(--zy-brand-base);
}

.zy-quick__icon {
  font-size: 24px;
}

.zy-quick__label {
  white-space: nowrap;
}

.zy-quick__badge {
  position: absolute;
  top: 6px;
  right: 6px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: var(--zy-radius-full);
  background: var(--zy-palette-danger-500);
  color: #fff;
  font-size: 10px;
  font-weight: var(--zy-font-weight-bold);
  line-height: 16px;
}
</style>
