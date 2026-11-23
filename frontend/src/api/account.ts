import { request } from './http'

export const accountApi = {
  changePassword: (currentPassword: string, newPassword: string) => request<void>('/me/password', {
    method: 'PUT', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ currentPassword, newPassword }),
  }),
}
