<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import WorkspaceShell from '../components/WorkspaceShell.vue'
import PublicVideoCard from '../components/PublicVideoCard.vue'
import { publicApi } from '../api/public'
import type { AuthorProfile, PublicCollection, PublicVideo } from '../api/public'
import { useStudioError } from '../composables/useStudioError'

const route = useRoute()
const explain = useStudioError()
const id = String(route.params.id)
const items = ref<PublicVideo[]>([])
const page = ref(1)
const total = ref(0)
const loading = ref(false)
const error = ref('')
const authorName = ref('')
const displayName = ref('')
const collections = ref<PublicCollection[]>([]); const profile = ref<AuthorProfile>(); const followBusy = ref(false)
let activeRequest: AbortController | undefined
let stopped = false

async function loadProfile() { try { const result = await publicApi.authorProfile(id, new AbortController().signal); if (!stopped) { profile.value = result; if (result.displayName) displayName.value = result.displayName } } catch { /* optional */ } }
async function toggleFollow() { if (!profile.value || followBusy.value) return; followBusy.value = true; const add = !profile.value.following; try { await publicApi.follow(id, add); if (!stopped) profile.value = { ...profile.value, following: add, followerCount: Math.max(0, profile.value.followerCount + (add ? 1 : -1)) } } catch { /* best effort */ } finally { if (!stopped) followBusy.value = false } }
async function loadCollections() { try { const result = await publicApi.authorCollections(id, new AbortController().signal); if (!stopped) collections.value = result.items } catch { /* optional */ } }
async function load(next = page.value) {
  activeRequest?.abort(); const controller = new AbortController(); activeRequest = controller
  loading.value = true; error.value = ''; items.value = []
  try {
    const result = await publicApi.authorVideos(id, next, controller.signal)
    if (stopped || controller.signal.aborted) return
    items.value = result.items; total.value = result.total; page.value = result.page
    authorName.value = result.items[0]?.authorName ?? ''
  } catch (failure) { if (!stopped && !controller.signal.aborted) error.value = explain(failure) }
  finally { if (!stopped && !controller.signal.aborted) loading.value = false }
}
onMounted(() => {
  if (/^[1-9]\d{0,18}$/.test(id)) { void load(); void loadCollections(); void loadProfile() }
  else error.value = '作者编号无效'
})
onBeforeUnmount(() => { stopped = true; activeRequest?.abort() })
</script>

<template>
  <WorkspaceShell>
    <div class="author-hero">
      <div class="author-avatar" aria-hidden="true">
        <img v-if="profile?.hasAvatar" :src="`/api/authors/${id}/avatar?v=${profile.profileVersion}`" alt="" />
        <span v-else>{{ (displayName || authorName || profile?.username || '作').slice(0, 1) }}</span>
      </div>
      <div class="author-copy">
        <p class="eyebrow">FRAME ROOM / AUTHOR</p>
        <h1>{{ displayName || authorName || profile?.username || '作者作品' }}</h1>
        <p v-if="profile?.bio" class="author-bio">{{ profile.bio }}</p>
        <p class="muted">公开作品 · {{ total }} 部<span v-if="profile"> · {{ profile.followerCount }} 关注者 · {{ profile.followingCount }} 关注中</span></p>
        <a v-if="profile?.website" class="author-link" :href="profile.website" target="_blank" rel="noopener noreferrer nofollow">{{ profile.website }}</a>
      </div>
      <div class="inline-actions author-actions"><template v-if="profile?.self"><RouterLink class="create-link" to="/settings/profile">编辑资料</RouterLink></template><el-button v-else-if="profile" :loading="followBusy" @click="toggleFollow">{{ profile.following ? '已关注' : '关注' }}</el-button><RouterLink class="create-link" to="/">返回发现</RouterLink></div>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" />
    <section v-if="collections.length" class="studio-panel"><div class="section-heading"><h2>合集</h2></div><div class="inline-actions"><RouterLink v-for="collection in collections" :key="collection.id" class="create-link" :to="`/collections/${collection.id}`">{{ collection.title }} · {{ collection.itemCount }}</RouterLink></div></section>
    <div v-if="loading" class="public-video-grid" aria-busy="true"><div v-for="index in 6" :key="index" class="public-card-skeleton" aria-hidden="true"></div></div>
    <div v-else-if="items.length" class="public-video-grid"><PublicVideoCard v-for="item in items" :key="item.id" :video="item" /></div>
    <p v-else-if="!error" class="empty-state">这位作者暂时没有公开作品。</p>
    <div class="page-controls">
      <el-button :disabled="loading || page <= 1" @click="load(page - 1)">上一页</el-button><span>第 {{ page }} 页</span><el-button :disabled="loading || !!error || page * 12 >= total || page >= 1000" @click="load(page + 1)">下一页</el-button>
    </div>
  </WorkspaceShell>
</template>

<style scoped>
.author-hero { display: grid; grid-template-columns: 92px minmax(0, 1fr) auto; gap: 22px; align-items: start; padding: 6px 0 28px; }
.author-avatar { width: 92px; height: 92px; border-radius: 50%; overflow: hidden; display: grid; place-items: center; background: var(--color-primary-soft); color: var(--color-primary); font-size: 34px; border: 1px solid var(--color-border); }
.author-avatar img { width: 100%; height: 100%; object-fit: cover; }
.author-copy h1 { font-size: 30px; margin: 4px 0 8px; }
.author-bio { max-width: 640px; margin: 0 0 10px; color: var(--color-secondary); line-height: 1.7; white-space: pre-wrap; }
.author-link { display: inline-block; margin-top: 6px; font-size: 13px; color: var(--color-accent); overflow-wrap: anywhere; }
.author-actions { flex-wrap: wrap; justify-content: flex-end; }
@media (max-width: 700px) { .author-hero { grid-template-columns: 1fr; } .author-actions { justify-content: flex-start; } }
</style>
