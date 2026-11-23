<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { auth } from '../stores/auth'
import { notificationState } from '../stores/notifications'
import { profileApi, type ProfileForm } from '../api/profile'
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
watch(() => user.value?.id, id => { if (id) { refreshUnread(); void loadProfile() } else { notificationState.clear(); profile.value = null; menuOpen.value = false } })
const roleLabel = computed(() => !user.value || user.value.role !== 'ADMIN' ? '用户' : user.value.adminScope === 'REVIEWER' ? '审核员' : user.value.adminScope === 'MODERATOR' ? '内容管理员' : '超级管理员')
const busy = ref(false)
const error = ref('')
const shell = ref<HTMLElement>()
const account = ref<HTMLElement>()
const menuOpen = ref(false)
const profile = ref<ProfileForm | null>(null)
const displayName = computed(() => profile.value?.displayName?.trim() || user.value?.username || '')
const initial = computed(() => (displayName.value || '?').slice(0, 1).toUpperCase())
const avatarUrl = computed(() => user.value && profile.value?.hasAvatar
  ? `/api/authors/${user.value.id}/avatar?v=${profile.value.version}` : '')
const loginTarget = computed(() => ({ path: '/login', query: { redirect: route.fullPath } }))
const registerTarget = computed(() => ({ path: '/register', query: { redirect: route.fullPath } }))
const explain = useStudioError()
async function loadProfile() {
  if (!user.value) { profile.value = null; return }
  try { profile.value = await profileApi.get() } catch { profile.value = null }
}
function toggleMenu() { menuOpen.value = !menuOpen.value }
function closeMenu() { menuOpen.value = false }
function onDocumentClick(event: MouseEvent) {
  if (menuOpen.value && account.value && !account.value.contains(event.target as Node)) closeMenu()
}
function onDocumentKeydown(event: KeyboardEvent) { if (event.key === 'Escape') closeMenu() }
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
onMounted(() => {
  void nextTick(revealActiveNav)
  refreshUnread()
  void loadProfile()
  unreadTimer = setInterval(refreshUnread, 60_000)
  document.addEventListener('click', onDocumentClick)
  document.addEventListener('keydown', onDocumentKeydown)
})
onBeforeUnmount(() => {
  if (unreadTimer) clearInterval(unreadTimer)
  document.removeEventListener('click', onDocumentClick)
  document.removeEventListener('keydown', onDocumentKeydown)
})
async function logout() {
  if (busy.value) return
  busy.value = true
  try { await auth.logout(); notificationState.clear(); profile.value = null; menuOpen.value = false; await router.push('/login') }
  catch (failure) { error.value = explain(failure) }
  finally { busy.value = false }
}
</script>

<template>
  <div ref="shell" class="workspace">
    <header class="workspace-header">
      <RouterLink class="brand" to="/">帧间<span>FRAME ROOM</span></RouterLink>
      <nav class="workspace-nav" aria-label="主导航"><RouterLink to="/">发现</RouterLink><template v-if="user"><RouterLink to="/favorites">我的收藏</RouterLink><RouterLink to="/history">观看历史</RouterLink><RouterLink to="/studio">我的作品</RouterLink><RouterLink to="/studio/analytics">数据</RouterLink><RouterLink to="/studio/collections">我的合集</RouterLink><RouterLink to="/notifications" class="notification-link" :aria-label="unread ? `通知，${unread} 条未读` : '通知'">通知<span v-if="unread" class="nav-badge" aria-hidden="true">{{ unread > 99 ? '99+' : unread }}</span></RouterLink><RouterLink to="/settings/profile">个人资料</RouterLink><RouterLink to="/settings/security">账号安全</RouterLink><RouterLink v-if="user.role === 'ADMIN'" to="/admin">管理后台</RouterLink></template></nav>
      <div class="account-menu">
        <div v-if="user" ref="account" class="account">
          <button type="button" class="account-trigger" :aria-label="`账号菜单，${displayName}`" :aria-expanded="menuOpen" aria-haspopup="menu" @click="toggleMenu">
            <span class="avatar" aria-hidden="true"><img v-if="avatarUrl" :src="avatarUrl" alt="" /><span v-else>{{ initial }}</span></span>
            <span class="account-name">{{ displayName }}</span>
            <svg class="account-chevron" viewBox="0 0 12 12" width="12" height="12" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true"><path d="m3 4.5 3 3 3-3" /></svg>
          </button>
          <div v-if="menuOpen" class="account-panel" role="menu" aria-label="账号菜单">
            <div class="account-head">
              <span class="avatar avatar-lg" aria-hidden="true"><img v-if="avatarUrl" :src="avatarUrl" alt="" /><span v-else>{{ initial }}</span></span>
              <div class="account-identity"><strong>{{ displayName }}</strong><span>@{{ user.username }}</span></div>
            </div>
            <div class="account-meta"><el-tag :type="user.role === 'ADMIN' ? 'warning' : 'info'">{{ roleLabel }}</el-tag></div>
            <div class="account-links">
              <RouterLink to="/settings/profile" role="menuitem" @click="closeMenu">个人资料</RouterLink>
              <RouterLink to="/settings/security" role="menuitem" @click="closeMenu">账号安全</RouterLink>
              <RouterLink to="/studio" role="menuitem" @click="closeMenu">我的作品</RouterLink>
              <RouterLink v-if="user.role === 'ADMIN'" to="/admin" role="menuitem" @click="closeMenu">管理后台</RouterLink>
            </div>
            <button type="button" class="account-logout" role="menuitem" :disabled="busy" @click="logout">{{ busy ? '正在退出…' : '退出登录' }}</button>
          </div>
        </div>
        <template v-else>
          <RouterLink class="account-login" :to="loginTarget">登录</RouterLink>
          <RouterLink class="account-register" :to="registerTarget">注册</RouterLink>
        </template>
      </div>
    </header>
    <nav v-if="adminArea" class="admin-nav" aria-label="管理导航">
      <span class="admin-nav-group">概览</span><RouterLink to="/admin">总览</RouterLink>
      <span class="admin-nav-group">内容</span><RouterLink to="/admin/videos">作品管理</RouterLink><RouterLink v-if="canReview" to="/admin/reviews">审核</RouterLink><RouterLink v-if="canReview" to="/admin/processing">异常处理</RouterLink>
      <span v-if="superAdmin" class="admin-nav-group">配置</span><RouterLink v-if="superAdmin" to="/admin/categories">分类管理</RouterLink><RouterLink v-if="superAdmin" to="/admin/tags">标签管理</RouterLink>
      <span class="admin-nav-group">系统</span><RouterLink v-if="superAdmin" to="/admin/users">用户管理</RouterLink><RouterLink v-if="canModerate" to="/admin/reports">举报处理</RouterLink><RouterLink v-if="canModerate" to="/admin/appeals">申诉处理</RouterLink><RouterLink v-if="superAdmin" to="/admin/audit">审计记录</RouterLink>
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

