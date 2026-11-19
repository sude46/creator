import { request } from './http'

export type Page<Result> = { items: Result[]; total: number; page: number; size: number }
export type Category = { id: string; name: string; enabled: boolean }
export type Metadata = { title: string; description: string; categoryId: string }
export type Draft = Metadata & { id: string; version: number }
export type CreatorVideo = Draft & {
  processingGeneration: number; sourceUploaded: boolean; moderationBlocked: boolean
  processingStatus: 'PENDING' | 'PROCESSING' | 'READY' | 'FAILED'
  reviewStatus: 'NOT_SUBMITTED' | 'PENDING' | 'APPROVED' | 'REJECTED'
  publicationStatus: 'UNPUBLISHED' | 'PUBLISHED' | 'OFFLINE'
  taskState: string | null; failureCode: string | null; executionAttempts: number
  nextAttemptAt: string | null; retryAllowed: boolean; durationMillis: number | null
  width: number | null; height: number | null; reviewRound: number
  reviewSubmittedAt: string | null; publishedAt: string | null
}
export type ReviewRecord = { id: string; reviewRound: number; outcome: string; reason: string; title: string; createdAt: string }
export type ModerationRecord = { id: string; action: string; reason: string; createdAt: string }
export type UploadState = { videoId: string; version: number; sourceUploaded: boolean; processingStatus: string }
export type SignedMedia = { url: string; expiresAt: string }
export type CreatorCommand = 'processing-retry' | 'review-submissions' | 'review-withdrawal' | 'offline' | 'publication'

const json = (method: string, body: unknown): RequestInit => ({ method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) })
const read = <Result>(path: string, signal?: AbortSignal) => request<Result>(path, {
  signal: signal ? AbortSignal.any([signal, AbortSignal.timeout(30000)]) : undefined,
})

export const creatorApi = {
  list: (page: number, signal?: AbortSignal) => read<Page<CreatorVideo>>(`/creator/videos?page=${page}&size=10`, signal),
  get: (id: string, signal?: AbortSignal) => read<CreatorVideo>(`/creator/videos/${id}`, signal),
  categories: (page: number, signal?: AbortSignal) => read<Page<Category>>(`/categories?page=${page}&size=20`, signal),
  create: (metadata: Metadata, requestKey: string) => request<Draft>('/creator/drafts', json('POST', { ...metadata, requestKey })),
  update: (video: CreatorVideo, metadata: Metadata) => request<Draft>(video.sourceUploaded ? `/creator/videos/${video.id}` : `/creator/drafts/${video.id}`,
    json('PUT', { ...metadata, version: video.version })),
  command: (video: CreatorVideo, command: CreatorCommand) => request<CreatorVideo>(`/creator/videos/${video.id}/${command}`, json('POST', { version: video.version })),
  upload: (video: CreatorVideo, file: File, signal: AbortSignal) => request<UploadState>(`/creator/videos/${video.id}/source`, {
    method: 'PUT', headers: { 'Content-Type': 'video/mp4', 'X-Video-Version': String(video.version) }, body: file, signal,
  }),
  uploadState: (id: string) => read<UploadState>(`/creator/videos/${id}/upload`),
  reviews: (id: string, page: number, signal?: AbortSignal) => read<Page<ReviewRecord>>(`/creator/videos/${id}/reviews?page=${page}&size=10`, signal),
  moderation: (id: string, page: number, signal?: AbortSignal) => read<Page<ModerationRecord>>(`/creator/videos/${id}/moderation-history?page=${page}&size=10`, signal),
  preview: (id: string) => request<SignedMedia>(`/creator/videos/${id}/preview?kind=SOURCE`, { method: 'POST' }),
  cover: (id: string) => request<SignedMedia>(`/creator/videos/${id}/preview?kind=COVER`, { method: 'POST' }),
  download: (id: string) => request<SignedMedia>(`/creator/videos/${id}/download?kind=SOURCE`, { method: 'POST' }),
  remove: (video: CreatorVideo) => request<void>(`/creator/videos/${video.id}?version=${video.version}`, { method: 'DELETE' }),
  cancelProcessing: (video: CreatorVideo) => request<CreatorVideo>(`/creator/videos/${video.id}/cancel-processing`, json('POST', { version: video.version })),
}

export const processingLabels: Record<CreatorVideo['processingStatus'], string> = { PENDING: '等待处理', PROCESSING: '正在处理', READY: '处理完成', FAILED: '处理失败' }
export const reviewLabels: Record<CreatorVideo['reviewStatus'], string> = { NOT_SUBMITTED: '未送审', PENDING: '审核中', APPROVED: '审核通过', REJECTED: '已驳回' }
export const publicationLabels: Record<CreatorVideo['publicationStatus'], string> = { UNPUBLISHED: '未发布', PUBLISHED: '已发布', OFFLINE: '已下架' }
export const failureLabels: Record<string, string> = {
  INVALID_MEDIA: '文件损坏或媒体信息无效', UNSUPPORTED_MEDIA: '编码、时长或分辨率不符合支持范围',
  STORAGE_UNAVAILABLE: '存储暂时不可用', PROCESS_FAILED: '媒体处理失败', PROCESS_TIMEOUT: '媒体处理超时',
  TEMP_UNAVAILABLE: '处理临时空间不可用', LEASE_EXPIRED: '处理任务执行超时',
}
export function formatTime(value: string | null) {
  if (!value) return '—'
  const time = new Date(/(?:Z|[+-]\d{2}:\d{2})$/.test(value) ? value : `${value}Z`)
  return Number.isNaN(time.getTime()) ? '—' : time.toLocaleString('zh-CN', { hour12: false })
}
