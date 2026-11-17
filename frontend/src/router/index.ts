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
    { path: '/studio/:id', component: () => import('../views/CreatorVideoView.vue') },
    { path: '/admin/reviews', component: () => import('../views/AdminReviewView.vue') },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

router.beforeEach(async (to) => {
  try {
    await auth.load()
  } catch {
    if (!to.meta.guest) return '/login'
  }
  if (!to.meta.guest && !auth.user.value) return '/login'
  if (to.path.startsWith('/admin/') && auth.user.value?.role !== 'ADMIN') return '/'
  if (to.meta.guest && auth.user.value) return '/'
})
