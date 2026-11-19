<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'
import { creatorApi, formatTime } from '../api/creator'
import type { Collection, CreatorCommand, CreatorVideo, ModerationRecord, ReviewRecord, SignedMedia } from '../api/creator'
import { ApiFailure } from '../api/http'
import { validateVideoFile } from '../api/uploadFile'
import { creatorStatus } from '../api/videoStatus'
import WorkspaceShell from '../components/WorkspaceShell.vue'
import VideoEditor from '../components/VideoEditor.vue'
import { useStudioError } from '../composables/useStudioError'

const route = useRoute()
const router = useRouter()
const id = String(route.params.id)
const video = ref<CreatorVideo>()
const loading = ref(false)
const busy = ref(false)
const needsRefresh = ref(false)
const editing = ref(false)
const editorBusy = ref(false)
const uploading = ref(false)
const uploadUnknown = ref(false)
const file = ref<File>()
const fileInput = ref<HTMLInputElement>()
const error = ref('')
const notice = ref('')
const collections = ref<Collection[]>([])
const collectionId = ref('')
const collectionBusy = ref(false)
const preview = ref<SignedMedia>()
const downloadBusy = ref(false)
const deleteBusy = ref(false)
const previewError = ref('')
const reviews = ref<ReviewRecord[]>([])
const moderation = ref<ModerationRecord[]>([])
const historyPage = ref(1)
const historyTotal = ref(0)
const moderationPage = ref(1)
const moderationTotal = ref(0)
const historyError = ref('')
const historyLoading = ref(false)
const explain = useStudioError()
const display = computed(() => video.value ? creatorStatus(video.value) : undefined)
const uploadPanel = ref<HTMLElement>()
let activeRequest: AbortController | undefined
let uploadRequest: AbortController | undefined
let historyRequest: AbortController | undefined
let timer: ReturnType<typeof setTimeout> | undefined
let stopped = false
let refreshFailures = 0
const blocked = computed(() => busy.value || editorBusy.value || loading.value || needsRefresh.value)
const ready = computed(() => video.value?.processingStatus === 'READY' && video.value.taskState === 'SUCCEEDED' && !video.value.moderationBlocked)
const draft = computed(() => !!video.value && !video.value.sourceUploaded && !video.value.moderationBlocked)
const editable = computed(() => draft.value || (ready.value && video.value?.reviewStatus !== 'PENDING'))
const polling = computed(() => !!video.value && !video.value.moderationBlocked &&
  ((video.value.sourceUploaded && ['PENDING', 'PROCESSING'].includes(video.value.processingStatus)) || video.value.reviewStatus === 'PENDING'))

