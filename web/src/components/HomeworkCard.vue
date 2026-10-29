<template>
  <div class="homework-card hover-lift" :class="{ 'homework-card--urgent': isUrgent }">
    <!-- Header: course name + status tag -->
    <div class="homework-card-header">
      <span class="homework-card-course">{{ homework.coursename }}</span>
      <span v-if="mode === 'pending'" class="homework-card-status" :class="statusClass">
        {{ statusLabel }}
      </span>
      <span v-else-if="mode === 'completed' && homework.score !== null && homework.score !== undefined" class="homework-card-score">
        <Icon icon="ph:star-duotone" class="score-icon" />
        {{ homework.score }}
      </span>
      <span v-else class="homework-card-status status-pending">待批改</span>
    </div>

    <!-- Title -->
    <div class="homework-card-title">{{ homework.title || homework.homeworktitle }}</div>

    <!-- Meta: deadline + homework type -->
    <div class="homework-card-meta">
      <div class="meta-item">
        <Icon icon="ph:calendar-duotone" class="meta-icon" />
        <span>{{ formatDeadline }}</span>
      </div>
      <div class="meta-item">
        <Icon icon="ph:file-text-duotone" class="meta-icon" />
        <span>{{ homeworkTypeLabel }}</span>
      </div>
    </div>

    <!-- Urgent warning badge -->
    <div v-if="isUrgent" class="homework-card-urgent-badge">
      <Icon icon="ph:warning-circle-duotone" class="urgent-icon" />
      即将截止
    </div>

    <!-- Comment excerpt (completed mode) -->
    <div v-if="mode === 'completed' && homework.comment" class="homework-card-comment">
      <Icon icon="ph:chat-circle-text-duotone" class="comment-icon" />
      <span class="comment-text">{{ commentExcerpt }}</span>
    </div>

    <!-- Actions -->
    <div class="homework-card-actions">
      <template v-if="mode === 'pending'">
        <el-button
          v-if="!homework.submitted && !isExpired"
          type="primary"
          size="small"
          @click.stop="$emit('submit', homework)"
        >
          提交作业
        </el-button>
        <el-button
          v-if="homework.submitted"
          size="small"
          @click.stop="$emit('view', homework)"
        >
          查看
        </el-button>
        <span v-if="!homework.submitted && isExpired" class="expired-label">已截止</span>
      </template>
      <template v-if="mode === 'completed'">
        <div class="actions-row">
          <el-button size="small" class="btn-view" @click.stop="$emit('view', homework)">
            <Icon icon="ph:eye-duotone" style="margin-right: 4px;" />
            查看详情
          </el-button>
          <el-button size="small" class="btn-ai-analyze" @click.stop="$emit('analyze', homework)">
            <Icon icon="ph:robot-duotone" style="margin-right: 4px;" />
            AI讲解
          </el-button>
        </div>
        <el-button size="small" class="btn-export-pdf" @click.stop="$emit('export', homework)">
          <Icon icon="ph:file-pdf-duotone" style="margin-right: 4px;" />
          导出PDF
        </el-button>
      </template>
    </div>
  </div>
</template>

<script setup>
/** 学生端作业卡片，待办与已完成两种形态 */
import { computed } from 'vue'
import { Icon } from '@iconify/vue'
import { formatDate } from '@/utils/format'

/** 距截止不足两天就标红提醒 */
const URGENT_WINDOW = 48 * 60 * 60 * 1000
/** 评语超过这个长度就截断，卡片高度才稳定 */
const COMMENT_MAX = 50

const props = defineProps({
  homework: { type: Object, required: true },
  mode: {
    type: String,
    default: 'pending',
    validator: (val) => ['pending', 'completed'].includes(val)
  }
})

defineEmits(['submit', 'view', 'export', 'analyze'])

const deadlineAt = computed(() =>
  props.homework.deadline ? new Date(props.homework.deadline).getTime() : null
)

const isExpired = computed(() => deadlineAt.value != null && Date.now() > deadlineAt.value)

const isUrgent = computed(() => {
  if (deadlineAt.value == null) return false
  const left = deadlineAt.value - Date.now()
  return left > 0 && left < URGENT_WINDOW
})

const formatDeadline = computed(() => formatDate(props.homework.deadline, 'minute', '-'))

