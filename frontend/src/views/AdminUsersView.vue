<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import WorkspaceShell from '../components/WorkspaceShell.vue'
import { adminApi } from '../api/admin'
import type { AdminUser } from '../api/admin'
import { useStudioError } from '../composables/useStudioError'

const explain = useStudioError()
const users = ref<AdminUser[]>([])
const page = ref(1)
const total = ref(0)
const loading = ref(false)
const busy = ref(false)
const error = ref('')
const notice = ref('')
const scopeDraft = ref<Record<string, string>>({})
const username = ref('')
const password = ref('')
const creating = ref(false)
let activeRequest: AbortController | undefined
let stopped = false

async function load(next = page.value) {
  activeRequest?.abort()
  const controller = new AbortController()
  activeRequest = controller
  loading.value = true
  error.value = ''
  users.value = []
  try {
    const result = await adminApi.users(next, controller.signal)
    if (stopped || controller.signal.aborted) return
    users.value = result.items
    total.value = result.total
    page.value = result.page
  } catch (failure) { if (!stopped && !controller.signal.aborted) error.value = explain(failure) }
  finally { if (!stopped && !controller.signal.aborted) loading.value = false }
}

async function create() {
  if (busy.value || creating.value) return
  const name = username.value.trim()
  if (!name || !password.value) { error.value = '请填写用户名和密码。'; return }
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    await adminApi.createUser(name, password.value)
    if (stopped) return
    username.value = ''
    password.value = ''
    creating.value = false
    notice.value = '用户已创建。'
    await load(1)
  } catch (failure) { if (!stopped) error.value = explain(failure) }
  finally { if (!stopped) busy.value = false }
}

function isSuper(user: AdminUser) { return user.role === 'ADMIN' && (!user.adminScope || user.adminScope === 'SUPER_ADMIN') }
function roleLabel(user: AdminUser) { return user.role !== 'ADMIN' ? '普通用户' : user.adminScope === 'REVIEWER' ? '审核员' : user.adminScope === 'MODERATOR' ? '内容管理员' : '超级管理员' }
function scopeValue(user: AdminUser) { return scopeDraft.value[user.id] ?? user.adminScope ?? 'NONE' }
function setScope(user: AdminUser, event: Event) { scopeDraft.value = { ...scopeDraft.value, [user.id]: (event.target as HTMLSelectElement).value } }
async function saveScope(user: AdminUser) {
  if (busy.value || loading.value || isSuper(user)) return
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    await adminApi.setAdminScope(user, scopeValue(user) as 'NONE' | 'REVIEWER' | 'MODERATOR')
    if (stopped) return
    notice.value = `已更新 ${user.username} 的管理权限。`
    scopeDraft.value = {}
    await load()
  } catch (failure) { if (!stopped) error.value = explain(failure) }
  finally { if (!stopped) busy.value = false }
}
async function toggle(user: AdminUser) {
  if (busy.value || loading.value || isSuper(user)) return
  const action = user.status === 'ACTIVE' ? '禁用' : '启用'
  if (!window.confirm(`${action}用户 ${user.username}？`)) return
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    await adminApi.setUserStatus(user, user.status !== 'ACTIVE')
    if (stopped) return
    notice.value = `用户已${action}。`
    await load()
  } catch (failure) { if (!stopped) error.value = explain(failure) }
  finally { if (!stopped) busy.value = false }
}

onMounted(() => { void load() })
onBeforeUnmount(() => { stopped = true; activeRequest?.abort() })
</script>

<template>
  <WorkspaceShell>
    <div class="studio-heading">
      <div><p class="eyebrow">FRAME ROOM / PEOPLE</p><h1>用户管理</h1><p class="muted">创建用户，分配审核员/内容管理员权限，并启用或禁用账号；超级管理员不在此处修改。</p></div>
      <el-button :disabled="busy" @click="creating = !creating">{{ creating ? '取消创建' : '创建用户' }}</el-button>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" />
    <p v-if="notice" role="status" class="muted">{{ notice }}</p>
    <section v-if="creating" class="studio-panel">
      <h2>新建普通用户</h2>
      <form class="studio-form" @submit.prevent="create">
        <label for="new-username">用户名</label><input id="new-username" v-model="username" maxlength="32" required />
        <label for="new-password">初始密码</label><input id="new-password" v-model="password" type="password" maxlength="64" required />
        <div class="inline-actions"><el-button type="primary" native-type="submit" :loading="busy">创建用户</el-button></div>
      </form>
    </section>
    <section class="studio-panel" :aria-busy="loading">
      <div class="section-heading"><h2>用户 <span class="count">{{ total }}</span></h2><el-button :loading="loading" :disabled="busy" @click="load()">刷新列表</el-button></div>
      <p v-if="loading" role="status">正在读取用户…</p>
      <div v-else class="user-table">
        <table>
          <thead><tr><th scope="col">用户名</th><th scope="col">角色</th><th scope="col">管理权限</th><th scope="col">状态</th><th scope="col">操作</th></tr></thead>
          <tbody>
            <tr v-for="user in users" :key="user.id">
              <td>{{ user.username }}</td>
              <td>{{ roleLabel(user) }}</td>
              <td>
                <span v-if="isSuper(user)" class="muted">超级管理员</span>
                <div v-else class="inline-actions scope-editor">
                  <select :value="scopeValue(user)" :disabled="busy" :aria-label="`${user.username} 的管理权限`" @change="setScope(user, $event)">
                    <option value="NONE">普通用户</option>
                    <option value="REVIEWER">审核员</option>
                    <option value="MODERATOR">内容管理员</option>
                  </select>
                  <el-button size="small" :disabled="busy || scopeValue(user) === (user.adminScope ?? 'NONE')" @click="saveScope(user)">保存权限</el-button>
                </div>
              </td>
              <td>{{ user.status === 'ACTIVE' ? '启用' : '禁用' }}</td>
              <td><span v-if="isSuper(user)" class="muted">不可修改</span><el-button v-else :disabled="busy" @click="toggle(user)">{{ user.status === 'ACTIVE' ? '禁用' : '启用' }}</el-button></td>
            </tr>
          </tbody>
        </table>
      </div>
      <p v-if="!loading && !users.length" class="empty-state">暂无用户。</p>
      <div class="page-controls"><el-button :disabled="loading || busy || page <= 1" @click="load(page - 1)">上一页</el-button><span>第 {{ page }} 页</span><el-button :disabled="loading || busy || page * 20 >= total || page >= 1000" @click="load(page + 1)">下一页</el-button></div>
    </section>
  </WorkspaceShell>
</template>

<style scoped>
.user-table { overflow-x: auto; }
.user-table table { width: 100%; min-width: 720px; border-collapse: collapse; font-size: 14px; }
.user-table th, .user-table td { text-align: left; padding: 12px 8px; border-bottom: 1px solid #e5e8e2; white-space: nowrap; }
.scope-editor { gap: 8px; }
.scope-editor select { border: 1px solid var(--color-control-border); border-radius: 3px; padding: 5px 8px; font: inherit; font-size: 13px; background: var(--color-surface); color: var(--color-text); }
</style>
