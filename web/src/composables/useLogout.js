/**
 * 退出登录
 *
 * 三个角色的布局各写了一遍「确认 → 调后端 → 清本地状态 → 跳登录页」，
 * 区别只在调哪个接口。逻辑收在这里，避免某一端漏掉清理步骤。
 */
import { useRouter } from 'vue-router'
import { useStore } from 'vuex'
import { ElMessage } from 'element-plus'
import { confirmOrAbort } from '@/utils/confirm'

/**
 * @param {() => Promise<unknown>} logoutRequest 对应角色的登出接口
 * @returns {{ logout: () => Promise<void> }}
 */
export function useLogout(logoutRequest) {
  const store = useStore()
  const router = useRouter()

  const logout = async () => {
    if (!(await confirmOrAbort('确定要退出登录吗？'))) return

    // 后端可能已经超时或会话早已失效，这里失败也必须继续清理本地状态，
    // 否则用户会卡在一个「看起来已登录、实际调不通接口」的状态里
    try {
      await logoutRequest()
    } catch (e) {
      console.error('注销会话失败，继续清理本地登录状态:', e)
    }

    store.dispatch('logout')
    ElMessage.success('退出成功')
    router.push('/login')
  }

  return { logout }
}
