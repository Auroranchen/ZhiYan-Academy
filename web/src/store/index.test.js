import { describe, it, expect, beforeEach, vi } from 'vitest'

// Mock localStorage before importing the store
const localStorageMock = (() => {
  let store = {}
  return {
    getItem: vi.fn(key => store[key] || null),
    setItem: vi.fn((key, value) => { store[key] = value }),
    removeItem: vi.fn(key => { delete store[key] }),
    clear: vi.fn(() => { store = {} })
  }
})()

Object.defineProperty(globalThis, 'localStorage', { value: localStorageMock })

// Import store after localStorage mock is set up
const { default: store } = await import('./index.js')

describe('Vuex Store - updateAvatar mutation', () => {
  beforeEach(() => {
    localStorageMock.clear()
    vi.clearAllMocks()
    // Set up a logged-in user
    store.commit('setUser', {
      username: 'testuser',
      realname: '张三',
      role: 'student',
      avatar: null
    })
    vi.clearAllMocks() // Clear mocks from setUser call
  })

  it('should update user avatar in state', () => {
    store.commit('updateAvatar', '/upload/avatar/test.jpg')
    expect(store.state.user.avatar).toBe('/upload/avatar/test.jpg')
  })

  it('should persist updated avatar to localStorage', () => {
    store.commit('updateAvatar', '/upload/avatar/test.jpg')
    expect(localStorageMock.setItem).toHaveBeenCalledWith(
      'user',
      expect.stringContaining('/upload/avatar/test.jpg')
    )
    const stored = JSON.parse(localStorageMock.setItem.mock.calls[0][1])
    expect(stored.avatar).toBe('/upload/avatar/test.jpg')
  })

  it('should not throw when user is null', () => {
    store.commit('logout')
    vi.clearAllMocks()
    expect(() => store.commit('updateAvatar', '/upload/avatar/test.jpg')).not.toThrow()
    expect(localStorageMock.setItem).not.toHaveBeenCalled()
  })

  it('should overwrite existing avatar value', () => {
    store.commit('updateAvatar', '/upload/avatar/old.jpg')
    store.commit('updateAvatar', '/upload/avatar/new.jpg')
    expect(store.state.user.avatar).toBe('/upload/avatar/new.jpg')
  })

  it('should allow setting avatar to null', () => {
    store.commit('updateAvatar', '/upload/avatar/test.jpg')
    store.commit('updateAvatar', null)
    expect(store.state.user.avatar).toBeNull()
  })
})

describe('Vuex Store - userAvatar getter', () => {
  beforeEach(() => {
    localStorageMock.clear()
    vi.clearAllMocks()
  })

  it('should return avatar URL with API base prefix when user has avatar', () => {
    store.commit('setUser', {
      username: 'testuser',
      realname: '张三',
      role: 'student',
      studentid: 7,
      avatar: '/upload/avatar/test.jpg'
    })
    // getter 统一补全 API 前缀，兼容所有部署环境
    expect(store.getters.userAvatar).toBe('/ZhiYanAcademy/avatar/get/student/7')
  })

  it('should return null when user has no avatar', () => {
    store.commit('setUser', {
      username: 'testuser',
      realname: '张三',
      role: 'student',
      avatar: null
    })
    expect(store.getters.userAvatar).toBeNull()
  })

  it('should return null when user is not logged in', () => {
    store.commit('logout')
    expect(store.getters.userAvatar).toBeNull()
  })

  it('should return null when user object has no avatar field', () => {
    store.commit('setUser', {
      username: 'testuser',
      realname: '张三',
      role: 'student'
    })
    expect(store.getters.userAvatar).toBeNull()
  })
})
