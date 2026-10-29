<template>
  <div
    class="user-avatar"
    :class="'user-avatar--' + size"
    :style="!showImage ? { background: avatarBgColor } : {}"
  >
    <!-- Custom image avatar -->
    <img
      v-if="showImage"
      :src="avatarUrl"
      :alt="name"
      class="user-avatar__img"
      @error="onImageError"
    />
    <!-- Default text avatar (first character of name) -->
    <span v-else-if="firstChar" class="user-avatar__text">{{ firstChar }}</span>
    <!-- Fallback icon when name is empty -->
    <Icon v-else icon="ph:user-duotone" class="user-avatar__icon" />
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { Icon } from '@iconify/vue'
import { getAvatarColor, getFirstChar } from '@/utils/avatar'

const props = defineProps({
  name: { type: String, default: '' },
  avatarUrl: { type: String, default: '' },
  size: {
    type: String,
    default: 'small',
    validator: (val) => ['small', 'medium', 'large'].includes(val)
  }
})

const imageError = ref(false)

const showImage = computed(() => props.avatarUrl && !imageError.value)
const firstChar = computed(() => getFirstChar(props.name))
const avatarBgColor = computed(() => firstChar.value ? getAvatarColor(props.name) : 'hsl(0, 0%, 75%)')

watch(() => props.avatarUrl, () => { imageError.value = false })

const onImageError = () => { imageError.value = true }
</script>

<style scoped>
.user-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  overflow: hidden;
  flex-shrink: 0;
  color: #ffffff;
  font-weight: 600;
  line-height: 1;
  user-select: none;
}

/* Size variants */
.user-avatar--small {
  width: 42px;
  height: 42px;
  font-size: 18px;
}

.user-avatar--medium {
  width: 54px;
  height: 54px;
  font-size: 22px;
}

.user-avatar--large {
  width: 80px;
  height: 80px;
  font-size: 34px;
}

/* Image avatar */
.user-avatar__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* Text character */
.user-avatar__text {
  display: flex;
  align-items: center;
  justify-content: center;
}

/* Fallback icon */
.user-avatar__icon {
  width: 60%;
  height: 60%;
  opacity: 0.9;
}
</style>