const homeworkTypeLabel = computed(() =>
  (props.homework.type ?? props.homework.homeworktype) === 1 ? '在线答题' : '文档上传'
)

const statusLabel = computed(() => (props.homework.submitted ? '已提交' : '未提交'))

const statusClass = computed(() => (props.homework.submitted ? 'status-success' : 'status-warning'))

const commentExcerpt = computed(() => {
  // 自动评分的前缀对学生没有意义，展示时去掉
  const cleaned = (props.homework.comment || '').replace('[AI自动评分] ', '')
  return cleaned.length > COMMENT_MAX ? `${cleaned.slice(0, COMMENT_MAX)}...` : cleaned
})
</script>

<style scoped>
.homework-card {
  background: var(--color-bg-card);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-sm);
  padding: 16px 20px;
  border: 1px solid var(--color-border);
  position: relative;
  cursor: pointer;
  display: flex;
  flex-direction: column;
}

.homework-card--urgent {
  border-color: var(--color-warning);
  border-width: 1.5px;
}

/* Header */
.homework-card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 6px;
}

.homework-card-course {
  flex: 1;
  min-width: 0;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  font-weight: 500;
  line-height: 1.35;
}

.homework-card-status {
  flex-shrink: 0;
  white-space: nowrap;
  font-size: var(--font-size-xs);
  padding: 2px 10px;
  border-radius: var(--radius-full);
  font-weight: 500;
}

.status-success {
  background: #dcfce7;
  color: #16a34a;
}

.status-warning {
  background: #fef3c7;
  color: #d97706;
}

.status-pending {
  background: #f1f5f9;
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
  padding: 2px 10px;
  border-radius: var(--radius-full);
  font-weight: 500;
}

.homework-card-score {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: var(--font-size-base);
  font-weight: 700;
  color: var(--color-primary);
}

.score-icon {
  font-size: 18px;
  color: var(--color-accent);
}

/* Title */
.homework-card-title {
  font-size: var(--font-size-base);
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: 6px;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

/* Meta */
.homework-card-meta {
  display: flex;
  gap: 16px;
  margin-bottom: 6px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}

.meta-icon {
  font-size: 16px;
  color: var(--color-text-muted);
}

/* Urgent badge */
.homework-card-urgent-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: #fef2f2;
  color: var(--color-danger);
  font-size: var(--font-size-xs);
  font-weight: 600;
  padding: 4px 10px;
  border-radius: var(--radius-full);
  margin-bottom: var(--spacing-sm);
}

.urgent-icon {
  font-size: 14px;
}

/* Comment excerpt */
.homework-card-comment {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin-bottom: 6px;
  padding: 8px;
  background: var(--color-primary-bg);
  border-radius: var(--radius-sm);
}

.comment-icon {
  font-size: 16px;
  color: var(--color-primary);
  flex-shrink: 0;
  margin-top: 1px;
}

.comment-text {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  line-height: 1.4;
}

/* Actions */
.homework-card-actions {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: auto;
  padding-top: 10px;
  border-top: 1px solid var(--color-border);
}

.actions-row {
  display: flex;
  gap: 6px;
}

.actions-row .el-button {
  flex: 1;
}

.homework-card-actions > .el-button {
  width: 100%;
}

.expired-label {
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}

.btn-view {
  background: #f0f9eb;
  border-color: #c2e7b0;
  color: #67c23a;
}
.btn-view:hover {
  background: #e1f3d8;
  border-color: #67c23a;
  color: #67c23a;
}

.btn-ai-analyze {
  background: #ecf5ff;
  border-color: #b3d8ff;
  color: #409eff;
}
.btn-ai-analyze:hover {
  background: #d9ecff;
  border-color: #409eff;
  color: #409eff;
}

.btn-export-pdf {
  background: #fdf6ec;
  border-color: #f5dab1;
  color: #e6a23c;
}
.btn-export-pdf:hover {
  background: #faecd8;
  border-color: #e6a23c;
  color: #e6a23c;
}

/* Mobile */
@media (max-width: 768px) {
  .homework-card {
    padding: 12px 14px;
  }

  .homework-card-meta {
    flex-direction: column;
    gap: 4px;
  }
}
</style>