.account { position: relative; }
.account-trigger { display: inline-flex; align-items: center; gap: 8px; padding: 4px 8px 4px 4px; border: 1px solid transparent; border-radius: 999px; background: none; color: var(--color-text); font: inherit; font-size: 14px; cursor: pointer; }
.account-trigger:hover { background: var(--color-hover); border-color: var(--color-border-soft); }
.account-trigger:focus-visible { outline: 2px solid var(--color-focus); outline-offset: 2px; }
.avatar { display: grid; place-items: center; width: 30px; height: 30px; flex: none; overflow: hidden; border: 1px solid var(--color-border); border-radius: 50%; background: var(--color-primary-soft); color: var(--color-primary); font-size: 13px; font-weight: 600; }
.avatar img { display: block; width: 100%; height: 100%; object-fit: cover; }
.avatar-lg { width: 44px; height: 44px; font-size: 17px; }
.account-name { max-width: 120px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.account-chevron { flex: none; color: var(--color-muted); }
.account-panel { position: absolute; top: calc(100% + 8px); right: 0; z-index: 30; width: 248px; padding: 14px; border: 1px solid var(--color-border); border-radius: 4px; background: var(--color-surface); box-shadow: 0 12px 30px rgba(28,40,34,.12); }
.account-head { display: flex; align-items: center; gap: 10px; min-width: 0; }
.account-identity { display: grid; gap: 2px; min-width: 0; }
.account-identity strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 14px; }
.account-identity span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 12px; color: var(--color-muted); }
.account-meta { margin: 12px 0 8px; }
.account-links { display: grid; gap: 2px; margin-top: 8px; padding-top: 8px; border-top: 1px solid var(--color-border-soft); }
.account-links a { padding: 7px 8px; border-radius: 3px; color: var(--color-text); font-size: 13px; }
.account-links a:hover { background: var(--color-hover); color: var(--color-primary); }
.account-links a:focus-visible { outline: 2px solid var(--color-focus); outline-offset: 1px; }
.account-logout { width: 100%; margin-top: 6px; padding: 8px; border: 0; border-top: 1px solid var(--color-border-soft); border-radius: 0; background: none; color: var(--color-danger); font: inherit; font-size: 13px; text-align: left; cursor: pointer; }
.account-logout:hover { color: color-mix(in srgb, var(--color-danger) 80%, #000); }
.account-logout:disabled { color: var(--color-muted); cursor: wait; }
.account-logout:focus-visible { outline: 2px solid var(--color-focus); outline-offset: -2px; }
.account-login, .account-register { display: inline-flex; align-items: center; min-height: 36px; padding: 0 15px; border-radius: 999px; font-size: 13px; font-weight: 600; }
.account-login { background: var(--color-primary); color: var(--color-surface); }
.account-login:hover { background: var(--color-primary-dark); }
.account-register { border: 1px solid var(--color-border); color: var(--color-secondary); }
.account-register:hover { border-color: var(--color-primary); background: var(--color-primary-soft); color: var(--color-primary); }
.account-login:focus-visible, .account-register:focus-visible { outline: 2px solid var(--color-focus); outline-offset: 2px; }
@media (max-width: 760px) {
  .account-name, .account-chevron { display: none; }
  .account-trigger { padding: 3px; }
  .account-panel { width: 232px; }
}
</style>
