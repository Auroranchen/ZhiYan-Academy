/**
 * 二次确认弹窗
 *
 * ElMessageBox.confirm 在用户取消时会 reject，直接 await 会让「取消」走进错误分支。
 * 各页面原先都要包一层 try/catch，这里统一收敛成返回布尔值。
 */
import { ElMessageBox } from 'element-plus'

/**
 * @param {string} message 说明文案，应写清操作影响
 * @param {string} [title]
 * @param {import('element-plus').ElMessageBoxOptions} [options]
 * @returns {Promise<boolean>} 用户确认返回 true，取消或关闭返回 false
 */
export const confirmOrAbort = async (message, title = '确认操作', options = {}) => {
  try {
    await ElMessageBox.confirm(message, title, { type: 'warning', ...options })
    return true
  } catch {
    return false
  }
}

/** 删除类操作的确认，按钮为红色以示危险 */
export const confirmDelete = (message, title = '确认删除') =>
  confirmOrAbort(message, title, { confirmButtonClass: 'el-button--danger' })
