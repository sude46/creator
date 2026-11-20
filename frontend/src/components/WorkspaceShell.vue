<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { auth } from '../stores/auth'
import { notificationState } from '../stores/notifications'
import { useStudioError } from '../composables/useStudioError'

const router = useRouter()
const route = useRoute()
const user = auth.user
const adminArea = computed(() => user.value?.role === 'ADMIN' && (route.path === '/admin' || route.path.startsWith('/admin/')))
const superAdmin = computed(() => user.value?.role === 'ADMIN' && (!user.value.adminScope || user.value.adminScope === 'SUPER_ADMIN'))
const canReview = computed(() => user.value?.role === 'ADMIN' && (!user.value.adminScope || ['SUPER_ADMIN', 'REVIEWER'].includes(user.value.adminScope)))
const canModerate = computed(() => user.value?.role === 'ADMIN' && (!user.value.adminScope || ['SUPER_ADMIN', 'MODERATOR'].includes(user.value.adminScope)))
const unread = notificationState.unread
let unreadTimer: ReturnType<typeof setInterval> | undefined
function refreshUnread() { if (user.value) void notificationState.refresh() }
watch(() => route.fullPath, refreshUnread)
watch(() => user.value?.id, id => { if (id) refreshUnread(); else notificationState.clear() })
const roleLabel = computed(() => !user.value || user.value.role !== 'ADMIN' ? '用户' : user.value.adminScope === 'REVIEWER' ? '审核员' : user.value.adminScope === 'MODERATOR' ? '内容管理员' : '超级管理员')
const busy = ref(false)
const error = ref('')
const shell = ref<HTMLElement>()
const explain = useStudioError()
function revealActiveNav() {
  const root = shell.value
  if (!root) return
  for (const nav of root.querySelectorAll<HTMLElement>('.workspace-nav, .admin-nav')) {
    if (nav.scrollWidth <= nav.clientWidth) continue
    const active = nav.querySelector<HTMLElement>('a.router-link-exact-active')
    if (!active) continue
    const navRect = nav.getBoundingClientRect()
    const activeRect = active.getBoundingClientRect()
    nav.scrollLeft += activeRect.left - navRect.left - (navRect.width - activeRect.width) / 2
  }
}
onMounted(() => { void nextTick(revealActiveNav); refreshUnread(); unreadTimer = setInterval(refreshUnread, 60_000) })
onBeforeUnmount(() => { if (unreadTimer) clearInterval(unreadTimer) })
async function logout() {
  if (busy.value) return
  busy.value = true
  try { await auth.logout(); notificationState.clear(); await router.push('/login') }
  catch (failure) { error.value = explain(failure) }
  finally { busy.value = false }
}
</script>

<template>
  <div ref="shell" class="workspace">
    <header class="workspace-header">
      <RouterLink class="brand" to="/">帧间<span>FRAME ROOM</span></RouterLink>
      <nav class="workspace-nav" aria-label="主导航"><RouterLink to="/">发现</RouterLink><RouterLink to="/favorites">我的收藏</RouterLink><RouterLink to="/history">观看历史</RouterLink><RouterLink to="/studio">我的作品</RouterLink><RouterLink to="/studio/analytics">数据</RouterLink><RouterLink to="/studio/collections">我的合集</RouterLink><RouterLink to="/notifications" class="notification-link" :aria-label="unread ? `通知，${unread} 条未读` : '通知'">通知<span v-if="unread" class="nav-badge" aria-hidden="true">{{ unread > 99 ? '99+' : unread }}</span></RouterLink><RouterLink to="/settings/profile">个人资料</RouterLink><RouterLink v-if="user?.role === 'ADMIN'" to="/admin">管理后台</RouterLink></nav>
      <div class="account-menu"><span>{{ user?.username }}</span><el-tag :type="user?.role === 'ADMIN' ? 'warning' : 'info'">{{ roleLabel }}</el-tag><el-button :loading="busy" @click="logout">退出登录</el-button></div>
    </header>
    <nav v-if="adminArea" class="admin-nav" aria-label="管理导航">
      <span class="admin-nav-group">概览</span><RouterLink to="/admin">总览</RouterLink>
      <span class="admin-nav-group">内容</span><RouterLink to="/admin/videos">作品管理</RouterLink><RouterLink v-if="canReview" to="/admin/reviews">审核</RouterLink><RouterLink v-if="canReview" to="/admin/processing">异常处理</RouterLink>
      <span v-if="superAdmin" class="admin-nav-group">配置</span><RouterLink v-if="superAdmin" to="/admin/categories">分类管理</RouterLink>
      <span class="admin-nav-group">系统</span><RouterLink v-if="superAdmin" to="/admin/users">用户管理</RouterLink><RouterLink v-if="canModerate" to="/admin/reports">举报处理</RouterLink><RouterLink v-if="superAdmin" to="/admin/audit">审计记录</RouterLink>
    </nav>
    <main class="studio-main">
      <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" />
      <slot />
    </main>
    <footer class="workspace-footer">帧间 · 每一帧，都值得被看见</footer>
  </div>
</template>

<style scoped>
.brand { flex: none; white-space: nowrap; }
.account-menu { flex: none; }
.workspace-nav { flex-wrap: wrap; }
.workspace-nav a { padding: 6px 10px; border-radius: 999px; }
.notification-link { display: inline-flex; align-items: center; gap: 6px; }
.nav-badge { display: inline-flex; align-items: center; justify-content: center; min-width: 18px; height: 18px; padding: 0 5px; border-radius: 999px; background: var(--color-accent); color: var(--color-surface); font-size: 10px; font-weight: 700; line-height: 1; }
.workspace-nav a.router-link-exact-active { background: var(--color-primary-soft); color: var(--color-primary); font-weight: 700; }
@media (max-width: 1180px) {
  .workspace-header { flex-wrap: wrap; gap: 10px 18px; }
  .workspace-nav { order: 3; width: 100%; flex-wrap: nowrap; overflow-x: auto; gap: 2px; padding: 2px 0 4px; scrollbar-width: none; }
  .workspace-nav::-webkit-scrollbar { display: none; }
  .workspace-nav a { display: inline-flex; align-items: center; min-height: 40px; padding: 0 12px; white-space: nowrap; }
}
.admin-nav { display: flex; flex-wrap: wrap; align-items: center; gap: 6px 16px; padding: 11px 28px; border-top: 1px solid #d9ddd5; border-bottom: 1px solid #d9ddd5; background: #fff; font-size: 13px; }
.admin-nav a { color: #4f5f56; }
.admin-nav a.router-link-exact-active { color: var(--color-accent-text); font-weight: 700; }
.admin-nav-group { color: var(--color-muted-soft); font-size: 11px; letter-spacing: .14em; margin-left: 8px; }
.admin-nav-group:first-child { margin-left: 0; }
.workspace-footer { border-top: 1px solid var(--color-border); padding: 22px 28px 30px; text-align: center; color: var(--color-muted-soft); font-size: 12px; letter-spacing: .08em; }
@media (max-width: 760px) {
  .admin-nav { flex-wrap: nowrap; overflow-x: auto; gap: 6px 14px; padding: 8px 16px; scrollbar-width: none; }
  .admin-nav::-webkit-scrollbar { display: none; }
  .admin-nav a { display: inline-flex; align-items: center; min-height: 40px; white-space: nowrap; }
  .admin-nav-group { white-space: nowrap; margin-left: 4px; }
}
</style>
