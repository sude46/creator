import { failureLabels } from './creator'
import type { CreatorVideo } from './creator'

export type StatusTone = 'success' | 'warning' | 'info' | 'danger' | 'primary'
export type StatusVisibility = 'PUBLIC' | 'PRIVATE' | 'BLOCKED'
export type VideoStatusKey =
  | 'BLOCKED'
  | 'NEEDS_UPLOAD'
  | 'PROCESSING'
  | 'PROCESS_FAILED'
  | 'READY_TO_SUBMIT'
  | 'PENDING_REVIEW'
  | 'REJECTED'
  | 'READY_TO_PUBLISH'
  | 'PUBLISHED'
  | 'OFFLINE'

export type VideoStatus = {
  key: VideoStatusKey
  label: string
  tone: StatusTone
  visibility: StatusVisibility
  visibilityLabel: string
  nextAction: string
  detail: string
}

type Copy = Pick<VideoStatus, 'label' | 'tone' | 'nextAction' | 'detail'>

const creatorCopy: Record<VideoStatusKey, Copy> = {
  BLOCKED: { label: '已封禁', tone: 'danger', nextAction: '查看处理原因', detail: '作品已被管理员封禁，请联系管理员了解处理原因。' },
  NEEDS_UPLOAD: { label: '待上传视频', tone: 'info', nextAction: '上传视频', detail: '作品信息已保存，选择 MP4 文件后开始处理。' },
  PROCESSING: { label: '处理中', tone: 'warning', nextAction: '等待处理完成', detail: '系统正在处理视频，页面会自动刷新状态。' },
  PROCESS_FAILED: { label: '处理失败', tone: 'danger', nextAction: '查看原因并重试', detail: '视频处理未完成，请检查失败原因后重试。' },
  READY_TO_SUBMIT: { label: '待提交审核', tone: 'info', nextAction: '提交审核', detail: '源视频已处理完成，可以提交管理员审核。' },
  PENDING_REVIEW: { label: '待审核', tone: 'warning', nextAction: '撤回审核', detail: '作品正在等待管理员审核，通过后才会公开。' },
  REJECTED: { label: '未通过', tone: 'danger', nextAction: '修改后重新提交', detail: '审核未通过，可查看审核记录并按说明修改。' },
  READY_TO_PUBLISH: { label: '待发布', tone: 'warning', nextAction: '再次发布', detail: '审核已通过，可以重新发布作品。' },
  PUBLISHED: { label: '已发布', tone: 'success', nextAction: '播放作品', detail: '作品已公开，其他用户可以浏览和播放。' },
  OFFLINE: { label: '已下架', tone: 'info', nextAction: '再次发布', detail: '作品已从公开展示中撤下，仅自己可见。' },
}

const adminCopy: Record<VideoStatusKey, Copy> = {
  BLOCKED: { label: '已封禁', tone: 'danger', nextAction: '查看处理记录', detail: '作品已被封禁，解禁后仍需作者重新送审。' },
  NEEDS_UPLOAD: { label: '待上传', tone: 'info', nextAction: '等待作者上传', detail: '作者尚未绑定源视频。' },
  PROCESSING: { label: '处理中', tone: 'warning', nextAction: '查看任务', detail: '媒体处理尚未结束。' },
  PROCESS_FAILED: { label: '处理失败', tone: 'danger', nextAction: '查看失败原因', detail: '媒体处理失败，可查看任务诊断或取消当前任务。' },
  READY_TO_SUBMIT: { label: '未送审', tone: 'info', nextAction: '不可审核', detail: '作品已处理完成，但作者尚未提交审核。' },
  PENDING_REVIEW: { label: '待审核', tone: 'warning', nextAction: '进入审核', detail: '作品等待管理员审核。' },
  REJECTED: { label: '已驳回', tone: 'danger', nextAction: '查看历史', detail: '当前轮次已驳回，等待作者修改后重新提交。' },
  READY_TO_PUBLISH: { label: '待发布', tone: 'warning', nextAction: '查看作品', detail: '审核已通过，但当前未公开。' },
  PUBLISHED: { label: '已发布', tone: 'success', nextAction: '查看公开页', detail: '作品当前公开可见。' },
  OFFLINE: { label: '已下架', tone: 'info', nextAction: '查看作品', detail: '作品当前未公开展示。' },
}

export function resolveVideoStatusKey(video: CreatorVideo): VideoStatusKey {
  if (video.moderationBlocked) return 'BLOCKED'
  if (!video.sourceUploaded) return 'NEEDS_UPLOAD'
  if (video.processingStatus === 'PENDING' || video.processingStatus === 'PROCESSING') return 'PROCESSING'
  if (video.processingStatus === 'FAILED') return 'PROCESS_FAILED'
  if (video.reviewStatus === 'PENDING') return 'PENDING_REVIEW'
  if (video.reviewStatus === 'REJECTED') return 'REJECTED'
  if (video.reviewStatus === 'APPROVED' && video.publicationStatus === 'PUBLISHED') return 'PUBLISHED'
  if (video.publicationStatus === 'OFFLINE') return 'OFFLINE'
  if (video.reviewStatus === 'APPROVED') return 'READY_TO_PUBLISH'
  return 'READY_TO_SUBMIT'
}

export function creatorStatus(video: CreatorVideo): VideoStatus {
  return status(video, creatorCopy)
}

export function adminStatus(video: CreatorVideo): VideoStatus {
  return status(video, adminCopy)
}

function status(video: CreatorVideo, copy: Record<VideoStatusKey, Copy>): VideoStatus {
  const key = resolveVideoStatusKey(video)
  const base = copy[key]
  const visibility: StatusVisibility = key === 'BLOCKED' ? 'BLOCKED' : key === 'PUBLISHED' ? 'PUBLIC' : 'PRIVATE'
  const visibilityLabel = visibility === 'PUBLIC' ? '公开' : visibility === 'BLOCKED' ? '已封禁' : '仅自己可见'
  const detail = key === 'PROCESS_FAILED'
    ? `${base.detail}${video.failureCode ? `（${failureLabels[video.failureCode] ?? video.failureCode}）` : ''}`
    : base.detail
  return { key, ...base, detail, visibility, visibilityLabel }
}
