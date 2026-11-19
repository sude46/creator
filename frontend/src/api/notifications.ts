import { request } from './http'
import type { Page } from './creator'
export type Notification = { id: string; type: 'REVIEW' | 'COMMENT' | 'FOLLOW'; title: string; body: string; link: string | null; readAt: string | null; createdAt: string }
export const notificationApi = {
  list: (page: number, signal: AbortSignal) => request<Page<Notification>>(`/notifications?page=${page}&size=20`, { signal }),
  unread: (signal: AbortSignal) => request<{ unread: number }>('/notifications/unread', { signal }),
  read: (id: string) => request<void>(`/notifications/${id}/read`, { method: 'POST' }),
  readAll: () => request<void>('/notifications/read-all', { method: 'POST' }),
}
