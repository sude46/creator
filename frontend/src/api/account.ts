import { request } from './http'

export type DeviceSession = { id: string; current: boolean; ip: string | null; userAgent: string | null; createdAt: string; lastSeenAt: string; expiresAt: string }
export const accountApi = {
  changePassword: (currentPassword: string, newPassword: string) => request<void>('/me/password', {
    method: 'PUT', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ currentPassword, newPassword }),
  }),
  sessions: (signal: AbortSignal) => request<{ items: DeviceSession[] }>('/me/sessions', {
    signal: AbortSignal.any([signal, AbortSignal.timeout(30000)]),
  }),
  revokeSession: (id: string) => request<void>(`/me/sessions/${encodeURIComponent(id)}`, { method: 'DELETE' }),
  revokeOtherSessions: () => request<void>('/me/sessions/others', { method: 'DELETE' }),
  revokeAllSessions: () => request<void>('/me/sessions/all', { method: 'DELETE' }),
}
