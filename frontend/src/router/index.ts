import { createRouter, createWebHistory } from 'vue-router'
import { auth } from '../stores/auth'
import { hasAdminAccess } from '../permissions'
import type { AdminAccess } from '../permissions'
import { safeRedirect } from './redirect'
import AuthView from '../views/AuthView.vue'
import WorkspaceView from '../views/WorkspaceView.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: AuthView, meta: { guest: true } },
    { path: '/register', component: AuthView, meta: { guest: true } },
    { path: '/', component: WorkspaceView, meta: { public: true } },
    { path: '/studio', component: () => import('../views/CreatorCenterView.vue') },
    { path: '/studio/analytics', component: () => import('../views/CreatorAnalyticsView.vue') },
    { path: '/studio/collections', component: () => import('../views/CollectionsView.vue') },
    { path: '/studio/collections/:id', component: () => import('../views/CollectionManageView.vue') },
    { path: '/studio/:id', component: () => import('../views/CreatorVideoView.vue') },
    { path: '/admin', component: () => import('../views/AdminOverviewView.vue') },
    { path: '/admin/reviews', component: () => import('../views/AdminReviewView.vue'), meta: { adminAccess: 'review' } },
    { path: '/admin/videos', component: () => import('../views/AdminVideosView.vue') },
    { path: '/admin/processing', component: () => import('../views/AdminProcessingView.vue'), meta: { adminAccess: 'review' } },
    { path: '/admin/tags', component: () => import('../views/AdminTagsView.vue'), meta: { adminAccess: 'super' } },
    { path: '/admin/categories', component: () => import('../views/AdminCategoriesView.vue'), meta: { adminAccess: 'super' } },
    { path: '/admin/users', component: () => import('../views/AdminUsersView.vue'), meta: { adminAccess: 'super' } },
    { path: '/admin/appeals', component: () => import('../views/AdminAppealsView.vue'), meta: { adminAccess: 'moderate' } },
    { path: '/admin/reports', component: () => import('../views/AdminReportsView.vue'), meta: { adminAccess: 'moderate' } },
    { path: '/admin/audit', component: () => import('../views/AdminAuditView.vue'), meta: { adminAccess: 'super' } },
    { path: '/feed/:id?', component: () => import('../views/FeedView.vue'), meta: { public: true } },
    { path: '/videos/:id', component: () => import('../views/PublicVideoView.vue'), meta: { public: true } },
    { path: '/authors/:id/followers', component: () => import('../views/AuthorConnectionsView.vue') },
    { path: '/authors/:id/following', component: () => import('../views/AuthorConnectionsView.vue') },
    { path: '/authors/:id', component: () => import('../views/AuthorView.vue'), meta: { public: true } },
    { path: '/collections/:id', component: () => import('../views/CollectionView.vue'), meta: { public: true } },
    { path: '/notifications', component: () => import('../views/NotificationsView.vue') },
    { path: '/settings/security', component: () => import('../views/SecuritySettingsView.vue') },
    { path: '/settings/profile', component: () => import('../views/ProfileSettingsView.vue') },
    { path: '/history', component: () => import('../views/HistoryView.vue') },
    { path: '/comments/:id/appeal', component: () => import('../views/CommentAppealView.vue') },
    { path: '/favorites', component: () => import('../views/FavoritesView.vue') },
    { path: '/:pathMatch(.*)*', component: () => import('../views/NotFoundView.vue') },
  ],
})

router.beforeEach(async (to) => {
  try {
    await auth.load()
  } catch {
    if (!to.meta.guest && !to.meta.public) return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (!to.meta.guest && !to.meta.public && !auth.user.value) return { path: '/login', query: { redirect: to.fullPath } }
  if (to.path === '/admin' || to.path.startsWith('/admin/')) {
    const user = auth.user.value
    const access = to.meta.adminAccess as AdminAccess | undefined
    if (user?.role !== 'ADMIN') return { path: '/', query: { denied: 'admin' } }
    if (access && !hasAdminAccess(user, access)) return { path: '/admin', query: { denied: access } }
  }
  if (to.meta.guest && auth.user.value) return safeRedirect(to.query.redirect) ?? '/'
})
