import { createRouter, createWebHistory } from 'vue-router'
import { auth } from '../stores/auth'
import AuthView from '../views/AuthView.vue'
import WorkspaceView from '../views/WorkspaceView.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: AuthView, meta: { guest: true } },
    { path: '/register', component: AuthView, meta: { guest: true } },
    { path: '/', component: WorkspaceView },
    { path: '/studio', component: () => import('../views/CreatorCenterView.vue') },
    { path: '/studio/collections', component: () => import('../views/CollectionsView.vue') },
    { path: '/studio/:id', component: () => import('../views/CreatorVideoView.vue') },
    { path: '/admin', component: () => import('../views/AdminOverviewView.vue') },
    { path: '/admin/reviews', component: () => import('../views/AdminReviewView.vue') },
    { path: '/admin/videos', component: () => import('../views/AdminVideosView.vue') },
    { path: '/admin/processing', component: () => import('../views/AdminProcessingView.vue') },
    { path: '/admin/categories', component: () => import('../views/AdminCategoriesView.vue') },
    { path: '/admin/users', component: () => import('../views/AdminUsersView.vue') },
    { path: '/admin/reports', component: () => import('../views/AdminReportsView.vue') },
    { path: '/admin/audit', component: () => import('../views/AdminAuditView.vue') },
    { path: '/feed/:id?', component: () => import('../views/FeedView.vue') },
    { path: '/videos/:id', component: () => import('../views/PublicVideoView.vue') },
    { path: '/authors/:id', component: () => import('../views/AuthorView.vue') },
    { path: '/collections/:id', component: () => import('../views/CollectionView.vue') },
    { path: '/notifications', component: () => import('../views/NotificationsView.vue') },
    { path: '/history', component: () => import('../views/HistoryView.vue') },
    { path: '/favorites', component: () => import('../views/FavoritesView.vue') },
    { path: '/:pathMatch(.*)*', component: () => import('../views/NotFoundView.vue') },
  ],
})

router.beforeEach(async (to) => {
  try {
    await auth.load()
  } catch {
    if (!to.meta.guest) return '/login'
  }
  if (!to.meta.guest && !auth.user.value) return '/login'
  if ((to.path === '/admin' || to.path.startsWith('/admin/')) && auth.user.value?.role !== 'ADMIN') return '/'
  if (to.meta.guest && auth.user.value) return '/'
})
