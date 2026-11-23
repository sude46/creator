import { request } from './http'
import type { CreatorVideo, Page, ReviewRecord, SignedMedia } from './creator'

export type ManagedVideo = { ownerId: string; video: CreatorVideo }
export type Decision = 'APPROVED' | 'REJECTED'
export type BatchReviewResult = { results: { videoId: string; ok: boolean; code: string | null; message: string }[]; succeeded: number; failed: number }
export type AdminModerationRecord = { id: string; actorId: string; action: string; reason: string; createdAt: string }
export type AdminUser = { id: string; username: string; role: 'USER' | 'ADMIN'; status: 'ACTIVE' | 'DISABLED'; adminScope: 'SUPER_ADMIN' | 'REVIEWER' | 'MODERATOR' | null }
export type AdminTag = { id: string; name: string; uses: number }
export type ContentReport = { id: string; reporterId: string; reporterName: string; targetType: 'VIDEO' | 'COMMENT'; targetId: string; reason: string; status: 'OPEN' | 'DISMISSED' | 'REMOVED'; resolverId: string | null; resolutionReason: string | null; createdAt: string; resolvedAt: string | null }
export type AuditRecord = { id: string; actorId: string; action: string; targetType: string; targetId: string; reason: string | null; requestId: string | null; createdAt: string }
export type HlsBackfillStatus = { enqueued: number; states: Record<string, number> }
export type AdminOverview = { pendingReviews: string; failedProcessing: string; todayUsers: string; todayDecisions: string; recentDecisions: { id: string; videoId: string; reviewerId: string; outcome: 'APPROVED' | 'REJECTED'; reason: string; title: string; createdAt: string }[] }

const boundedRead = <Result>(path: string, signal: AbortSignal) => request<Result>(path, { signal: AbortSignal.any([signal, AbortSignal.timeout(30000)]) })

