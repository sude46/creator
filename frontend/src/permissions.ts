import type { User } from './stores/auth'

export type AdminAccess = 'review' | 'moderate' | 'super'

export function isAdmin(user: User | null): boolean {
  return user?.role === 'ADMIN'
}

export function isSuperAdmin(user: User | null): boolean {
  if (!user || user.role !== 'ADMIN') return false
  return !user.adminScope || user.adminScope === 'SUPER_ADMIN'
}

export function canReview(user: User | null): boolean {
  if (!user || user.role !== 'ADMIN') return false
  return !user.adminScope || user.adminScope === 'SUPER_ADMIN' || user.adminScope === 'REVIEWER'
}

export function canModerate(user: User | null): boolean {
  if (!user || user.role !== 'ADMIN') return false
  return !user.adminScope || user.adminScope === 'SUPER_ADMIN' || user.adminScope === 'MODERATOR'
}

export function hasAdminAccess(user: User | null, access: AdminAccess): boolean {
  if (access === 'review') return canReview(user)
  if (access === 'moderate') return canModerate(user)
  return isSuperAdmin(user)
}

export function adminRoleLabel(user: User | null): string {
  if (!isAdmin(user)) return '用户'
  if (user?.adminScope === 'REVIEWER') return '审核员'
  if (user?.adminScope === 'MODERATOR') return '内容管理员'
  return '超级管理员'
}
