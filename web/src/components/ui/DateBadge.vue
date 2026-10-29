<template>
  <div class="zy-date">
    <span class="zy-date__day">{{ day }}</span>
    <span class="zy-date__week">{{ week }}</span>
  </div>
</template>

<script setup>
/** 三端首页右上角的今日日期 */
const WEEK_DAYS = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']

const today = new Date()
const day = `${today.getMonth() + 1} 月 ${today.getDate()} 日`
const week = WEEK_DAYS[today.getDay()]
</script>

<style scoped>
.zy-date {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  line-height: 1.3;
}

.zy-date__day {
  font-size: var(--zy-font-size-base);
  font-weight: var(--zy-font-weight-semibold);
  color: var(--zy-text-primary);
}

.zy-date__week {
  font-size: var(--zy-font-size-xs);
  color: var(--zy-text-tertiary);
}
</style>
