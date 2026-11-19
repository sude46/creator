<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import { creatorApi, processingLabels, publicationLabels, reviewLabels } from '../api/creator'
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

function nextAction(video: CreatorVideo) {
  if (video.moderationBlocked) return '等待管理员解除封禁'
  if (!video.sourceUploaded) return '上传原始视频'
  if (video.processingStatus === 'FAILED') return '检查处理失败原因'
  if (video.processingStatus !== 'READY') return '等待媒体处理完成'
  if (video.reviewStatus === 'REJECTED') return '修改后重新送审'
  if (video.reviewStatus === 'NOT_SUBMITTED') return '提交审核'
  if (video.reviewStatus === 'PENDING') return '等待审核结果'
  if (video.publicationStatus === 'PUBLISHED') return '查看已发布作品'
  return '发布作品'
}

function coverTone(id: string) {
  return `cover-tone-${(Number(id) % 4) + 1}`
}

async function loadCovers(items: CreatorVideo[]) {
  await Promise.allSettled(items.filter(video => video.sourceUploaded && video.processingStatus === 'READY').map(async video => {
    try {
      const signed = await creatorApi.cover(video.id)
      covers.value = { ...covers.value, [video.id]: signed.url }
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
    <div class="studio-heading"><div><p class="eyebrow">FRAME ROOM / WORKSPACE</p><h1>作品工作台</h1><p class="muted">上传视频、跟进处理进度，查看审核结果。</p></div><el-button v-if="!creating" type="primary" @click="creating = true">新建作品</el-button></div>
    <VideoEditor v-if="creating" @saved="saved" @cancel="creating = false; load(1)" @busy="saving = $event" />
    <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" />
    <section class="studio-panel" aria-label="我的作品" :aria-busy="loading">
      <div class="section-heading"><h2>我的作品 <span class="count">{{ total }}</span></h2><el-button :loading="loading" @click="load()">刷新列表</el-button></div>
      <p v-if="loading" class="empty-state" role="status">正在加载作品…</p>
      <div v-else-if="videos.length" class="video-grid">
        <article v-for="video in videos" :key="video.id" class="creator-card">
          <RouterLink class="creator-cover" :class="coverTone(video.id)" :to="`/studio/${video.id}`" :aria-label="`打开作品：${video.title}`"><img v-if="covers[video.id]" :src="covers[video.id]" alt="" loading="lazy" referrerpolicy="no-referrer" @error="coverFailed(video.id)" /><span class="cover-index">{{ String(video.id).padStart(2, '0') }}</span><span class="cover-caption">{{ covers[video.id] ? '打开作品' : '封面暂未接入 · 打开作品' }}</span></RouterLink>
          <div class="creator-card-body"><div class="card-number">作品 #{{ video.id }}</div><h3><RouterLink :to="`/studio/${video.id}`">{{ video.title }}</RouterLink></h3>
          <p class="card-description">{{ video.description || '还没有简介，补充一句作品想表达什么。' }}</p>
          <div class="status-group"><el-tag :type="video.processingStatus === 'FAILED' ? 'danger' : 'info'">{{ video.sourceUploaded ? processingLabels[video.processingStatus] : '等待上传' }}</el-tag><el-tag>{{ reviewLabels[video.reviewStatus] }}</el-tag><el-tag :type="video.moderationBlocked ? 'danger' : 'info'">{{ video.moderationBlocked ? '已封禁' : publicationLabels[video.publicationStatus] }}</el-tag></div>
          <div class="card-next"><span>下一步</span><strong>{{ nextAction(video) }}</strong></div><RouterLink class="card-open" :to="`/studio/${video.id}`">打开工作台 <span aria-hidden="true">↗</span></RouterLink></div>
        </article>
      </div>
      <p v-else class="empty-state">{{ error ? '作品未能加载，请重试。' : total > 0 ? '本页暂无作品，请返回上一页。' : '还没有作品。点击“新建作品”，填写信息后上传视频。' }}</p>
      <div class="page-controls"><el-button :disabled="loading || page <= 1" @click="load(page - 1)">上一页</el-button><span>第 {{ page }} 页</span><el-button :disabled="loading || page * 10 >= total || page >= 1000" @click="load(page + 1)">下一页</el-button></div>
    </section>
  </WorkspaceShell>
</template>
