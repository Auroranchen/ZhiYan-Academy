import { describe, expect, it } from 'vitest'
import {
  ROLES,
  ROLE_HOME,
  ROLE_PATH_PREFIX,
  flattenMenuPaths,
  getMenus,
  roleOfPath
} from '@/config/menus'

describe('菜单配置', () => {
  it('三端角色都有菜单', () => {
    expect(ROLES).toEqual(['admin', 'teacher', 'student'])
    ROLES.forEach((role) => expect(getMenus(role).length).toBeGreaterThan(0))
  })

  it('未知角色返回空数组而不是抛错', () => {
    expect(getMenus('guest')).toEqual([])
    expect(getMenus(undefined)).toEqual([])
  })

  it('菜单路径不会跨越到其他角色的地盘', () => {
    ROLES.forEach((role) => {
      const prefix = ROLE_PATH_PREFIX[role]
      flattenMenuPaths(role).forEach((path) => {
        expect(path.startsWith(prefix), `${role} 的菜单项 ${path} 越界`).toBe(true)
      })
    })
  })

  it('一级菜单项都有图标，二级不需要', () => {
    ROLES.forEach((role) => {
      getMenus(role).forEach((item) => {
        expect(item.icon, `${role} 的 ${item.label} 缺少图标`).toBeTruthy()
        expect(item.label).toBeTruthy()
      })
    })
  })

  it('有子菜单时父项路径应当是其中之一，点父项不会跳到空白页', () => {
    ROLES.forEach((role) => {
      getMenus(role)
        .filter((item) => item.children?.length)
        .forEach((item) => {
          const childPaths = item.children.map((c) => c.path)
          expect(childPaths, `${role} 的 ${item.label} 入口路径不在子菜单中`).toContain(item.path)
        })
    })
  })

  it('同一角色内不会出现重复的二级路径', () => {
    ROLES.forEach((role) => {
      const childPaths = getMenus(role).flatMap((item) => (item.children || []).map((c) => c.path))
      expect(new Set(childPaths).size, `${role} 存在重复菜单路径`).toBe(childPaths.length)
    })
  })

  it('每个角色的落地页都在自己的菜单里', () => {
    ROLES.forEach((role) => {
      expect(flattenMenuPaths(role)).toContain(ROLE_HOME[role])
    })
  })
})

describe('按路径反查角色', () => {
  it('识别三端路径前缀', () => {
    expect(roleOfPath('/admin/student')).toBe('admin')
    expect(roleOfPath('/teacher/exam')).toBe('teacher')
    expect(roleOfPath('/student/home')).toBe('student')
  })

  it('公共路径不归属任何角色，路由守卫据此放行', () => {
    expect(roleOfPath('/')).toBeNull()
    expect(roleOfPath('/login')).toBeNull()
  })
})
