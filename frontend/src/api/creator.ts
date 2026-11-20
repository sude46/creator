import { ApiFailure, clearCsrf, csrfHeader, request } from './http'

export type Page<Result> = { items: Result[]; total: number; page: number; size: number }
export type Category = { id: string; name: string; enabled: boolean }
export type Metadata = { title: string; description: string; categoryId: string; tags: string[] }
export type Draft = Metadata & { id: string; version: number }
export type CreatorVideo = Draft & {
  processingGeneration: number; sourceUploaded: boolean; moderationBlocked: boolean
  processingStatus: 'PENDING' | 'PROCESSING' | 'READY' | 'FAILED'
  reviewStatus: 'NOT_SUBMITTED' | 'PENDING' | 'APPROVED' | 'REJECTED'
  publicationStatus: 'UNPUBLISHED' | 'PUBLISHED' | 'OFFLINE'
  taskState: string | null; failureCode: string | null; executionAttempts: number
  nextAttemptAt: string | null; retryAllowed: boolean; durationMillis: number | null
  width: number | null; height: number | null; reviewRound: number
  reviewSubmittedAt: string | null; publishedAt: string | null; hasCover: boolean; coverKey: string | null
  status: 'NEEDS_UPLOAD' | 'PROCESSING' | 'FAILED' | 'READY_TO_SUBMIT' | 'PENDING_REVIEW' | 'REJECTED' | 'PUBLISHED' | 'TAKEN_DOWN' | 'BLOCKED'
  statusReason: string | null
}
export type ReviewRecord = { id: string; reviewRound: number; outcome: string; reason: string; title: string; createdAt: string }
export type ModerationRecord = { id: string; action: string; reason: string; createdAt: string }
export type UploadState = { videoId: string; version: number; sourceUploaded: boolean; processingStatus: string }
export type SignedMedia = { url: string; expiresAt: string }
export type CollectionItem = { videoId: string; title: string; processingStatus: string; reviewStatus: string; publicationStatus: string; deleted: boolean; sortOrder: number }
export type Collection = { id: string; ownerId: string; ownerName: string; title: string; description: string; visibility: 'PUBLIC' | 'PRIVATE'; version: number; itemCount: number; createdAt: string }
export type UploadTarget = { id: string; version: number }
export type CreatorCommand = 'processing-retry' | 'review-submissions' | 'review-withdrawal' | 'offline' | 'publication'

const json = (method: string, body: unknown): RequestInit => ({ method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) })
function uploadWithProgress(video: UploadTarget, file: File, signal: AbortSignal, onProgress?: (percent: number) => void): Promise<UploadState> {
  return new Promise((resolve, reject) => {
    csrfHeader().then(csrf => {
      const xhr = new XMLHttpRequest()
      xhr.open('PUT', `/api/creator/videos/${video.id}/source`)
      xhr.setRequestHeader('Content-Type', 'video/mp4')
      xhr.setRequestHeader('X-Video-Version', String(video.version))
      xhr.setRequestHeader(csrf.headerName, csrf.token)
      const abort = () => xhr.abort()
      signal.addEventListener('abort', abort, { once: true })
      const finish = () => signal.removeEventListener('abort', abort)
      xhr.upload.onprogress = event => {
        if (event.lengthComputable) onProgress?.(Math.min(99, Math.round((event.loaded / event.total) * 100)))
      }
      xhr.onload = () => {
        finish()
        if (xhr.status >= 200 && xhr.status < 300) {
          onProgress?.(100)
          try { resolve(JSON.parse(xhr.responseText) as UploadState) }
          catch { reject(new ApiFailure(0, '上传响应无法解析，请查询上传状态')) }
          return
        }
        if (xhr.status === 403) clearCsrf()
        let body: { message?: string; code?: string } = {}
        try { body = JSON.parse(xhr.responseText) } catch { /* keep generic error */ }
        reject(new ApiFailure(xhr.status, body.message || '上传失败，请查询状态后重试', body.code))
      }
      xhr.onerror = () => { finish(); reject(new ApiFailure(0, '无法连接上传服务，请查询状态后重试')) }
      xhr.onabort = () => { finish(); reject(new DOMException('Upload aborted', 'AbortError')) }
      xhr.send(file)
    }).catch(reject)
  })
}
const read = <Result>(path: string, signal?: AbortSignal) => request<Result>(path, {
  signal: signal ? AbortSignal.any([signal, AbortSignal.timeout(30000)]) : undefined,
})

