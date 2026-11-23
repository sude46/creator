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
export type SubtitleState = { exists: boolean; sizeBytes: number; updatedAt: string | null }
export type UploadSession = { uploadId: string; videoId: string; size: number; chunkSize: number; uploaded: number[]; receivedBytes: number; expiresAt: string }
export type SignedMedia = { url: string; expiresAt: string }
export type CreatorAnalytics = { videoCount: number; views: number; likes: number; favorites: number; comments: number; followers: number; from: string; to: string; daily: { date: string; views: number }[]; topVideos: { id: string; title: string; views: number; likes: number; favorites: number; comments: number }[] }
export type CollectionItem = { videoId: string; title: string; processingStatus: string; reviewStatus: string; publicationStatus: string; deleted: boolean; sortOrder: number }
export type Collection = { id: string; ownerId: string; ownerName: string; title: string; description: string; visibility: 'PUBLIC' | 'PRIVATE'; version: number; itemCount: number; createdAt: string }
export type UploadTarget = { id: string; version: number }
export type CreatorCommand = 'processing-retry' | 'review-submissions' | 'review-withdrawal' | 'offline' | 'publication'

const json = (method: string, body: unknown): RequestInit => ({ method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) })
function versionHeader(video: UploadTarget): Record<string, string> { return { 'X-Video-Version': String(video.version) } }
function abortError() { return new DOMException('Upload aborted', 'AbortError') }
function wait(ms: number, signal: AbortSignal) {
  return new Promise<void>((resolve, reject) => {
    if (signal.aborted) { reject(abortError()); return }
    const timer = setTimeout(() => { signal.removeEventListener('abort', cancel); resolve() }, ms)
    const cancel = () => { clearTimeout(timer); reject(abortError()) }
    signal.addEventListener('abort', cancel, { once: true })
  })
}
function uploadSession(video: UploadTarget, uploadId: string, signal?: AbortSignal): Promise<UploadSession> {
  return read<UploadSession>(`/creator/videos/${video.id}/source/uploads/${uploadId}`, signal)
}
function fileFingerprint(file: File) {
  const source = `${file.name}|${file.size}|${file.lastModified}`
  let hash = 2166136261
  for (let index = 0; index < source.length; index++) {
    hash ^= source.charCodeAt(index)
    hash = Math.imul(hash, 16777619)
  }
  return `${file.size.toString(16)}-${(hash >>> 0).toString(16)}`
}
function startUpload(video: UploadTarget, size: number, fingerprint: string, signal: AbortSignal): Promise<UploadSession> {
  return request<UploadSession>(`/creator/videos/${video.id}/source/uploads`,
    { ...json('POST', { size, fingerprint }), headers: { 'Content-Type': 'application/json', ...versionHeader(video) }, signal })
}
function completeUpload(video: UploadTarget, uploadId: string, signal: AbortSignal): Promise<UploadState> {
  return request<UploadState>(`/creator/videos/${video.id}/source/uploads/${uploadId}/complete`,
    { method: 'POST', headers: versionHeader(video), signal })
}
function putChunk(video: UploadTarget, session: UploadSession, index: number, body: Blob,
    signal: AbortSignal, onLoaded: (loaded: number) => void): Promise<void> {
  return new Promise((resolve, reject) => {
    csrfHeader().then(csrf => {
      const xhr = new XMLHttpRequest()
      xhr.open('PUT', `/api/creator/videos/${video.id}/source/uploads/${session.uploadId}/chunks/${index}`)
      xhr.timeout = 120000
      xhr.setRequestHeader('Content-Type', 'application/octet-stream')
      xhr.setRequestHeader(csrf.headerName, csrf.token)
      const abort = () => xhr.abort()
      signal.addEventListener('abort', abort, { once: true })
      const finish = () => signal.removeEventListener('abort', abort)
      xhr.upload.onprogress = event => onLoaded(event.loaded)
      xhr.onload = () => {
        finish()
        if (xhr.status >= 200 && xhr.status < 300) { resolve(); return }
        if (xhr.status === 403) clearCsrf()
        let payload: { message?: string; code?: string } = {}
        try { payload = JSON.parse(xhr.responseText) } catch { /* keep generic error */ }
        reject(new ApiFailure(xhr.status, payload.message || '分片上传失败，正在重试', payload.code))
      }
      xhr.onerror = () => { finish(); reject(new ApiFailure(0, '无法连接上传服务，正在重试')) }
      xhr.ontimeout = () => { finish(); reject(new ApiFailure(0, '分片上传超时，正在重试', 'UPLOAD_TIMEOUT')) }
      xhr.onabort = () => { finish(); reject(abortError()) }
      xhr.send(body)
    }).catch(reject)
  })
}
async function uploadWithProgress(video: UploadTarget, file: File, signal: AbortSignal,
    onProgress?: (percent: number) => void, onResume?: (receivedBytes: number) => void): Promise<UploadState> {
  const session = await startUpload(video, file.size, fileFingerprint(file), signal)
  if (signal.aborted) throw abortError()
  onResume?.(session.receivedBytes)
  const total = file.size
  const uploaded = new Set(session.uploaded)
  let received = session.receivedBytes
  const report = (extra = 0) => onProgress?.(Math.min(99, Math.floor(((received + extra) / total) * 100)))
  report()
  const count = Math.ceil(total / session.chunkSize)
  for (let index = 0; index < count; index++) {
    if (uploaded.has(index)) continue
    const start = index * session.chunkSize
    const end = Math.min(total, start + session.chunkSize)
    const body = file.slice(start, end)
    for (let attempt = 1; ; attempt++) {
      if (signal.aborted) throw abortError()
      try {
        await putChunk(video, session, index, body, signal, loaded => report(Math.min(loaded, end - start)))
        uploaded.add(index)
        received += end - start
        report()
        break
      } catch (failure) {
        if (signal.aborted || (failure instanceof DOMException && failure.name === 'AbortError')) throw failure
        const status = failure instanceof ApiFailure ? failure.status : 0
        if (status >= 400 && status < 500 && status !== 408 && status !== 429) throw failure
        if (attempt >= 3) throw failure
        await wait(500 * attempt, signal)
        try {
          const refreshed = await uploadSession(video, session.uploadId, signal)
          if (refreshed.uploaded.includes(index)) {
            uploaded.add(index)
            received = refreshed.receivedBytes
            break
          }
        } catch { /* retry the same chunk */ }
      }
    }
  }
  const state = await completeUpload(video, session.uploadId, signal)
  onProgress?.(100)
  return state
}
const read = <Result>(path: string, signal?: AbortSignal) => request<Result>(path, {
  signal: signal ? AbortSignal.any([signal, AbortSignal.timeout(30000)]) : undefined,
})

