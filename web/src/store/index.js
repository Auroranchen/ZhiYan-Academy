/**
 * Vuex状态管理
 * 管理用户登录状态等全局数据
 */
import { createStore } from 'vuex'
import { resolveAvatarUrl } from '@/utils/avatar'

const readStoredUser = () => {
  try {
    return JSON.parse(localStorage.getItem('user')) || null
  } catch (e) {
    localStorage.removeItem('user')
    localStorage.removeItem('role')
    return null
  }
}

export default createStore({
  state: {
    // 用户信息（支持管理员、教师、学生）
    user: readStoredUser(),
    // 用户角色：admin, teacher, student
    role: localStorage.getItem('role') || null,
    // 当前页面的作业上下文（供AI助手使用；**仅作显示上下文，不承担安全职责**）
    homeworkContext: null,
    // 当前作业 ID：作业加固的依据，服务端据此查库判定是否已提交
    homeworkId: null,
    // 当前 AI 助手可检索的课程；服务端仍会重新校验用户是否有权访问
    aiCourseId: null
  },
  
  getters: {
    /**
     * 判断是否已登录
     */
    isLoggedIn: state => state.user !== null,
    
    /**
     * 获取用户信息
     */
    userInfo: state => state.user,
    
    /**
     * 获取用户角色
     */
    userRole: state => state.role,
    
    /**
     * 获取显示名称
     */
    displayName: state => {
      if (!state.user) return ''
      return state.user.realname || state.user.teachername || state.user.studentname || ''
    },
    
    /**
     * 获取管理员真实姓名（兼容旧代码）
     */
    realname: state => {
      if (!state.user) return ''
      return state.user.realname || state.user.username || '教务人员'
    },
    
    /**
     * 获取用户头像URL
     * 统一通过 API 接口获取头像，兼容所有部署环境
     */
    userAvatar: state => {
      if (!state.user || !state.user.avatar) return null
      const id = state.user.adminid || state.user.teacherid || state.user.studentid
      return resolveAvatarUrl(state.user.avatar, state.role, id) || null
    }
  },
  
  mutations: {
    /**
     * 设置用户信息（登录）
     */
    setUser(state, user) {
      state.user = user
      state.role = user.role || 'admin'
      localStorage.setItem('user', JSON.stringify(user))
      localStorage.setItem('role', state.role)
    },
    
    /**
     * 清除用户信息（退出登录）
     */
    logout(state) {
      state.user = null
      state.role = null
      localStorage.removeItem('user')
      localStorage.removeItem('role')
    },
    
    // 兼容旧代码
    SET_ADMIN(state, admin) {
      state.user = admin
      state.role = 'admin'
      localStorage.setItem('user', JSON.stringify(admin))
      localStorage.setItem('role', 'admin')
    },
    
    CLEAR_ADMIN(state) {
      state.user = null
      state.role = null
      localStorage.removeItem('user')
      localStorage.removeItem('role')
    },
    
    /**
     * 更新用户头像
     * 上传新头像后更新 state 和 localStorage
     */
    updateAvatar(state, avatar) {
      if (state.user) {
        state.user.avatar = avatar
        localStorage.setItem('user', JSON.stringify(state.user))
      }
    },

    setHomeworkContext(state, context) {
      state.homeworkContext = context
    },
    setHomeworkId(state, homeworkId) {
      const parsed = Number(homeworkId)
      state.homeworkId = Number.isInteger(parsed) && parsed > 0 ? parsed : null
    },
    setAiCourseId(state, courseId) {
      const parsed = Number(courseId)
      state.aiCourseId = Number.isInteger(parsed) && parsed > 0 ? parsed : null
    },
    clearHomeworkContext(state) {
      state.homeworkContext = null
      state.homeworkId = null
      state.aiCourseId = null
    }
  },
  
  actions: {
    /**
     * 登录操作
     */
    login({ commit }, user) {
      commit('setUser', user)
    },
    
    /**
     * 退出登录操作
     */
    logout({ commit }) {
      commit('logout')
    }
  }
})
