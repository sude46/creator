import type { CreatorVideo } from './creator'

export type StatusTone = 'success' | 'warning' | 'info' | 'danger' | 'primary'
export type StatusVisibility = 'PUBLIC' | 'PRIVATE' | 'BLOCKED'
export type VideoStatusKey = CreatorVideo['status']

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
  NEEDS_UPLOAD: { label: '待上传视频', tone: 'info', nextAction: '上传视频', detail: '作品信息已保存，选择 MP4 文件后开始处理。' },
  PROCESSING: { label: '处理中', tone: 'warning', nextAction: '等待处理完成', detail: '处理中，预计 1～2 分钟；页面会自动刷新。' },
  FAILED: { label: '处理失败', tone: 'danger', nextAction: '重新处理', detail: '处理失败，请查看原因后重试。' },
  READY_TO_SUBMIT: { label: '待提交审核', tone: 'info', nextAction: '提交审核', detail: '视频已处理完成，可以提交审核。' },
  PENDING_REVIEW: { label: '待审核', tone: 'warning', nextAction: '撤回审核', detail: '作品正在等待审核，通过后会公开。' },
  REJECTED: { label: '未通过', tone: 'danger', nextAction: '修改后重新提交', detail: '审核未通过，请按说明修改。' },
  PUBLISHED: { label: '已发布', tone: 'success', nextAction: '播放我的作品', detail: '作品已公开，其他用户可以浏览和播放。' },
  TAKEN_DOWN: { label: '已下架', tone: 'info', nextAction: '—', detail: '作品已从公开展示中撤下。' },
  BLOCKED: { label: '已封禁', tone: 'danger', nextAction: '查看处理原因', detail: '作品已被管理员封禁，请联系管理员了解原因。' },
}

const adminCopy: Record<VideoStatusKey, Copy> = {
  NEEDS_UPLOAD: { label: '待上传', tone: 'info', nextAction: '等待作者上传', detail: '作者尚未绑定源视频。' },
  PROCESSING: { label: '处理中', tone: 'warning', nextAction: '查看任务', detail: '媒体处理尚未结束。' },
  FAILED: { label: '处理失败', tone: 'danger', nextAction: '查看失败原因', detail: '媒体处理失败。' },
  READY_TO_SUBMIT: { label: '未送审', tone: 'info', nextAction: '不可审核', detail: '作品已处理完成，但作者尚未提交审核。' },
  PENDING_REVIEW: { label: '待审核', tone: 'warning', nextAction: '进入审核', detail: '作品等待管理员审核。' },
  REJECTED: { label: '已驳回', tone: 'danger', nextAction: '查看历史', detail: '当前轮次已驳回。' },
  PUBLISHED: { label: '已发布', tone: 'success', nextAction: '查看公开页', detail: '作品当前公开可见。' },
  TAKEN_DOWN: { label: '已下架', tone: 'info', nextAction: '查看作品', detail: '作品当前未公开展示。' },
  BLOCKED: { label: '已封禁', tone: 'danger', nextAction: '查看处理记录', detail: '解禁后仍需作者重新送审。' },
}

export function creatorStatus(video: CreatorVideo): VideoStatus {
  return status(video, creatorCopy)
}

export function adminStatus(video: CreatorVideo): VideoStatus {
  return status(video, adminCopy)
}

function status(video: CreatorVideo, copy: Record<VideoStatusKey, Copy>): VideoStatus {
  const key = video.status
  const base = copy[key]
  const visibility: StatusVisibility = key === 'BLOCKED' ? 'BLOCKED' : key === 'PUBLISHED' ? 'PUBLIC' : 'PRIVATE'
  const visibilityLabel = visibility === 'PUBLIC' ? '公开' : visibility === 'BLOCKED' ? '已封禁' : '仅自己可见'
  const detail = key === 'REJECTED' && video.statusReason
    ? `未通过：${video.statusReason}`
    : key === 'FAILED' && video.statusReason
      ? `处理失败：${video.statusReason}`
      : base.detail
  return { key, ...base, detail, visibility, visibilityLabel }
}
