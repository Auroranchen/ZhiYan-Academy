<template>
  <div class="pagination">
    <el-pagination
      :current-page="page"
      :page-size="size"
      :page-sizes="pageSizes"
      :total="total"
      background
      layout="total, sizes, prev, pager, next, jumper"
      @update:current-page="handlePageChange"
      @update:page-size="handleSizeChange"
    />
  </div>
</template>

<script setup>
/**
 * 分页条
 *
 * 尚未并入 DataTable 的列表页在用。新页面请直接使用 ui/DataTable，
 * 它内置了分页，不需要再单独摆一个。
 */
const props = defineProps({
  page: { type: Number, default: 1 },
  size: { type: Number, default: 10 },
  total: { type: Number, default: 0 },
  pageSizes: { type: Array, default: () => [10, 20, 50, 100] }
})

const emit = defineEmits(['update:page', 'update:size', 'change'])

const handlePageChange = (next) => {
  emit('update:page', next)
  emit('change', { page: next, size: props.size })
}

const handleSizeChange = (nextSize) => {
  // 每页条数变化后停留在原页码可能越界，统一回到第一页
  emit('update:size', nextSize)
  emit('update:page', 1)
  emit('change', { page: 1, size: nextSize })
}
</script>

<style scoped>
.pagination {
  display: flex;
  justify-content: flex-end;
  padding: var(--zy-space-5) 0;
}
</style>
