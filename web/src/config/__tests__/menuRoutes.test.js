import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import { ROLES, flattenMenuPaths } from '@/config/menus'

/**
 * 菜单配置和路由表是两份独立维护的清单，改错一处不会有任何报错，
 * 只是用户点进去看到空白页。这里直接从路由源码里抽出已注册的路径做比对。
 *
 * 不 import 路由模块本身：它会连带加载所有页面组件与 Element Plus，
 * 在 node 测试环境下跑不起来，而这里只需要路径字符串。
 */
const routerSource = readFileSync(resolve(__dirname, '../../router/index.js'), 'utf8')

const registeredPaths = new Set(
  [...routerSource.matchAll(/path:\s*'([^']+)'/g)].map(([, path]) => path)
)

describe('菜单路径与路由表一致', () => {
  it('路由表能被解析出足够多的路径，确保这个测试本身是有效的', () => {
    expect(registeredPaths.size).toBeGreaterThan(40)
  })

  ROLES.forEach((role) => {
    it(`${role} 端每个菜单项都有对应路由`, () => {
      const missing = flattenMenuPaths(role).filter((path) => !registeredPaths.has(path))
      expect(missing, `以下菜单项没有注册路由：${missing.join('、')}`).toEqual([])
    })
  })
})
