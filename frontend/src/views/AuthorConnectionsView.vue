<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import WorkspaceShell from '../components/WorkspaceShell.vue'
import { publicApi } from '../api/public'
import type { AuthorCard, AuthorProfile } from '../api/public'
import { useStudioError } from '../composables/useStudioError'
import { auth } from '../stores/auth'

const route = useRoute()
const explain = useStudioError()
const authorId = computed(() => String(route.params.id))
const mode = computed<'followers' | 'following'>(() => route.path.endsWith('/following') ? 'following' : 'followers')
const profile = ref<AuthorProfile>()
const items = ref<AuthorCard[]>([])
const page = ref(1)
const total = ref(0)
const loading = ref(false)
const busyId = ref<string>()
const error = ref('')
let request: AbortController | undefined
let stopped = false

async function loadProfile() {
  try {
    const result = await publicApi.authorProfile(authorId.value, AbortSignal.timeout(15000))
    if (!stopped) profile.value = result
  } catch { /* the list still renders without the header profile */ }
}

async function load(next = page.value) {
  request?.abort()
  const controller = new AbortController()
  request = controller
  loading.value = true
  error.value = ''
  try {
    const result = mode.value === 'followers'
      ? await publicApi.authorFollowers(authorId.value, next, controller.signal)
      : await publicApi.authorFollowing(authorId.value, next, controller.signal)
    if (stopped || controller.signal.aborted) return
    items.value = result.items
    page.value = result.page
    total.value = result.total
  } catch (failure) { if (!stopped && !controller.signal.aborted) error.value = explain(failure) }
  finally { if (!stopped && !controller.signal.aborted) loading.value = false }
}

async function toggleFollow(card: AuthorCard) {
  if (busyId.value) return
  busyId.value = card.id
  error.value = ''
  const add = !card.following
  try {
    await publicApi.follow(card.id, add)
    if (stopped) return
    items.value = items.value.map(item => item.id === card.id
      ? { ...item, following: add, followerCount: Math.max(0, item.followerCount + (add ? 1 : -1)) }
      : item)
  } catch (failure) { if (!stopped) error.value = explain(failure) }
  finally { if (!stopped) busyId.value = undefined }
}

const displayName = computed(() => profile.value?.displayName || profile.value?.username || '作者')
function avatarUrl(card: AuthorCard) {
  return card.hasAvatar ? `/api/authors/${card.id}/avatar?v=${card.profileVersion}` : ''
}
function refresh() { void loadProfile(); void load(1) }
watch(() => route.fullPath, refresh)
onMounted(refresh)
onBeforeUnmount(() => { stopped = true; request?.abort() })
</script>

<template>
  <WorkspaceShell>
    <RouterLink class="back-link" :to="`/authors/${authorId}`">← 返回 {{ displayName }}</RouterLink>
    <div class="studio-heading">
      <div><p class="eyebrow">FRAME ROOM / CONNECTIONS</p><h1>{{ mode === 'followers' ? '关注者' : '关注中' }}</h1><p class="muted">{{ displayName }} · {{ total }} 人</p></div>
      <RouterLink class="create-link" :to="`/authors/${authorId}`">作者主页</RouterLink>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" />
    <p v-if="loading" class="empty-state" role="status">正在读取列表…</p>
    <div v-else-if="items.length" class="author-card-grid">
      <article v-for="card in items" :key="card.id" class="author-card">
        <div class="author-card-avatar" aria-hidden="true">
          <img v-if="avatarUrl(card)" :src="avatarUrl(card)" alt="" />
          <span v-else>{{ (card.displayName || card.username).slice(0, 1) }}</span>
        </div>
        <div class="author-card-copy">
          <h2><RouterLink :to="`/authors/${card.id}`">{{ card.displayName || card.username }}</RouterLink></h2>
          <p class="muted">@{{ card.username }} · {{ card.followerCount }} 关注者</p>
          <p v-if="card.bio" class="author-card-bio">{{ card.bio }}</p>
        </div>
        <el-button v-if="auth.user.value?.id !== card.id" :loading="busyId === card.id" :disabled="!!busyId && busyId !== card.id" @click="toggleFollow(card)">{{ card.following ? '已关注' : '关注' }}</el-button>
      </article>
    </div>
    <p v-else-if="!error" class="empty-state">{{ mode === 'followers' ? '还没有关注者。' : '还没有关注任何人。' }}</p>
    <div class="page-controls">
      <el-button :disabled="loading || page <= 1" @click="load(page - 1)">上一页</el-button>
      <span>第 {{ page }} 页</span>
      <el-button :disabled="loading || !!error || page * 20 >= total || page >= 1000" @click="load(page + 1)">下一页</el-button>
    </div>
  </WorkspaceShell>
</template>

<style scoped>
.author-card-grid { display: grid; gap: 12px; }
.author-card { display: flex; align-items: center; gap: 16px; padding: 18px 20px; border: 1px solid var(--color-border); border-radius: var(--radius-card); background: var(--color-surface); }
.author-card-avatar { flex: none; width: 52px; height: 52px; border-radius: 50%; overflow: hidden; display: grid; place-items: center; background: var(--color-primary-soft); color: var(--color-primary); font-size: 20px; }
.author-card-avatar img { width: 100%; height: 100%; object-fit: cover; }
.author-card-copy { flex: 1; min-width: 0; }
.author-card-copy h2 { font-size: 17px; margin: 0 0 4px; overflow-wrap: anywhere; }
.author-card-copy h2 a { color: var(--color-text); }
.author-card-copy h2 a:hover { color: var(--color-primary); }
.author-card-copy .muted { margin: 0; font-size: 12px; }
.author-card-bio { margin: 5px 0 0; font-size: 13px; color: var(--color-muted-text); overflow-wrap: anywhere; }
@media (max-width: 600px) { .author-card { align-items: flex-start; flex-wrap: wrap; } .author-card .el-button { margin-left: 68px; } }
</style>
