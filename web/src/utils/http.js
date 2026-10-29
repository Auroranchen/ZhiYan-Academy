/**
 * HTTP请求封装
 * 基于Axios封装统一的请求方法 */
import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import store from '@/store'

// 创建axios实例
const http = axios.create({
  baseURL: '/ZhiYanAcademy',
  timeout: 30000,
  withCredentials: true, // 允许携带cookie
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN'
})

// 需要更长超时的 AI / 大模型相关接口（生成讲解、出题、评分、查重分析等耗时较长）
const AI_LONG_TIMEOUT = 180000 // 3 分钟
const AI_ENDPOINTS = ['/ai/', '/ai-insight', '/agent/', '/profile/chat', '/profile/evaluate', '/plagiarism/ai-detect', '/exam/ai', '/learnpath']

// 请求拦截器
http.interceptors.request.use(
  config => {
    // AI 类接口默认放宽超时，避免大模型响应慢导致前端「请求超时」
    if (config.timeout == null || config.timeout === 30000) {
      const url = config.url || ''
      if (AI_ENDPOINTS.some(p => url.includes(p))) {
        config.timeout = AI_LONG_TIMEOUT
      }
    }
    return config
  },
  error => {
    console.error('请求错误:', error)
    return Promise.reject(error)
  }
)

// 响应拦截器
http.interceptors.response.use(
  response => {
    // 如果是下载文件（blob），直接返回response对象，不做code校验
    if (response.config.responseType === 'blob') {
      return response
    }

    const res = response.data
    
    // 判断响应状态码
    if (res.code === 200) {
      return res
    } else if (res.code === 401) {
      // 未登录或登录过期
      if (!response.config?.suppressMessage) {
        ElMessage.error('请先登录')
      }
      store.dispatch('logout')
      router.push('/login')
      return Promise.reject(new Error(res.msg || '未授权'))
    } else {
      // 其他错误
      if (!response.config?.suppressMessage) {
        ElMessage.error(res.msg || '操作失败')
      }
      // 如果设置了suppressMessage，仍然reject以便调用方处理
      const err = new Error(res.msg || '操作失败')
      err.code = res.code
      err.msg = res.msg
      return Promise.reject(err)
    }
  },
  error => {
    // 用户主动取消生成时不弹出“请求失败”，由业务页面展示更准确的停止状态
    if (axios.isCancel(error) || error.code === 'ERR_CANCELED') {
      return Promise.reject(error)
    }
    console.error('响应错误:', error)
    
    // 网络错误处理
    if (error.message.includes('Network Error')) {
      if (!error.config?.suppressMessage) {
        ElMessage.error('网络连接失败，请检查网络')
      }
    } else if (error.message.includes('timeout')) {
      if (!error.config?.suppressMessage) {
        ElMessage.error('请求超时，请稍后重试')
      }
    } else if (error.response) {
      // 服务器返回错误状态码
      const status = error.response.status
      if (status === 401) {
        if (!error.config?.suppressMessage) {
          ElMessage.error('请先登录')
        }
        store.dispatch('logout')
        router.push('/login')
      } else if (status === 404) {
        if (!error.config?.suppressMessage) {
          ElMessage.error('请求的资源不存在')
        }
      } else if (status === 500) {
        if (!error.config?.suppressMessage) {
          ElMessage.error('服务器内部错误')
        }
      } else {
        if (!error.config?.suppressMessage) {
          ElMessage.error(`请求失败: ${status}`)
        }
      }
    } else {
      if (!error.config?.suppressMessage) {
        ElMessage.error('请求失败，请稍后重试')
      }
    }
    
    return Promise.reject(error)
  }
)

// POST请求方法
http.post = (url, data, config = {}) => {
  return http({
    method: 'post',
    url: url,
    data: data,
    ...config
  })
}

http.put = (url, data, config = {}) => {
  return http({
    method: 'put',
    url: url,
    data: data,
    ...config
  })
}

export default http
