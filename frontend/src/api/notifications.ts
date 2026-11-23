import { request } from './http'
import type { Page } from './creator'
export type NotificationType = 'REVIEW' | 'COMMENT' | 'FOLLOW' | 'REPORT' | 'APPEAL' | 'MODERATION' | 'MENTION'
export type Notification = { id: string; type: NotificationType; title: string; body: string; link: string | null; readAt: string | null; createdAt: string }
export const notificationApi = {
  list: (page: number, unreadOnly: boolean, type: NotificationType | '', signal: AbortSignal) => {
    const query = new URLSearchParams({ page: String(page), size: '20', unreadOnly: String(unreadOnly) })
    if (type) query.set('type', type)
    return request<Page<Notification>>(`/notifications?${query}`, { signal })
  },
  unread: (signal: AbortSignal) => request<{ unread: number }>('/notifications/unread', { signal }),
  read: (id: string) => request<void>(`/notifications/${id}/read`, { method: 'POST' }),
  readAll: () => request<void>('/notifications/read-all', { method: 'POST' }),
  remove: (id: string) => request<void>(`/notifications/${id}`, { method: 'DELETE' }),
  clearRead: () => request<void>('/notifications/read', { method: 'DELETE' }),
}
