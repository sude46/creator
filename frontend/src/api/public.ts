import { request } from './http'
import type { Page, SignedMedia } from './creator'

export type PublicVideo = { id: string; authorId: string; authorName: string; categoryId: string; categoryName: string; title: string; description: string; durationMillis: number; width: number; height: number; publishedAt: string; hasCover: boolean; coverKey: string | null }
export type Favorite = { videoId: string; createdAt: string; available: boolean; video: PublicVideo | null }
function read<Result>(path: string, signal: AbortSignal) { return request<Result>(path, { signal: AbortSignal.any([signal, AbortSignal.timeout(30000)]) }) }
export const publicApi = {
  list: (title: string, categoryId: string, page: number, signal: AbortSignal) => {
    const query = new URLSearchParams({ title, page: String(page), size: '12' })
    if (categoryId) query.set('categoryId', categoryId)
    return read<Page<PublicVideo>>(`/videos?${query}`, signal)
  },
  categories: (signal: AbortSignal) => read<Page<{ id: string; name: string; enabled: boolean }>>('/categories?page=1&size=50', signal),
  get: (id: string, signal: AbortSignal) => read<PublicVideo>(`/videos/${id}`, signal),
  media: async (id: string, kind: 'SOURCE' | 'COVER', signal: AbortSignal) => { const signed = await request<SignedMedia>(`/videos/${id}/media?kind=${kind}`, { method: 'POST', signal: AbortSignal.any([signal, AbortSignal.timeout(30000)]) }); const address = new URL(signed.url); if (!['https:', 'http:'].includes(address.protocol) || address.username || address.password) throw new Error('媒体地址无效'); return signed },
  favorites: (page: number, signal: AbortSignal) => read<Page<Favorite>>(`/favorites?page=${page}&size=12`, signal),
  favoriteState: (id: string, signal: AbortSignal) => read<{ favorited: boolean }>(`/favorites/${id}`, signal),
  favorite: (id: string, add: boolean) => request<void>(`/favorites/${id}`, { method: add ? 'PUT' : 'DELETE' }),
}
export function durationLabel(milliseconds: number) { const seconds = Math.floor(milliseconds / 1000); return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}` }
