import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import { ROLES, ROLE_HOME, ROLE_PATH_PREFIX, roleOfPath } from '@/config/menus'

/**
 * 前端的角色隔离靠两件事：路由前缀 + 路由守卫。
 *
 * 之所以没有再引入按钮级的 v-role 指令，是因为每个页面都只挂在一个角色的
 * 路径前缀下，跨角色共用的组件（消息中心、通知、截止日历、智能体工作台）
 * 各自已用 props 或具名 computed 表达了差异。再加一层全局指令属于多余的抽象。
 *
 * 真正的拦截始终在后端 @RequireRole 与 ResourceAccessService，
 * 前端这层只负责不显示点了必然 403 的入口。
 */
const routerSource = readFileSync(resolve(__dirname, '../../router/index.js'), 'utf8')

describe('路由守卫的角色隔离', () => {
  it('守卫用统一的前缀映射判断归属，而不是散落的 if', () => {
    expect(routerSource).toContain('roleOfPath')
    expect(routerSource).toContain('ROLE_HOME')
  })

  it('三个角色的路径前缀互不包含，避免前缀匹配串台', () => {
    ROLES.forEach((a) => {
      ROLES.filter((b) => b !== a).forEach((b) => {
        expect(
          ROLE_PATH_PREFIX[a].startsWith(ROLE_PATH_PREFIX[b]),
          `${a} 与 ${b} 的路径前缀存在包含关系`
        ).toBe(false)
      })
    })
  })

  it('落地页与自身前缀一致，越权跳转不会再次被守卫拦下形成死循环', () => {
    ROLES.forEach((role) => {
      expect(roleOfPath(ROLE_HOME[role])).toBe(role)
    })
  })

  it('公开页面不属于任何角色，未登录也能访问', () => {
    const publicPaths = ['/', '/welcome', '/login']
    publicPaths.forEach((path) => expect(roleOfPath(path)).toBeNull())
  })
})