export const adminApi = {
  overview: (signal: AbortSignal) => boundedRead<AdminOverview>('/admin/overview', signal),
  audit: (page: number, filters: { actorId?: string; action?: string; targetType?: string; from?: string; to?: string }, signal: AbortSignal) => {
    const query = new URLSearchParams({ page: String(page), size: '20' })
    for (const [key, value] of Object.entries(filters)) if (value) query.set(key, value)
    return boundedRead<Page<AuditRecord>>(`/admin/audit?${query}`, signal)
  },
  auditExport: (filters: { actorId?: string; action?: string; targetType?: string; from?: string; to?: string }) => {
    const query = new URLSearchParams()
    for (const [key, value] of Object.entries(filters)) if (value) query.set(key, value)
    window.location.assign(`/api/admin/audit/export?${query}`)
  },
  tags: (signal: AbortSignal) => boundedRead<AdminTag[]>('/admin/tags', signal),
  renameTag: (tag: AdminTag, name: string) => request<AdminTag>(`/admin/tags/${tag.id}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ name }) }),
  mergeTag: (tag: AdminTag, targetId: string) => request<void>(`/admin/tags/${tag.id}/merge`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ targetId }) }),
  deleteTag: (tag: AdminTag) => request<void>(`/admin/tags/${tag.id}`, { method: 'DELETE' }),
  reports: (status: string, page: number, signal: AbortSignal) => boundedRead<Page<ContentReport>>(`/admin/reports?status=${status}&page=${page}&size=20`, signal),
  resolveReport: (report: ContentReport, action: 'DISMISS' | 'REMOVE', reason: string) => request<void>(`/admin/reports/${report.id}/resolve`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ action, reason }) }),
  users: (page: number, signal: AbortSignal) => boundedRead<Page<AdminUser>>(`/admin/users?page=${page}&size=20`, signal),
  createUser: (username: string, password: string) => request<AdminUser>('/admin/users', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username, password }),
  }),
  resetPassword: (user: AdminUser, newPassword: string) => request<void>(`/admin/users/${user.id}/password`, {
    method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ newPassword }),
  }),
  setAdminScope: (user: AdminUser, adminScope: 'NONE' | 'REVIEWER' | 'MODERATOR') => request<AdminUser>(`/admin/users/${user.id}/admin-scope`, {
    method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ adminScope }),
  }),
  setUserStatus: (user: AdminUser, enabled: boolean) => request<AdminUser>(`/admin/users/${user.id}`, {
    method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ enabled }),
  }),
  queue: (page: number, signal: AbortSignal) => boundedRead<Page<ManagedVideo>>(`/admin/reviews?page=${page}&size=20`, signal),
  pending: (id: string, signal: AbortSignal) => boundedRead<CreatorVideo>(`/admin/reviews/${id}`, signal),
  batch: (items: { videoId: string; version: number; reviewRound: number }[], outcome: Decision, reason: string) => request<BatchReviewResult>('/admin/reviews/batch', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ items: items.map(item => ({ ...item, outcome, reason })) }),
  }),
  decide: (video: CreatorVideo, outcome: Decision, reason: string) => request<CreatorVideo>(`/admin/reviews/${video.id}/decision`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ version: video.version, reviewRound: video.reviewRound, outcome, reason }),
  }),
  history: (id: string, page: number, signal: AbortSignal) => boundedRead<Page<ReviewRecord>>(`/admin/reviews/${id}/history?page=${page}&size=20`, signal),
  managed: (page: number, signal?: AbortSignal) => request<Page<ManagedVideo>>(`/admin/videos?page=${page}&size=20`, { signal }),
  processing: (page: number, signal: AbortSignal) => boundedRead<Page<ManagedVideo>>(`/admin/videos/processing?page=${page}&size=20`, signal),
  hlsBackfillStatus: (signal: AbortSignal) => boundedRead<HlsBackfillStatus>('/admin/maintenance/hls-backfill', signal),
  hlsBackfill: (limit = 20, videoId?: string) => request<HlsBackfillStatus>(`/admin/maintenance/hls-backfill?limit=${limit}${videoId ? `&videoId=${videoId}` : ''}`, { method: 'POST' }),
  cancelProcessing: (video: CreatorVideo, reason: string) => request<CreatorVideo>(`/admin/videos/${video.id}/cancel-processing`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ version: video.version, reason }),
  }),
  cover: (video: CreatorVideo) => request<SignedMedia>(`/admin/reviews/${video.id}/preview?kind=COVER&version=${video.version}&reviewRound=${video.reviewRound}`, { method: 'POST' }),
  managedDetail: (id: string, signal: AbortSignal) => boundedRead<ManagedVideo>(`/admin/videos/${id}`, signal),
  moderationHistory: (id: string, page: number, signal: AbortSignal) => boundedRead<Page<AdminModerationRecord>>(`/admin/videos/${id}/moderation-history?page=${page}&size=20`, signal),
  block: (video: CreatorVideo, reason: string) => request<CreatorVideo>(`/admin/videos/${video.id}/block`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ version: video.version, reason }),
  }),
  unblock: (video: CreatorVideo, reason: string) => request<CreatorVideo>(`/admin/videos/${video.id}/unblock`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ version: video.version, reason }),
  }),
  remove: (video: CreatorVideo, reason: string) => request<void>(`/admin/videos/${video.id}/delete`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ version: video.version, reason }),
  }),
  download: (video: CreatorVideo) => request<SignedMedia>(`/admin/reviews/${video.id}/download?kind=SOURCE&version=${video.version}&reviewRound=${video.reviewRound}`, { method: 'POST' }),
  preview: async (video: CreatorVideo, signal: AbortSignal) => {
    const result = await request<SignedMedia>(`/admin/reviews/${video.id}/preview?kind=SOURCE&version=${video.version}&reviewRound=${video.reviewRound}`, {
      method: 'POST', signal: AbortSignal.any([signal, AbortSignal.timeout(30000)]),
    })
    const address = new URL(result.url)
    if (!['http:', 'https:'].includes(address.protocol) || address.username || address.password) throw new Error('预览地址无效')
    return result
  },
}