export const creatorApi = {
  list: (page: number, signal?: AbortSignal) => read<Page<CreatorVideo>>(`/creator/videos?page=${page}&size=10`, signal),
  get: (id: string, signal?: AbortSignal) => read<CreatorVideo>(`/creator/videos/${id}`, signal),
  tags: (id: string, signal?: AbortSignal) => read<{ tags: string[] }>(`/creator/videos/${id}/tags`, signal),
  categories: (page: number, signal?: AbortSignal) => read<Page<Category>>(`/categories?page=${page}&size=20`, signal),
  create: (metadata: Metadata, requestKey: string) => request<Draft>('/creator/drafts', json('POST', { ...metadata, requestKey })),
  update: (video: CreatorVideo, metadata: Metadata) => request<Draft>(video.sourceUploaded ? `/creator/videos/${video.id}` : `/creator/drafts/${video.id}`,
    json('PUT', { ...metadata, version: video.version })),
  command: (video: CreatorVideo, command: CreatorCommand) => request<CreatorVideo>(`/creator/videos/${video.id}/${command}`, json('POST', { version: video.version })),
  upload: (video: UploadTarget, file: File, signal: AbortSignal, onProgress?: (percent: number) => void) =>
    uploadWithProgress(video, file, signal, onProgress),
  uploadState: (id: string) => read<UploadState>(`/creator/videos/${id}/upload`),
  reviews: (id: string, page: number, signal?: AbortSignal) => read<Page<ReviewRecord>>(`/creator/videos/${id}/reviews?page=${page}&size=10`, signal),
  moderation: (id: string, page: number, signal?: AbortSignal) => read<Page<ModerationRecord>>(`/creator/videos/${id}/moderation-history?page=${page}&size=10`, signal),
  preview: (id: string) => request<SignedMedia>(`/creator/videos/${id}/preview?kind=SOURCE`, { method: 'POST' }),
  cover: (id: string) => request<SignedMedia>(`/creator/videos/${id}/preview?kind=COVER`, { method: 'POST' }),
  download: (id: string) => request<SignedMedia>(`/creator/videos/${id}/download?kind=SOURCE`, { method: 'POST' }),
  remove: (video: CreatorVideo) => request<void>(`/creator/videos/${video.id}?version=${video.version}`, { method: 'DELETE' }),
  exportVideos: () => window.location.assign('/api/creator/videos/export'),
  collections: (signal?: AbortSignal) => read<Page<Collection>>('/creator/collections?page=1&size=50', signal),
  createCollection: (title: string, description: string, visibility: 'PUBLIC' | 'PRIVATE') => request<Collection>('/creator/collections', json('POST', { title, description, visibility })),
  updateCollection: (collection: Collection, title: string, description: string, visibility: 'PUBLIC' | 'PRIVATE') => request<Collection>(`/creator/collections/${collection.id}`, json('PUT', { version: collection.version, title, description, visibility })),
  removeCollection: (collection: Collection) => request<void>(`/creator/collections/${collection.id}?version=${collection.version}`, { method: 'DELETE' }),
  collection: (id: string, signal?: AbortSignal) => read<Collection>(`/creator/collections/${id}`, signal),
  collectionItems: (id: string, signal?: AbortSignal) => read<CollectionItem[]>(`/creator/collections/${id}/videos`, signal),
  moveCollectionItem: (collectionId: string, videoId: string, direction: 'UP' | 'DOWN') => request<void>(`/creator/collections/${collectionId}/videos/${videoId}/move`, json('POST', { direction })),
  addToCollection: (collectionId: string, videoId: string) => request<void>(`/creator/collections/${collectionId}/items`, json('POST', { videoId })),
  removeFromCollection: (collectionId: string, videoId: string) => request<void>(`/creator/collections/${collectionId}/items/${videoId}`, { method: 'DELETE' }),
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
  return Number.isNaN(time.getTime()) ? '—' : time.toLocaleString('zh-CN', { hour12: false, timeZone: 'Asia/Shanghai' })
}
