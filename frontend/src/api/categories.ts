import { request } from './http'
import type { Page } from './creator'

export type ManagedCategory = { id: string; name: string; sortOrder: number; enabled: boolean; version: number }
export type CategoryInput = { name: string; sortOrder: number }
const json = (method: string, body: unknown): RequestInit => ({ method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) })

export const categoryApi = {
  list: (page: number, signal: AbortSignal) => request<Page<ManagedCategory>>(`/admin/categories?page=${page}&size=20`, {
    signal: AbortSignal.any([signal, AbortSignal.timeout(30000)]),
  }),
  create: (input: CategoryInput) => request<ManagedCategory>('/admin/categories', json('POST', input)),
  update: (category: ManagedCategory, input: CategoryInput, enabled: boolean) => request<ManagedCategory>(`/admin/categories/${category.id}`, json('PUT', { ...input, enabled, version: category.version })),
  remove: (category: ManagedCategory) => request<void>(`/admin/categories/${category.id}?version=${category.version}`, { method: 'DELETE' }),
}
