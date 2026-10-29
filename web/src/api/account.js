/**
 * 账号通用操作
 *
 * 三种角色共用的部分放这里；角色专属的资料与密码接口仍在
 * student / teacher / system 各自的模块里。
 */
import http from '@/utils/http'

/** form 为包含 file 字段的 FormData，返回体带新的 avatarUrl */
export const uploadAvatar = (form) =>
  http.post('/avatar/upload', form, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
