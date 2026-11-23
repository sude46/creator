<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import WorkspaceShell from '../components/WorkspaceShell.vue'
import { accountApi } from '../api/account'
import type { DeviceSession } from '../api/account'
import { formatTime } from '../api/creator'
import { auth } from '../stores/auth'
import { useStudioError } from '../composables/useStudioError'

const router = useRouter()
const explain = useStudioError()
const form = reactive({ current: '', next: '', confirm: '' })
const busy = ref(false)
const error = ref('')
const sessions = ref<DeviceSession[]>([])
const sessionsLoading = ref(false)
const sessionsBusy = ref(false)
const sessionsError = ref('')
let stopped = false
let sessionRequest: AbortController | undefined

async function submit() {
  error.value = ''
  if (busy.value) return
  if (form.next !== form.confirm) { error.value = '两次输入的新密码不一致。'; return }
  if (form.next.length < 10) { error.value = '新密码至少 10 位。'; return }
  if (form.current === form.next) { error.value = '新密码不能与当前密码相同。'; return }
  busy.value = true
  try {
    await accountApi.changePassword(form.current, form.next)
    if (stopped) return
    auth.expire()
    await router.push({ path: '/login', query: { changed: '1' } })
  } catch (failure) { if (!stopped) error.value = explain(failure) }
  finally { if (!stopped) busy.value = false }
}

async function loadSessions() {
  sessionRequest?.abort()
  const controller = new AbortController()
  sessionRequest = controller
  sessionsLoading.value = true
  sessionsError.value = ''
  try {
    const result = await accountApi.sessions(controller.signal)
    if (!stopped && !controller.signal.aborted) sessions.value = result.items
  } catch (failure) { if (!stopped && !controller.signal.aborted) sessionsError.value = explain(failure) }
  finally { if (!stopped && !controller.signal.aborted) sessionsLoading.value = false }
}
async function revokeSession(session: DeviceSession) {
  if (sessionsBusy.value) return
  if (!window.confirm(session.current ? '退出当前设备后需要重新登录。确认退出？' : '确认退出该设备？')) return
  sessionsBusy.value = true
  sessionsError.value = ''
  try {
    await accountApi.revokeSession(session.id)
    if (session.current) { auth.expire(); await router.push({ path: '/login', query: { revoked: '1' } }); return }
    await loadSessions()
  } catch (failure) { if (!stopped) sessionsError.value = explain(failure) }
  finally { if (!stopped) sessionsBusy.value = false }
}
async function revokeOtherSessions() {
  if (sessionsBusy.value) return
  if (!window.confirm('确认退出除当前设备以外的所有登录设备？')) return
  sessionsBusy.value = true
  sessionsError.value = ''
  try { await accountApi.revokeOtherSessions(); await loadSessions() }
  catch (failure) { if (!stopped) sessionsError.value = explain(failure) }
  finally { if (!stopped) sessionsBusy.value = false }
}
async function revokeAllSessions() {
  if (sessionsBusy.value) return
  if (!window.confirm('退出全部设备后，所有浏览器都需要重新登录。确认继续？')) return
  sessionsBusy.value = true
  sessionsError.value = ''
  try {
    await accountApi.revokeAllSessions()
    auth.expire()
    await router.push({ path: '/login', query: { revoked: '1' } })
  } catch (failure) { if (!stopped) sessionsError.value = explain(failure) }
  finally { if (!stopped) sessionsBusy.value = false }
}
function deviceLabel(userAgent: string | null) {
  if (!userAgent) return '未知设备'
  if (/Edg\//.test(userAgent)) return 'Microsoft Edge'
  if (/Firefox\//.test(userAgent)) return 'Firefox'
  if (/Chrome\//.test(userAgent)) return 'Chrome'
  if (/Safari\//.test(userAgent)) return 'Safari'
  return '浏览器'
}
onMounted(() => { void loadSessions() })
onBeforeUnmount(() => { stopped = true; sessionRequest?.abort() })
</script>

<template>
  <WorkspaceShell>
    <div class="studio-heading"><div><p class="eyebrow">FRAME ROOM / SECURITY</p><h1>账号安全</h1><p class="muted">修改密码会使所有设备上的既有会话失效，需要重新登录。</p></div><RouterLink class="create-link" to="/settings/profile">个人资料</RouterLink></div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" />
    <section class="studio-panel security-panel">
      <h2>修改密码</h2>
      <form class="studio-form" @submit.prevent="submit">
        <label for="current-password">当前密码</label>
        <input id="current-password" v-model="form.current" type="password" autocomplete="current-password" maxlength="64" required />
        <label for="new-password">新密码</label>
        <input id="new-password" v-model="form.next" type="password" autocomplete="new-password" maxlength="64" required />
        <p class="muted">10～64 位，至少包含一个字母和一个数字。</p>
        <label for="confirm-password">确认新密码</label>
        <input id="confirm-password" v-model="form.confirm" type="password" autocomplete="new-password" maxlength="64" required />
        <div class="inline-actions"><el-button type="primary" native-type="submit" :loading="busy">修改密码</el-button><RouterLink class="create-link" to="/settings/profile">取消</RouterLink></div>
      </form>
    </section>
    <section class="studio-panel security-panel">
      <div class="section-heading"><h2>登录设备 <span class="count">{{ sessions.length }}</span></h2><div class="inline-actions"><el-button :loading="sessionsLoading" :disabled="sessionsBusy" @click="loadSessions">刷新</el-button><el-button :disabled="sessionsBusy || sessions.length < 2" @click="revokeOtherSessions">退出其他设备</el-button><el-button type="danger" :disabled="sessionsBusy || !sessions.length" @click="revokeAllSessions">退出全部设备</el-button></div></div>
      <p class="muted">这里只列出当前仍有效、未过期的登录会话；最近活动超过 30 分钟会自动失效。</p>
      <el-alert v-if="sessionsError" :title="sessionsError" type="error" :closable="false" role="alert" />
      <p v-if="sessionsLoading" class="muted" role="status">正在读取登录设备…</p>
      <ul v-else-if="sessions.length" class="session-list">
        <li v-for="session in sessions" :key="session.id">
          <div><strong>{{ session.current ? '当前设备' : deviceLabel(session.userAgent) }}</strong><p>{{ session.ip || '未知地址' }} · 最近活动 {{ formatTime(session.lastSeenAt) }}</p></div>
          <el-button size="small" :disabled="sessionsBusy" @click="revokeSession(session)">{{ session.current ? '退出当前设备' : '退出该设备' }}</el-button>
        </li>
      </ul>
      <p v-else class="muted">没有可显示的登录设备。</p>
    </section>
  </WorkspaceShell>
</template>

<style scoped>
.security-panel { max-width: 620px; }
.session-list { list-style: none; padding: 0; margin: 12px 0 0; display: grid; gap: 8px; }
.session-list li { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 12px; border: 1px solid var(--color-border-soft); border-radius: 8px; }
.session-list strong { font-size: 13px; }
.session-list p { margin: 4px 0 0; font-size: 12px; color: var(--color-muted-text); overflow-wrap: anywhere; }
</style>
