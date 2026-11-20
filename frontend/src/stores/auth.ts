import { ref } from 'vue'
import { ApiFailure, clearCsrf, request } from '../api/http'

export type AdminScope = 'SUPER_ADMIN' | 'REVIEWER' | 'MODERATOR'
export type User = { id: string; username: string; role: 'USER' | 'ADMIN'; adminScope: AdminScope | null }
const user = ref<User | null>(null)
let loaded = false

async function load() {
  if (loaded) return
  try {
    user.value = await request<User>('/me')
    loaded = true
  } catch (error) {
    if (error instanceof ApiFailure && error.status === 401) {
      user.value = null
      loaded = true
    } else {
      throw error
    }
  }
}

async function login(username: string, password: string) {
  user.value = await request<User>('/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ username, password }),
  })
  loaded = true
  clearCsrf()
}

async function register(username: string, password: string) {
  await request<User>('/auth/register', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  })
}

async function logout() {
  await request<void>('/auth/logout', { method: 'POST' })
  user.value = null
  loaded = true
  clearCsrf()
}

function expire() {
  user.value = null
  loaded = true
  clearCsrf()
}

export const auth = { user, load, login, register, logout, expire }
