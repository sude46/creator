import { request } from './http'
import type { CreatorVideo, Page, ReviewRecord, SignedMedia } from './creator'

export type ManagedVideo = { ownerId: string; video: CreatorVideo }
export type Decision = 'APPROVED' | 'REJECTED'
export type AdminModerationRecord = { id: string; actorId: string; action: string; reason: string; createdAt: string }

const boundedRead = <Result>(path: string, signal: AbortSignal) => request<Result>(path, { signal: AbortSignal.any([signal, AbortSignal.timeout(30000)]) })

export const adminApi = {
  queue: (page: number, signal?: AbortSignal) => request<Page<{ ownerId: string; video: CreatorVideo }>>(`/admin/reviews?page=${page}&size=20`, { signal }),
  pending: (id: string, signal?: AbortSignal) => request<CreatorVideo>(`/admin/reviews/${id}`, { signal }),
  decide: (video: CreatorVideo, outcome: Decision, reason: string) => request<CreatorVideo>(`/admin/reviews/${video.id}/decision`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ version: video.version, reviewRound: video.reviewRound, outcome, reason }),
  }),
  history: (id: string, page: number, signal?: AbortSignal) => request<Page<ReviewRecord>>(`/admin/reviews/${id}/history?page=${page}&size=20`, { signal }),
  managed: (page: number, signal?: AbortSignal) => request<Page<ManagedVideo>>(`/admin/videos?page=${page}&size=20`, { signal }),
  managedDetail: (id: string, signal: AbortSignal) => boundedRead<ManagedVideo>(`/admin/videos/${id}`, signal),
  moderationHistory: (id: string, page: number, signal: AbortSignal) => boundedRead<Page<AdminModerationRecord>>(`/admin/videos/${id}/moderation-history?page=${page}&size=20`, signal),
  block: (video: CreatorVideo, reason: string) => request<CreatorVideo>(`/admin/videos/${video.id}/block`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ version: video.version, reason }),
  }),
  unblock: (video: CreatorVideo, reason: string) => request<CreatorVideo>(`/admin/videos/${video.id}/unblock`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ version: video.version, reason }),
  }),
  preview: (video: CreatorVideo) => request<SignedMedia>(`/admin/reviews/${video.id}/preview?kind=SOURCE&version=${video.version}&reviewRound=${video.reviewRound}`, { method: 'POST' }),
}
