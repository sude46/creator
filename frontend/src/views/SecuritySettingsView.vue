<script setup lang="ts">
import { onBeforeUnmount, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import WorkspaceShell from '../components/WorkspaceShell.vue'
import { accountApi } from '../api/account'
import { auth } from '../stores/auth'
import { useStudioError } from '../composables/useStudioError'

const router = useRouter()
const explain = useStudioError()
const form = reactive({ current: '', next: '', confirm: '' })
const busy = ref(false)
const error = ref('')
let stopped = false

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
    await auth.logout()
    await router.push({ path: '/login', query: { changed: '1' } })
  } catch (failure) { if (!stopped) error.value = explain(failure) }
  finally { if (!stopped) busy.value = false }
}

onBeforeUnmount(() => { stopped = true })
</script>

<template>
  <WorkspaceShell>
    <div class="studio-heading"><div><p class="eyebrow">FRAME ROOM / SECURITY</p><h1>账号安全</h1><p class="muted">修改密码后当前会话会退出，其他设备上的既有会话不会自动失效。</p></div><RouterLink class="create-link" to="/settings/profile">个人资料</RouterLink></div>
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
  </WorkspaceShell>
</template>

<style scoped>
.security-panel { max-width: 620px; }
</style>
