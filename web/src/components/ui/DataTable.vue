<template>
  <div class="zy-table">
    <el-table
      v-loading="loading"
      :data="data"
      :row-key="rowKey"
      :border="border"
      :stripe="stripe"
      :height="height"
      :max-height="maxHeight"
      :default-sort="defaultSort"
      class="zy-table__el"
      @selection-change="emit('update:selection', $event)"
      @sort-change="emit('sort-change', $event)"
      @row-click="emit('row-click', $event)"
    >
      <el-table-column v-if="selectable" type="selection" width="46" :reserve-selection="!!rowKey" />
      <el-table-column v-if="showIndex" type="index" label="#" width="56" align="center" />

      <el-table-column
        v-for="col in columns"
        :key="col.prop || col.slot || col.label"
        :prop="col.prop"
        :label="col.label"
        :width="col.width"
        :min-width="col.minWidth"
        :align="col.align"
        :fixed="col.fixed"
        :sortable="col.sortable"
        :sort-by="col.sortBy"
        :formatter="col.formatter"
        :show-overflow-tooltip="col.showOverflowTooltip !== false"
      >
        <template v-if="col.slot" #default="scope">
          <slot :name="col.slot" v-bind="scope" />
        </template>
      </el-table-column>

      <el-table-column
        v-if="$slots.actions"
        label="操作"
        :width="actionsWidth"
        :min-width="actionsWidth ? undefined : 140"
        fixed="right"
        align="right"
      >
        <template #default="scope">
          <div class="zy-table__actions">
            <slot name="actions" v-bind="scope" />
          </div>
        </template>
      </el-table-column>

      <template #empty>
        <div class="zy-table__empty">
          <slot name="empty">
            <p class="zy-table__empty-title">{{ emptyText }}</p>
            <p v-if="emptyHint" class="zy-table__empty-hint">{{ emptyHint }}</p>
          </slot>
        </div>
      </template>
    </el-table>

    <div v-if="showPagination" class="zy-table__footer">
      <span class="zy-table__count">共 {{ total }} 条</span>
      <el-pagination
        :current-page="page"
        :page-size="pageSize"
        :page-sizes="pageSizes"
        :total="total"
        background
        layout="sizes, prev, pager, next, jumper"
        @update:current-page="handlePageChange"
        @update:page-size="handleSizeChange"
      />
    </div>
  </div>
</template>

<script setup>
/**
 * 列表页表格。把表格、加载态、空状态、分页收成一个组件，
 * 替代原先每个列表页各写一遍 el-table + Pagination + v-loading 的重复结构。
 *
 * 列通过 columns 声明；需要自定义渲染的列加 slot 字段，再由父组件提供同名插槽。
 */
const props = defineProps({
  /** [{ prop, label, width, minWidth, align, fixed, sortable, sortBy, slot, formatter }] */
  columns: { type: Array, required: true },
  data: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  rowKey: { type: String, default: '' },
  border: { type: Boolean, default: false },
  stripe: { type: Boolean, default: false },
  height: { type: [String, Number], default: undefined },
  maxHeight: { type: [String, Number], default: undefined },
  defaultSort: { type: Object, default: undefined },
  selectable: { type: Boolean, default: false },
  showIndex: { type: Boolean, default: false },
  actionsWidth: { type: [String, Number], default: undefined },

  emptyText: { type: String, default: '暂无数据' },
  emptyHint: { type: String, default: '' },

  page: { type: Number, default: 1 },
  pageSize: { type: Number, default: 10 },
  total: { type: Number, default: 0 },
  pageSizes: { type: Array, default: () => [10, 20, 50, 100] },
  showPagination: { type: Boolean, default: true }
})

const emit = defineEmits([
  'update:page',
  'update:pageSize',
  'update:selection',
  'change',
  'sort-change',
  'row-click'
])

const handlePageChange = (next) => {
  emit('update:page', next)
  emit('change', { page: next, pageSize: props.pageSize })
}

const handleSizeChange = (size) => {
  // 每页条数变化后停留在原页码可能越界，统一回到第一页
  emit('update:pageSize', size)
  emit('update:page', 1)
  emit('change', { page: 1, pageSize: size })
}
</script>

<style scoped>
.zy-table {
  display: flex;
  flex-direction: column;
  gap: var(--zy-space-3);
}

.zy-table__el {
  width: 100%;
}

.zy-table__actions {
  display: inline-flex;
  align-items: center;
  gap: var(--zy-space-1);
  justify-content: flex-end;
  white-space: nowrap;
}

.zy-table__empty {
  padding: var(--zy-space-10) 0;
  text-align: center;
}

.zy-table__empty-title {
  margin: 0;
  font-size: var(--zy-font-size-base);
  color: var(--zy-text-secondary);
}

.zy-table__empty-hint {
  margin: var(--zy-space-1) 0 0;
  font-size: var(--zy-font-size-sm);
  color: var(--zy-text-tertiary);
}

.zy-table__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--zy-space-3);
  flex-wrap: wrap;
}

.zy-table__count {
  font-size: var(--zy-font-size-sm);
  color: var(--zy-text-tertiary);
  font-variant-numeric: tabular-nums;
}

@media screen and (max-width: 768px) {
  .zy-table__footer {
    justify-content: center;
  }

  .zy-table__count {
    width: 100%;
    text-align: center;
  }
}
</style>
