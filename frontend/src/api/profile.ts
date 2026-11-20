import { request } from './http'

export type ProfileForm = { displayName: string | null; bio: string; website: string; hasAvatar: boolean; version: number }

export const profileApi = {
  get: (signal?: AbortSignal) => request<ProfileForm>('/me/profile', { signal: signal ? AbortSignal.any([signal, AbortSignal.timeout(30000)]) : undefined }),
  update: (form: ProfileForm) => request<ProfileForm>('/me/profile', {
    method: 'PUT', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ version: form.version, displayName: form.displayName, bio: form.bio, website: form.website }),
  }),
  uploadAvatar: (file: File) => request<void>('/me/profile/avatar', { method: 'PUT', headers: { 'Content-Type': file.type }, body: file }),
  removeAvatar: () => request<void>('/me/profile/avatar', { method: 'DELETE' }),
}
