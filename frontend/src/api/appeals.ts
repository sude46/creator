import { request } from './http'
import type { Page } from './creator'

export type AppealStatus = 'OPEN' | 'ACCEPTED' | 'REJECTED'
export type Appeal = { id: string; appellantId: string; appellantName: string; targetType: 'VIDEO'; targetId: string; targetTitle: string | null; reason: string; status: AppealStatus; resolverId: string | null; resolutionReason: string | null; createdAt: string; resolvedAt: string | null }

export const appealApi = {
  create: (targetId: string, reason: string) => request<Appeal>('/appeals', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ targetType: 'VIDEO', targetId, reason }) }),
  mine: (targetId: string, signal?: AbortSignal) => request<Page<Appeal>>(`/appeals?targetType=VIDEO&targetId=${targetId}&page=1&size=20`, { signal: signal ? AbortSignal.any([signal, AbortSignal.timeout(30000)]) : undefined }),
  admin: (status: AppealStatus | '', page: number, signal: AbortSignal) => request<Page<Appeal>>(`/admin/appeals?page=${page}&size=20${status ? `&status=${status}` : ''}`, { signal }),
  resolve: (id: string, action: 'ACCEPT' | 'REJECT', reason: string) => request<void>(`/admin/appeals/${id}/resolve`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ action, reason }) }),
}