async function uploadSubtitleRequest(video: UploadTarget, file: File): Promise<SubtitleState> {
  const csrf = await csrfHeader()
  let response: Response
  try {
    response = await fetch(`/api/creator/videos/${video.id}/subtitle`, {
      method: 'PUT', credentials: 'same-origin', body: file,
      headers: { 'Content-Type': 'text/vtt', [csrf.headerName]: csrf.token },
    })
  } catch { throw new ApiFailure(0, '无法连接字幕服务，请稍后重试') }
  if (!response.ok) {
    if (response.status === 403) clearCsrf()
    const body = await response.json().catch(() => ({} as { message?: string; code?: string }))
    throw new ApiFailure(response.status, body.message || '字幕上传失败', body.code)
  }
  return response.json() as Promise<SubtitleState>
}
export const creatorApi = {
  list: (page: number, signal?: AbortSignal) => read<Page<CreatorVideo>>(`/creator/videos?page=${page}&size=10`, signal),
  get: (id: string, signal?: AbortSignal) => read<CreatorVideo>(`/creator/videos/${id}`, signal),
  tags: (id: string, signal?: AbortSignal) => read<{ tags: string[] }>(`/creator/videos/${id}/tags`, signal),
  categories: (page: number, signal?: AbortSignal) => read<Page<Category>>(`/categories?page=${page}&size=20`, signal),
  create: (metadata: Metadata, requestKey: string) => request<Draft>('/creator/drafts', json('POST', { ...metadata, requestKey })),
  update: (video: CreatorVideo, metadata: Metadata) => request<Draft>(video.sourceUploaded ? `/creator/videos/${video.id}` : `/creator/drafts/${video.id}`,
    json('PUT', { ...metadata, version: video.version })),
  command: (video: CreatorVideo, command: CreatorCommand) => request<CreatorVideo>(`/creator/videos/${video.id}/${command}`, json('POST', { version: video.version })),
  upload: (video: UploadTarget, file: File, signal: AbortSignal, onProgress?: (percent: number) => void,
    onResume?: (receivedBytes: number) => void) => uploadWithProgress(video, file, signal, onProgress, onResume),
  uploadState: (id: string) => read<UploadState>(`/creator/videos/${id}/upload`),
  subtitleState: (id: string, signal?: AbortSignal) => read<SubtitleState>(`/creator/videos/${id}/subtitle`, signal),
  uploadSubtitle: (video: UploadTarget, file: File) => uploadSubtitleRequest(video, file),
  removeSubtitle: (id: string) => request<void>(`/creator/videos/${id}/subtitle`, { method: 'DELETE' }),
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
  analytics: (days: number, signal?: AbortSignal) => read<CreatorAnalytics>(`/creator/analytics?days=${days}`, signal),
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
