<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import WorkspaceShell from '../components/WorkspaceShell.vue'
import { ApiFailure } from '../api/http'
import { profileApi } from '../api/profile'
import type { ProfileForm } from '../api/profile'
import { useStudioError } from '../composables/useStudioError'
import { auth } from '../stores/auth'

const explain = useStudioError()
const form = ref<ProfileForm>({ displayName: null, bio: '', website: '', hasAvatar: false, version: 0 })
const displayName = ref('')
const bio = ref('')
const website = ref('')
const loading = ref(false)
const saving = ref(false)
const avatarBusy = ref(false)
const error = ref('')
const notice = ref('')
const preview = ref('')
const fileInput = ref<HTMLInputElement>()
let activeRequest: AbortController | undefined
let stopped = false

function apply(profile: ProfileForm) {
  form.value = profile
  displayName.value = profile.displayName ?? ''
  bio.value = profile.bio
  website.value = profile.website
  if (preview.value) { URL.revokeObjectURL(preview.value); preview.value = '' }
}

async function load() {
  activeRequest?.abort()
  const controller = new AbortController()
  activeRequest = controller
  loading.value = true
  error.value = ''
  try {
    const profile = await profileApi.get(controller.signal)
    if (!stopped && !controller.signal.aborted) apply(profile)
  } catch (failure) { if (!stopped && !controller.signal.aborted) error.value = explain(failure) }
  finally { if (!stopped && !controller.signal.aborted) loading.value = false }
}

async function save() {
  if (saving.value || loading.value) return
  saving.value = true
  error.value = ''
  notice.value = ''
  try {
    const updated = await profileApi.update({ ...form.value, displayName: displayName.value.trim() || null, bio: bio.value, website: website.value.trim() })
    if (stopped) return
    apply(updated)
    notice.value = '个人资料已保存。'
  } catch (failure) {
    if (!stopped) {
      error.value = explain(failure)
      if (failure instanceof ApiFailure && failure.status === 409) error.value += '。请刷新后重新编辑。'
    }
  } finally { if (!stopped) saving.value = false }
}

async function chooseAvatar(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file || avatarBusy.value) return
  if (!['image/png', 'image/jpeg', 'image/webp'].includes(file.type)) { error.value = '头像仅支持 PNG、JPEG 或 WebP。'; return }
  if (file.size > 200 * 1024) { error.value = '头像不能超过 200 KiB。'; return }
  avatarBusy.value = true
  error.value = ''
  notice.value = ''
  const objectUrl = URL.createObjectURL(file)
  try {
    await profileApi.uploadAvatar(file)
    if (stopped) return
    if (preview.value) URL.revokeObjectURL(preview.value)
    preview.value = objectUrl
    form.value = { ...form.value, hasAvatar: true }
    notice.value = '头像已更新。'
  } catch (failure) {
    URL.revokeObjectURL(objectUrl)
    if (!stopped) error.value = explain(failure)
  } finally {
    if (!stopped) avatarBusy.value = false
    if (fileInput.value) fileInput.value.value = ''
  }
}

async function removeAvatar() {
  if (avatarBusy.value || !window.confirm('移除当前头像？')) return
  avatarBusy.value = true
  error.value = ''
  notice.value = ''
  try {
    await profileApi.removeAvatar()
    if (stopped) return
    if (preview.value) URL.revokeObjectURL(preview.value)
    preview.value = ''
    form.value = { ...form.value, hasAvatar: false }
    notice.value = '头像已移除。'
  } catch (failure) { if (!stopped) error.value = explain(failure) }
  finally { if (!stopped) avatarBusy.value = false }
}

function avatarUrl() {
  const userId = auth.user.value?.id
  return preview.value || (form.value.hasAvatar && userId ? `/api/authors/${encodeURIComponent(userId)}/avatar?v=${form.value.version}` : '')
}

onMounted(() => void load())
onBeforeUnmount(() => { stopped = true; activeRequest?.abort(); if (preview.value) URL.revokeObjectURL(preview.value) })
</script>

<template>
  <WorkspaceShell>
    <div class="studio-heading">
      <div><p class="eyebrow">FRAME ROOM / PROFILE</p><h1>个人资料</h1><p class="muted">昵称、简介和头像会展示在作者主页与公开作品旁。</p></div>
      <RouterLink class="create-link" to="/">返回发现</RouterLink>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" />
    <p v-if="notice" class="muted" role="status">{{ notice }}</p>
    <section class="studio-panel" :aria-busy="loading">
      <div class="profile-layout">
        <div class="avatar-column">
          <div class="avatar-preview">
            <img v-if="avatarUrl()" :src="avatarUrl()" alt="当前头像" />
            <span v-else aria-hidden="true">{{ (displayName || '我').slice(0, 1) }}</span>
          </div>
          <label class="file-picker" for="avatar-file">{{ avatarBusy ? '正在上传…' : '更换头像' }}</label>
          <input id="avatar-file" ref="fileInput" type="file" accept="image/png,image/jpeg,image/webp" :disabled="avatarBusy" @change="chooseAvatar" />
          <p class="muted">PNG / JPEG / WebP，最大 200 KiB。</p>
          <el-button v-if="form.hasAvatar" :disabled="avatarBusy" @click="removeAvatar">移除头像</el-button>
        </div>
        <form class="studio-form profile-form" @submit.prevent="save">
          <label for="profile-name">昵称</label>
          <input id="profile-name" v-model="displayName" maxlength="32" placeholder="留空时显示登录用户名" />
          <label for="profile-bio">简介</label>
          <textarea id="profile-bio" v-model="bio" maxlength="200" rows="4" placeholder="介绍一下自己或创作方向，最多 200 字。" />
          <label for="profile-website">主页链接</label>
          <input id="profile-website" v-model="website" maxlength="200" placeholder="https://example.com（可选）" />
          <p class="muted">仅支持 http/https 外链；保存后旧资料立即替换。</p>
          <div class="inline-actions">
            <el-button type="primary" native-type="submit" :loading="saving" :disabled="loading">保存资料</el-button>
            <el-button :disabled="saving || loading" @click="load">放弃修改</el-button>
          </div>
        </form>
      </div>
    </section>
  </WorkspaceShell>
</template>

<style scoped>
.profile-layout { display: grid; grid-template-columns: 180px minmax(0, 1fr); gap: 32px; align-items: start; }
.avatar-column { display: flex; flex-direction: column; align-items: center; gap: 10px; text-align: center; }
.avatar-preview { width: 132px; height: 132px; border-radius: 50%; overflow: hidden; display: grid; place-items: center; background: var(--color-primary-soft); color: var(--color-primary); font-size: 44px; border: 1px solid var(--color-border); }
.avatar-preview img { width: 100%; height: 100%; object-fit: cover; }
.avatar-column input[type=file] { display: none; }
.profile-form { max-width: 640px; }
@media (max-width: 700px) { .profile-layout { grid-template-columns: 1fr; } }
</style>