function schedule() {
  clearTimeout(timer)
  if (!stopped && polling.value && refreshFailures < 3 && !document.hidden) {
    timer = setTimeout(() => {
      if (!busy.value && !editorBusy.value && !loading.value && !editing.value) void load(false)
      else schedule()
    }, 10000)
  }
}
async function load(manual = true) {
  if (manual) refreshFailures = 0
  activeRequest?.abort()
  const controller = new AbortController()
  activeRequest = controller
  loading.value = true
  try {
    const current = await creatorApi.get(id, controller.signal)
    if (stopped || controller.signal.aborted) return
    if (current.version !== video.value?.version || current.moderationBlocked) { preview.value = undefined; previewError.value = '' }
    video.value = current
    needsRefresh.value = false
    error.value = ''
    refreshFailures = 0
    if (manual || !polling.value) void loadHistory()
  } catch (failure) {
    if (!stopped && !controller.signal.aborted) {
      refreshFailures++
      error.value = explain(failure)
      needsRefresh.value = true
      preview.value = undefined
      if (failure instanceof ApiFailure && failure.status === 404) video.value = undefined
    }
  } finally {
    if (!stopped && !controller.signal.aborted) { loading.value = false; schedule() }
  }
}
async function loadHistory(reviewPage = historyPage.value, blockPage = moderationPage.value) {
  historyRequest?.abort()
  const controller = new AbortController()
  historyRequest = controller
  historyLoading.value = true
  historyError.value = ''
  const results = await Promise.allSettled([
    creatorApi.reviews(id, reviewPage, controller.signal), creatorApi.moderation(id, blockPage, controller.signal),
  ])
  if (stopped || controller.signal.aborted) return
  const [reviewResult, blockResult] = results
  if (reviewResult.status === 'fulfilled') { reviews.value = reviewResult.value.items; historyTotal.value = reviewResult.value.total; historyPage.value = reviewPage }
  else { reviews.value = []; historyError.value = explain(reviewResult.reason) }
  if (blockResult.status === 'fulfilled') { moderation.value = blockResult.value.items; moderationTotal.value = blockResult.value.total; moderationPage.value = blockPage }
  else { moderation.value = []; historyError.value = explain(blockResult.reason) }
  historyLoading.value = false
}
async function command(action: CreatorCommand) {
  if (!video.value || blocked.value) return
  if (action === 'offline' && !window.confirm('下架后将不再出现在公开列表。确定下架吗？')) return
  busy.value = true
  error.value = ''
  notice.value = ''
  const snapshot = video.value
  try {
    video.value = await creatorApi.command(snapshot, action)
    preview.value = undefined
    notice.value = '操作已完成，作品状态已更新。'
    void loadHistory(1, 1)
  } catch (failure) {
    error.value = explain(failure)
    needsRefresh.value = true
    if (failure instanceof ApiFailure && failure.status === 409) error.value += '。请刷新状态后再操作。'
    else error.value += '。请先刷新状态确认结果，不要连续重复提交。'
  } finally { busy.value = false; schedule() }
}
function focusUpload() {
  uploadPanel.value?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  fileInput.value?.focus()
}
function chooseFile(event: Event) {
  file.value = undefined
  const selected = (event.target as HTMLInputElement).files?.[0]
  if (!selected) return
  const problem = validateVideoFile(selected)
  if (problem) error.value = problem
  else { file.value = selected; error.value = '' }
}
async function confirmUpload() {
  try {
    const state = await creatorApi.uploadState(id)
    if (stopped) return
    uploadUnknown.value = false
    notice.value = state.sourceUploaded ? '源文件已绑定，正在等待处理，请勿重复上传。' : '尚未绑定源文件，可按当前版本重新上传。'
    await load()
  } catch (failure) {
    if (!stopped) { uploadUnknown.value = true; error.value = `${explain(failure)}。上传结果仍未确认，请稍后查询；管理员需确认上传服务已启用。` }
  }
}
async function upload() {
  if (!video.value || !file.value || blocked.value || uploadUnknown.value) return
  busy.value = true
  uploading.value = true
  uploadUnknown.value = true
  error.value = ''
  notice.value = ''
  const controller = new AbortController()
  uploadRequest = controller
  const deadline = setTimeout(() => controller.abort(), 180000)
  try {
    await creatorApi.upload(video.value, file.value, controller.signal)
    if (stopped) return
    uploadUnknown.value = false
    file.value = undefined
    if (fileInput.value) fileInput.value.value = ''
    notice.value = '上传已完成，等待视频处理；处理完成后才能送审。'
    await load()
  } catch (failure) {
    if (stopped) return
    error.value = controller.signal.aborted ? '上传已停止或等待超时，正在核对服务端状态。' : explain(failure)
    await confirmUpload()
  } finally {
    clearTimeout(deadline)
    if (!stopped) { busy.value = false; uploading.value = false; schedule() }
  }
}
async function cancelProcessing() {
  if (!video.value || blocked.value || deleteBusy.value) return
  if (!window.confirm('取消后当前处理任务会停止，可在失败状态下手动重试。确认取消处理？')) return
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    video.value = await creatorApi.cancelProcessing(video.value)
    notice.value = '处理已取消，可在失败状态下手动重试。'
  } catch (failure) { if (!stopped) error.value = explain(failure) }
  finally { if (!stopped) { busy.value = false; schedule() } }
}
async function remove() {
  if (!video.value || busy.value || downloadBusy.value || deleteBusy.value) return
  if (!window.confirm('删除后作品和源文件将进入回收流程，且不可恢复。确认删除？')) return
  deleteBusy.value = true
  error.value = ''
  try {
    await creatorApi.remove(video.value)
    await router.push('/studio')
  } catch (failure) { if (!stopped) error.value = explain(failure) }
  finally { if (!stopped) deleteBusy.value = false }
}
async function loadCollections() {
  try { const page = await creatorApi.collections(); if (!stopped) collections.value = page.items }
  catch { /* the panel stays hidden if collections cannot load */ }
}
async function addToCollection() {
  if (!video.value || !collectionId.value || collectionBusy.value) return
  collectionBusy.value = true; error.value = ''
  try { await creatorApi.addToCollection(collectionId.value, video.value.id); notice.value = '已加入合集。' }
  catch (failure) { if (!stopped) error.value = explain(failure) }
  finally { if (!stopped) collectionBusy.value = false }
}
async function download() {
  if (!video.value || blocked.value || downloadBusy.value) return
  downloadBusy.value = true
  error.value = ''
  try {
    const signed = await creatorApi.download(id)
    window.location.assign(signed.url)
  } catch (failure) { if (!stopped) error.value = explain(failure) }
  finally { if (!stopped) downloadBusy.value = false }
}
async function openPreview() {
  if (blocked.value) return
  busy.value = true
  preview.value = undefined
  previewError.value = ''
  try {
    const signed = await creatorApi.preview(id)
    const address = new URL(signed.url)
    if (!['https:', 'http:'].includes(address.protocol) || address.username || address.password) throw new Error('预览地址无效，请联系管理员')
    if (!stopped) preview.value = signed
  } catch (failure) { if (!stopped) previewError.value = explain(failure) }
  finally { if (!stopped) busy.value = false }
}
async function saved() {
  editorBusy.value = false
  editing.value = false
  notice.value = '作品信息已保存。修改后的内容需要重新提交审核。'
  await load()
}
function canLeave() { return !(busy.value || editorBusy.value) || window.confirm('有请求正在进行，离开后结果可能未确认。仍要离开吗？') }
function leave(event: BeforeUnloadEvent) { if (busy.value || editorBusy.value) event.preventDefault() }
function visibility() { if (document.hidden) clearTimeout(timer); else schedule() }
onBeforeRouteLeave(canLeave)
onBeforeRouteUpdate(canLeave)
onMounted(() => {
  if (/^[1-9]\d{0,18}$/.test(id)) void load()
  else error.value = '作品编号无效'
  window.addEventListener('beforeunload', leave)
  void loadCollections()
  document.addEventListener('visibilitychange', visibility)
})
onBeforeUnmount(() => {
  stopped = true
  activeRequest?.abort(); uploadRequest?.abort(); historyRequest?.abort(); clearTimeout(timer)
  preview.value = undefined
  window.removeEventListener('beforeunload', leave)
  document.removeEventListener('visibilitychange', visibility)
})
</script>

