import { request } from './http'
import type { Page, SignedMedia } from './creator'

export type PublicVideo = { id: string; authorId: string; authorName: string; categoryId: string; categoryName: string; title: string; description: string; durationMillis: number; width: number; height: number; publishedAt: string; hasCover: boolean; coverKey: string | null; viewCount: number; favoriteCount: number; likeCount: number }
export type Comment = { id: string; videoId: string; userId: string; username: string; content: string; createdAt: string }
export type AuthorProfile = { id: string; username: string; followerCount: number; followingCount: number; following: boolean; self: boolean }
export type PublicCollection = { id: string; ownerId: string; ownerName: string; title: string; description: string; visibility: string; version: number; itemCount: number; createdAt: string }
export type AnchorPage = { items: PublicVideo[]; anchorIndex: number | null; hasBefore: boolean; hasAfter: boolean }
export type Favorite = { videoId: string; createdAt: string; available: boolean; video: PublicVideo | null }
function read<Result>(path: string, signal: AbortSignal) { return request<Result>(path, { signal: AbortSignal.any([signal, AbortSignal.timeout(30000)]) }) }
export const publicApi = {
  list: (title: string, categoryId: string, page: number, signal: AbortSignal) => {
    const query = new URLSearchParams({ title, page: String(page), size: '12' })
    if (categoryId) query.set('categoryId', categoryId)
    return read<Page<PublicVideo>>(`/videos?${query}`, signal)
  },
  categories: (signal: AbortSignal) => read<Page<{ id: string; name: string; enabled: boolean }>>('/categories?page=1&size=50', signal),
  anchor: (anchorId: string, categoryId: string, before: number, after: number, signal: AbortSignal) => {
    const query = new URLSearchParams({ anchorId, before: String(before), after: String(after) })
    if (categoryId) query.set('categoryId', categoryId)
    return read<AnchorPage>(`/videos?${query}`, signal)
  },
  get: (id: string, signal: AbortSignal) => read<PublicVideo>(`/videos/${id}`, signal),
  media: async (id: string, kind: 'SOURCE' | 'COVER', signal: AbortSignal) => { const signed = await request<SignedMedia>(`/videos/${id}/media?kind=${kind}`, { method: 'POST', signal: AbortSignal.any([signal, AbortSignal.timeout(30000)]) }); const address = new URL(signed.url); if (!['https:', 'http:'].includes(address.protocol) || address.username || address.password) throw new Error('媒体地址无效'); return signed },
  favorites: (page: number, signal: AbortSignal) => read<Page<Favorite>>(`/favorites?page=${page}&size=12`, signal),
  favoriteState: (id: string, signal: AbortSignal) => read<{ favorited: boolean }>(`/favorites/${id}`, signal),
  favorite: (id: string, add: boolean) => request<void>(`/favorites/${id}`, { method: add ? 'PUT' : 'DELETE' }),
  like: (id: string, add: boolean) => request<void>(`/videos/${id}/like`, { method: add ? 'PUT' : 'DELETE' }),
  comments: (videoId: string, page: number, signal: AbortSignal) => read<Page<Comment>>(`/videos/${videoId}/comments?page=${page}&size=10`, signal),
  addComment: (videoId: string, content: string) => request<Comment>(`/videos/${videoId}/comments`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ content }) }),
  deleteComment: (id: string) => request<void>(`/comments/${id}`, { method: 'DELETE' }),
  likeState: (id: string, signal: AbortSignal) => read<{ liked: boolean }>(`/videos/${id}/like`, signal),
  view: (id: string) => request<{ viewCount: number }>(`/videos/${id}/view`, { method: 'POST' }),
  report: (targetType: 'VIDEO' | 'COMMENT', targetId: string, reason: string) => request<void>('/reports', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ targetType, targetId, reason }) }),
  following: (page: number, signal: AbortSignal) => read<Page<PublicVideo>>(`/videos/following?page=${page}&size=12`, signal),
  authorProfile: (authorId: string, signal: AbortSignal) => read<AuthorProfile>(`/authors/${authorId}/profile`, signal),
  follow: (authorId: string, add: boolean) => request<void>(`/authors/${authorId}/follow`, { method: add ? 'PUT' : 'DELETE' }),
  authorCollections: (authorId: string, signal: AbortSignal) => read<Page<PublicCollection>>(`/authors/${authorId}/collections?page=1&size=20`, signal),
  collection: (id: string, page: number, signal: AbortSignal) => read<{ collection: PublicCollection; items: Page<PublicVideo> }>(`/collections/${id}?page=${page}&size=12`, signal),
  authorVideos: (authorId: string, page: number, signal: AbortSignal) => read<Page<PublicVideo>>(`/authors/${authorId}/videos?page=${page}&size=12`, signal),
}
export function durationLabel(milliseconds: number) { const seconds = Math.floor(milliseconds / 1000); return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}` }
