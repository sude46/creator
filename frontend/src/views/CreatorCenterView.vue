<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import { creatorApi } from '../api/creator'
import { creatorStatus } from '../api/videoStatus'
import { cachedCover } from '../api/coverCache'
import type { CreatorVideo, Draft } from '../api/creator'
import WorkspaceShell from '../components/WorkspaceShell.vue'
import VideoEditor from '../components/VideoEditor.vue'
import { useStudioError } from '../composables/useStudioError'

const router = useRouter()
const videos = ref<CreatorVideo[]>([])
const covers = ref<Record<string, string>>({})
const page = ref(1)
const total = ref(0)
const loading = ref(false)
const creating = ref(false)
const saving = ref(false)
const error = ref('')
const explain = useStudioError()
let activeRequest: AbortController | undefined

function statusOf(video: CreatorVideo) { return creatorStatus(video) }
function itemNumber(index: number) { return String((page.value - 1) * 10 + index + 1).padStart(2, '0') }
function coverTone(id: string) {
  return `cover-tone-${(Number(id) % 4) + 1}`
}

async function loadCovers(items: CreatorVideo[]) {
  await Promise.allSettled(items.filter(video => video.sourceUploaded && video.processingStatus === 'READY' && video.hasCover).map(async video => {
    try {
      const entry = await cachedCover(`creator:${video.id}:${video.coverKey ?? ''}`, () => creatorApi.cover(video.id))
      covers.value = { ...covers.value, [video.id]: entry.url }
    } catch { /* processed videos without a readable cover keep the placeholder */ }
  }))
}
function coverFailed(id: string) {
  const next = { ...covers.value }
  delete next[id]
  covers.value = next
}
async function load(next = page.value) {
  activeRequest?.abort()
  const controller = new AbortController()
  activeRequest = controller
  loading.value = true
  error.value = ''
  try {
    const result = await creatorApi.list(next, controller.signal)
    if (controller.signal.aborted) return
    videos.value = result.items
    page.value = next
    total.value = result.total
    void loadCovers(result.items)
  } catch (failure) { if (!controller.signal.aborted) { videos.value = []; error.value = explain(failure) } }
  finally { if (!controller.signal.aborted) loading.value = false }
}
function saved(draft: Draft) { saving.value = false; creating.value = false; void router.push(`/studio/${draft.id}`) }
function leave(event: BeforeUnloadEvent) { if (saving.value) event.preventDefault() }
onBeforeRouteLeave(() => !saving.value || window.confirm('作品正在保存，离开后结果可能未确认。仍要离开吗？'))
onMounted(() => { void load(); window.addEventListener('beforeunload', leave) })
onBeforeUnmount(() => { activeRequest?.abort(); window.removeEventListener('beforeunload', leave) })
</script>

<template>
  <WorkspaceShell>
    <div class="studio-heading"><div><p class="eyebrow">FRAME ROOM / WORKSPACE</p><h1>我的作品</h1><p class="muted">发布作品，跟进处理进度与审核结果。</p></div><div class="inline-actions"><el-button :disabled="loading || !total" @click="creatorApi.exportVideos()">导出 CSV</el-button><el-button v-if="!creating" type="primary" @click="creating = true">发布作品</el-button></div></div>
    <VideoEditor v-if="creating" @saved="saved" @cancel="creating = false; load(1)" @busy="saving = $event" />
    <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" />
    <section class="studio-panel" aria-label="我的作品" :aria-busy="loading">
      <div class="section-heading"><h2>我的作品 <span class="count">{{ total }}</span></h2><el-button :loading="loading" @click="load()">刷新列表</el-button></div>
      <div v-if="loading" class="video-grid" aria-busy="true" aria-label="正在加载作品"><div v-for="index in 6" :key="index" class="creator-card-skeleton" aria-hidden="true"></div></div>
      <div v-else-if="videos.length" class="video-grid">
        <article v-for="(video, index) in videos" :key="video.id" class="creator-card">
          <RouterLink class="creator-cover" :class="coverTone(video.id)" :to="`/studio/${video.id}`" :aria-label="`打开作品：${video.title}`"><img v-if="covers[video.id]" :src="covers[video.id]" alt="" loading="lazy" referrerpolicy="no-referrer" @error="coverFailed(video.id)" /><span class="cover-index">{{ itemNumber(index) }}</span><span class="cover-caption">打开作品</span></RouterLink>
          <div class="creator-card-body"><div class="card-number">{{ statusOf(video).visibilityLabel }}</div><h3><RouterLink :to="`/studio/${video.id}`">{{ video.title }}</RouterLink></h3>
          <p class="card-description">{{ video.description || '还没有简介，补充一句作品想表达什么。' }}</p>
          <div class="status-group"><el-tag :type="statusOf(video).tone">{{ statusOf(video).label }}</el-tag></div>
          <p class="card-status-detail">{{ statusOf(video).detail }}</p>
          <div class="card-next"><span>下一步</span><strong>{{ statusOf(video).nextAction }}</strong></div><RouterLink class="card-open" :to="`/studio/${video.id}`">打开作品 <span aria-hidden="true">↗</span></RouterLink></div>
        </article>
      </div>
      <p v-else class="empty-state">{{ error ? '作品未能加载，请重试。' : total > 0 ? '本页暂无作品，请返回上一页。' : '还没有作品。点击“发布作品”，填写信息并选择视频，平台会开始处理。' }}</p>
      <div v-if="error" class="inline-actions empty-retry"><el-button @click="load()">重新加载</el-button></div>
      <div class="page-controls"><el-button :disabled="loading || page <= 1" @click="load(page - 1)">上一页</el-button><span>第 {{ page }} 页</span><el-button :disabled="loading || page * 10 >= total || page >= 1000" @click="load(page + 1)">下一页</el-button></div>
    </section>
  </WorkspaceShell>
</template>