<template>
  <WorkspaceShell>
    <RouterLink class="back-link" to="/studio">← 我的作品</RouterLink>
    <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" />
    <el-alert v-if="notice" :title="notice" type="success" :closable="false" role="status" />
    <div class="studio-heading"><div><p class="eyebrow">FRAME ROOM / WORK</p><h1>{{ video?.title ?? '作品详情' }}</h1></div><el-button :loading="loading" :disabled="busy || editorBusy || editing" @click="load()">刷新状态</el-button></div>
    <p v-if="!video && loading" class="empty-state" role="status">正在读取作品…</p>
    <template v-if="video">
      <section class="studio-panel">
        <div class="status-group"><el-tag :type="display?.tone">{{ display?.label }}</el-tag><span class="visibility-note">{{ display?.visibilityLabel }}</span></div>
        <p class="video-description">{{ video.description || '暂未填写简介' }}</p>
        <p class="status-detail">{{ display?.detail }}</p>
        <p v-if="video.nextAttemptAt && video.taskState === 'RETRY_WAIT'" class="muted">预计下次处理：{{ formatTime(video.nextAttemptAt) }}</p>
        <p v-if="polling" class="muted" role="status">页面可见时每 10 秒查询一次状态；连续失败三次后暂停，可手动刷新。</p>
        <div class="inline-actions primary-actions" v-if="!editing">
          <el-button v-if="display?.key === 'NEEDS_UPLOAD'" type="primary" :disabled="blocked" @click="focusUpload">上传视频</el-button>
          <el-button v-else-if="display?.key === 'READY_TO_SUBMIT'" type="primary" :disabled="blocked" @click="command('review-submissions')">提交审核</el-button>
          <el-button v-else-if="display?.key === 'FAILED' && video.retryAllowed" type="primary" :disabled="blocked" @click="command('processing-retry')">重新处理</el-button>
          <el-button v-else-if="display?.key === 'PENDING_REVIEW'" type="primary" :disabled="blocked" @click="command('review-withdrawal')">撤回审核</el-button>
          <el-button v-else-if="display?.key === 'REJECTED'" type="primary" :disabled="blocked" @click="editing = true">修改后重新提交</el-button>
          <el-button v-else-if="display?.key === 'PUBLISHED'" type="primary" :disabled="blocked" @click="openPreview">{{ preview ? '重新播放' : '播放我的作品' }}</el-button>
        </div>
        <details v-if="!editing" class="more-actions">
          <summary>更多操作</summary>
          <div class="inline-actions">
            <el-button v-if="editable && display?.key !== 'REJECTED'" :disabled="blocked" @click="editing = true">编辑信息</el-button>
            <el-button v-if="ready && display?.key !== 'PUBLISHED'" :disabled="blocked" @click="openPreview">{{ preview ? '重新播放' : '播放我的作品' }}</el-button>
            <el-button v-if="display?.key === 'PROCESSING'" :loading="busy" :disabled="blocked" @click="cancelProcessing">取消处理</el-button>
            <el-button v-if="display?.key === 'PUBLISHED'" :disabled="blocked" @click="command('offline')">下架作品</el-button>
            <el-button v-if="display?.key === 'TAKEN_DOWN'" :disabled="blocked" @click="command('publication')">再次发布</el-button>
            <el-button v-if="ready" :loading="downloadBusy" :disabled="blocked" @click="download">下载源文件</el-button>
            <el-button type="danger" :loading="deleteBusy" :disabled="blocked" @click="remove">删除作品</el-button>
          </div>
        </details>
      </section>
      <VideoEditor v-if="editing" :key="video.id" :video="video" @saved="saved" @cancel="editing = false; load()" @busy="editorBusy = $event" />
      <section v-if="draft && !editing" ref="uploadPanel" class="studio-panel">
        <h2>上传源视频</h2><p class="muted">MP4 / H.264 / yuv420p，可含一条 AAC 音频；最长 10 分钟、最高 60 帧，每边不超过 1920 像素且总像素不超过 1920 × 1080。最大 200 MiB（以服务端限制为准）。上传成功不代表已审核发布。</p>
        <label class="file-picker" for="source-video">选择视频文件</label><input id="source-video" ref="fileInput" type="file" accept=".mp4,video/mp4" :disabled="blocked || uploadUnknown" @change="chooseFile" />
        <p v-if="file" class="muted">{{ file.name }} · {{ (file.size / 1024 / 1024).toFixed(2) }} MiB</p>
        <div class="inline-actions"><el-button type="primary" :loading="uploading" :disabled="!file || blocked || uploadUnknown" @click="upload">上传视频</el-button><el-button v-if="uploading" @click="uploadRequest?.abort()">停止上传并核对状态</el-button><el-button v-if="uploadUnknown && !uploading" :disabled="blocked" @click="confirmUpload">查询上传结果</el-button></div>
        <p v-if="uploading" role="status" class="muted">正在上传，请保持页面打开。完成后会核对作品状态。</p>
      </section>
      <section v-if="preview || previewError" class="studio-panel">
        <h2>本人预览</h2><el-alert v-if="previewError" :title="previewError" type="error" :closable="false" role="alert" />
        <video v-if="preview" :key="preview.url" :src="preview.url" controls preload="metadata" class="studio-player" @error="previewError = '视频加载失败，链接可能已过期或存储不可达。请重新获取预览，仍失败时联系管理员。'"></video>
        <p v-if="preview" class="muted">链接到期：{{ formatTime(preview.expiresAt) }}。过期或拖动失败时请重新获取预览。</p>
      </section>
      <section v-if="!editing && collections.length" class="studio-panel">
        <h2>加入合集</h2><p class="muted">把作品整理到自己的系列中；公开合集会在作者主页展示。</p>
        <div class="inline-actions"><select id="collection-select" v-model="collectionId"><option value="" disabled>选择合集</option><option v-for="item in collections" :key="item.id" :value="item.id">{{ item.title }}</option></select><el-button :loading="collectionBusy" :disabled="!collectionId" @click="addToCollection">加入合集</el-button><RouterLink to="/studio/collections">管理合集 →</RouterLink></div>
      </section>
      <section class="studio-panel" :aria-busy="historyLoading">
        <div class="section-heading"><h2>审核记录</h2><el-button :disabled="historyLoading" @click="loadHistory()">刷新记录</el-button></div>
        <el-alert v-if="historyError" :title="historyError" type="error" :closable="false" role="alert" />
        <article v-for="record in reviews" :key="record.id" class="history-item"><div class="section-heading"><strong>第 {{ record.reviewRound }} 轮 · {{ record.outcome === 'APPROVED' ? '通过' : '驳回' }}</strong><time>{{ formatTime(record.createdAt) }}</time></div><p>{{ record.title }}</p><p class="history-reason">{{ record.reason || '未附加说明' }}</p></article>
        <p v-if="!reviews.length && !historyLoading" class="muted">暂无审核记录。</p>
        <div v-if="historyTotal > 10" class="page-controls"><el-button :disabled="historyLoading || historyPage <= 1" @click="loadHistory(historyPage - 1)">上一页审核</el-button><span>第 {{ historyPage }} 页</span><el-button :disabled="historyLoading || historyPage * 10 >= historyTotal || historyPage >= 1000" @click="loadHistory(historyPage + 1)">下一页审核</el-button></div>
        <h2 class="history-subheading">封禁与解禁记录</h2>
        <article v-for="record in moderation" :key="record.id" class="history-item"><div class="section-heading"><strong>{{ record.action === 'VIDEO_BLOCKED' ? '封禁' : '解禁' }}</strong><time>{{ formatTime(record.createdAt) }}</time></div><p class="history-reason">{{ record.reason }}</p></article>
        <p v-if="!moderation.length && !historyLoading" class="muted">暂无封禁或解禁记录。</p>
        <div v-if="moderationTotal > 10" class="page-controls"><el-button :disabled="historyLoading || moderationPage <= 1" @click="loadHistory(historyPage, moderationPage - 1)">上一页处理</el-button><span>第 {{ moderationPage }} 页</span><el-button :disabled="historyLoading || moderationPage * 10 >= moderationTotal || moderationPage >= 1000" @click="loadHistory(historyPage, moderationPage + 1)">下一页处理</el-button></div>
      </section>
    </template>
  </WorkspaceShell>
</template>
