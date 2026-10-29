/**
 * 列表页通用逻辑
 *
 * 分页查询、搜索、重置、删除确认这四件事在 20 多个列表页里各写了一遍，
 * 且错误处理与竞态处理各不相同。这里收敛成一份实现。
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

/**
 * @param {object} options
 * @param {(params: object) => Promise<any>} options.fetch 列表接口，接收合并后的查询参数
 * @param {(row: object) => Promise<any>} [options.remove] 删除接口
 * @param {object} [options.initialQuery] 除分页外的初始查询条件
 * @param {number} [options.pageSize]
 * @param {boolean} [options.immediate] 是否在挂载时自动加载，默认 true
 * @param {(row: object) => string} [options.confirmText] 删除确认文案
 */
export function useCrudTable(options) {
  const {
    fetch,
    remove: removeApi,
    initialQuery = {},
    pageSize = 10,
    immediate = true,
    confirmText = () => '确定要删除这条记录吗？删除后不可恢复。'
  } = options

  const rows = ref([])
  const total = ref(0)
  const loading = ref(false)
  const selection = ref([])

  const defaults = { ...initialQuery }
  const query = reactive({ page: 1, pageSize, ...initialQuery })

  // 快速连点搜索时，先发出的请求可能后返回，用序号丢弃过期响应
  let requestSeq = 0

  const load = async () => {
    const seq = ++requestSeq
    loading.value = true
    try {
      const res = await fetch({ ...query })
      if (seq !== requestSeq) return
      rows.value = res?.data || []
      total.value = res?.total ?? rows.value.length
    } catch {
      // http 拦截器已统一提示，这里只需保证列表回到可用状态
      if (seq === requestSeq) {
        rows.value = []
        total.value = 0
      }
    } finally {
      if (seq === requestSeq) loading.value = false
    }
  }

  const search = () => {
    query.page = 1
    return load()
  }

  const reset = () => {
    Object.assign(query, defaults)
    query.page = 1
    return load()
  }

  /** 删除后若当前页已空，自动回退一页，避免停在空列表上 */
  const remove = async (row) => {
    if (!removeApi) return
    try {
      await ElMessageBox.confirm(confirmText(row), '删除确认', {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        confirmButtonClass: 'el-button--danger'
      })
    } catch {
      return
    }

    try {
      await removeApi(row)
      ElMessage.success('删除成功')
      if (rows.value.length === 1 && query.page > 1) query.page -= 1
      await load()
    } catch {
      // 失败提示由 http 拦截器负责
    }
  }

  if (immediate) onMounted(load)

  return { rows, total, loading, selection, query, load, search, reset, remove }
}
