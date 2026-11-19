<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import WorkspaceShell from '../components/WorkspaceShell.vue'
import { adminApi } from '../api/admin'
import type { CreatorVideo, ReviewRecord, SignedMedia } from '../api/creator'
import { formatTime, processingLabels, publicationLabels, reviewLabels } from '../api/creator'
import { useStudioError } from '../composables/useStudioError'

const explain = useStudioError()
const items = ref<{ ownerId: string; video: CreatorVideo }[]>([]); const total = ref(0); const page = ref(1)
const selected = ref<CreatorVideo>(); const history = ref<ReviewRecord[]>([]); const media = ref<SignedMedia>()
const historyPage = ref(1); const historyTotal = ref(0); const historyLoading = ref(false)
const reason = ref(''); const loading = ref(false); const busy = ref(false); const error = ref(''); const notice = ref('')
const needsRefresh = ref(false)
const previewBusy = ref(false)
const downloadBusy = ref(false)
let queueRequest: AbortController | undefined
let selectionRequest: AbortController | undefined
let previewRequest: AbortController | undefined
let historyRequest: AbortController | undefined
let stopped = false
const queueLabel = computed(() => total.value ? `${total.value} 个待审核作品` : '审核队列为空')
function clearSelectionRequests() {
  selectionRequest?.abort()
  previewRequest?.abort()
  historyRequest?.abort()
  historyLoading.value = false
  previewBusy.value = false
  media.value = undefined
  history.value = []
}
async function select(video: CreatorVideo, preserveReason = false) {
  if (busy.value) return
  clearSelectionRequests()
  const controller = new AbortController()
  selectionRequest = controller
  selected.value = video
  needsRefresh.value = true
  if (!preserveReason) reason.value = ''
  error.value = ''
  try {
    const current = await adminApi.pending(video.id, controller.signal)
    if (stopped || controller.signal.aborted) return
    selected.value = current
    needsRefresh.value = false
    const result = await adminApi.history(video.id, 1, controller.signal)
    if (!stopped && !controller.signal.aborted) {
      history.value = result.items
      historyTotal.value = result.total
      historyPage.value = 1
    }
  } catch (failure) { if (!stopped && !controller.signal.aborted) error.value = explain(failure) }
}
async function load(next = page.value) {
  if (busy.value) return
  queueRequest?.abort()
  const controller = new AbortController()
  queueRequest = controller
  clearSelectionRequests()
  needsRefresh.value = true
  loading.value = true
  error.value = ''
  items.value = []
  try {
    const result = await adminApi.queue(next, controller.signal)
    if (stopped || controller.signal.aborted) return
    items.value = result.items
    total.value = result.total
    page.value = next
    const existing = selected.value && result.items.find(item => item.video.id === selected.value?.id)
    if (existing) await select(existing.video, true)
    else {
      selected.value = undefined
      reason.value = ''
      if (result.items[0]) await select(result.items[0].video)
    }
  } catch (failure) { if (!stopped && !controller.signal.aborted) error.value = explain(failure) }
  finally { if (!stopped && !controller.signal.aborted) loading.value = false }
}
async function decide(outcome: 'APPROVED' | 'REJECTED') {
  if (!selected.value || busy.value || loading.value || needsRefresh.value) return
  if (outcome === 'REJECTED' && !reason.value.trim()) { error.value = '驳回必须填写原因'; return }
  if (!window.confirm(outcome === 'APPROVED' ? '确认通过并发布这部作品？' : '确认驳回这部作品？')) return
  const snapshot = selected.value
  busy.value = true
  clearSelectionRequests()
  error.value = ''
  notice.value = ''
  let succeeded = false
  try {
    await adminApi.decide(snapshot, outcome, reason.value.trim())
    if (stopped) return
    notice.value = outcome === 'APPROVED' ? '已通过审核，作品进入已发布状态。' : '已驳回，作者可修改后重新送审。'
    selected.value = undefined
    reason.value = ''
    succeeded = true
  } catch (failure) {
    if (!stopped) { needsRefresh.value = true; error.value = `${explain(failure)}。请刷新队列核对结果后再操作，当前输入暂保留。` }
  } finally { if (!stopped) busy.value = false }
  if (succeeded && !stopped) await load(1)
}
async function download() {
  if (!selected.value || busy.value || loading.value || needsRefresh.value || downloadBusy.value) return
  downloadBusy.value = true
  error.value = ''
  try {
    const signed = await adminApi.download(selected.value)
    window.location.assign(signed.url)
  } catch (failure) { if (!stopped) error.value = explain(failure) }
  finally { if (!stopped) downloadBusy.value = false }
}
async function loadHistory(next = historyPage.value) {
  if (!selected.value || busy.value || loading.value) return
  historyRequest?.abort()
  const controller = new AbortController()
  historyRequest = controller
  historyLoading.value = true
  try {
    const result = await adminApi.history(selected.value.id, next, controller.signal)
    if (!stopped && !controller.signal.aborted) {
      history.value = result.items
      historyPage.value = next
      historyTotal.value = result.total
    }
  } catch (failure) { if (!stopped && !controller.signal.aborted) error.value = explain(failure) }
  finally { if (!stopped && !controller.signal.aborted) historyLoading.value = false }
}
async function preview() {
  if (!selected.value || busy.value || loading.value || needsRefresh.value || previewBusy.value) return
  const snapshot = selected.value
  const controller = new AbortController()
  previewRequest?.abort()
  previewRequest = controller
  previewBusy.value = true
  media.value = undefined
  try {
    const result = await adminApi.preview(snapshot, controller.signal)
    if (!stopped && !controller.signal.aborted && selected.value?.id === snapshot.id && selected.value.version === snapshot.version) media.value = result
  } catch (failure) { if (!stopped && !controller.signal.aborted) error.value = explain(failure) }
  finally { if (!stopped && !controller.signal.aborted) previewBusy.value = false }
}
function leave(event: BeforeUnloadEvent) { if (busy.value) event.preventDefault() }
onBeforeRouteLeave(() => !busy.value || window.confirm('审核请求尚未完成，离开后需核对结果。确认离开？'))
onMounted(() => { void load(); window.addEventListener('beforeunload', leave) })
onBeforeUnmount(() => { stopped = true; queueRequest?.abort(); clearSelectionRequests(); historyRequest?.abort(); window.removeEventListener('beforeunload', leave) })
</script>

<template>
  <WorkspaceShell>
    <div class="studio-heading"><div><p class="eyebrow">FRAME ROOM / MODERATION</p><h1>审核工作台</h1><p class="muted">先看作品内容，再决定是否让它进入公开空间。{{ queueLabel }}</p></div></div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert"/><el-alert v-if="notice" :title="notice" type="success" :closable="false" role="status"/>
    <div class="admin-layout">
      <section class="studio-panel review-queue" :aria-busy="loading"><div class="section-heading"><h2>待审核 <span class="count">{{ total }}</span></h2><el-button :loading="loading" :disabled="busy" @click="load()">刷新</el-button></div>
        <p v-if="loading" class="empty-state">正在读取审核队列…</p><button v-for="item in items" v-else :key="item.video.id" class="queue-item" :disabled="busy || loading" :class="{selected:selected?.id === item.video.id}" @click="select(item.video)"><span><strong>{{ item.video.title }}</strong><small>作者 #{{ item.ownerId }} · 第 {{ item.video.reviewRound }} 轮</small></span><time>{{ formatTime(item.video.reviewSubmittedAt) }}</time></button><p v-if="!loading && !items.length" class="empty-state">暂无待审核作品。</p>
        <div class="page-controls"><el-button :disabled="busy || loading || page <= 1" @click="load(page - 1)">上一页</el-button><span>第 {{ page }} 页</span><el-button :disabled="busy || loading || page * 20 >= total || page >= 1000" @click="load(page + 1)">下一页</el-button></div>
      </section>
      <section class="studio-panel review-detail" v-if="selected"><div class="review-detail-head"><div><p class="card-number">作品 #{{ selected.id }} · 作者作品</p><h2>{{ selected.title }}</h2></div><el-tag>{{ reviewLabels[selected.reviewStatus] }}</el-tag></div><p class="video-description">{{ selected.description || '作者暂未填写简介。' }}</p><dl class="video-facts"><div><dt>处理状态</dt><dd>{{ processingLabels[selected.processingStatus] }}</dd></div><div><dt>视频时长</dt><dd>{{ selected.durationMillis ? `${(selected.durationMillis / 1000).toFixed(1)} 秒` : '—' }}</dd></div><div><dt>发布状态</dt><dd>{{ publicationLabels[selected.publicationStatus] }}</dd></div></dl><div class="inline-actions"><el-button :loading="previewBusy" :disabled="busy || loading || needsRefresh" @click="preview">{{ media ? '重新获取预览' : '预览视频' }}</el-button><el-button :loading="downloadBusy" :disabled="busy || loading || needsRefresh" @click="download">下载源文件</el-button><el-button type="primary" :loading="busy" :disabled="loading || needsRefresh" @click="decide('APPROVED')">通过审核</el-button><el-button type="danger" :loading="busy" :disabled="loading || needsRefresh" @click="decide('REJECTED')">驳回</el-button></div><video v-if="media" class="studio-player admin-player" :src="media.url" controls preload="metadata" @error="error = '视频无法加载或链接已过期，请重新获取预览。'"/><p v-if="media" class="muted">预览链接到期：{{ formatTime(media.expiresAt) }}</p><label class="reason-label" for="review-reason">处理说明 <span>驳回时必填</span></label><textarea id="review-reason" v-model="reason" :disabled="busy" maxlength="500" rows="4" placeholder="告诉作者需要修改什么，或记录本次审核依据。"/><h3 class="history-subheading">历史审核</h3><article v-for="record in history" :key="record.id" class="history-item"><div class="section-heading"><strong>第 {{ record.reviewRound }} 轮 · {{ record.outcome === 'APPROVED' ? '通过' : '驳回' }}</strong><time>{{ formatTime(record.createdAt) }}</time></div><p>{{ record.reason || '未附加说明' }}</p></article><p v-if="historyLoading" class="muted" role="status">正在读取历史记录…</p><p v-if="!history.length && !historyLoading" class="muted">暂无历史记录。</p><div v-if="historyTotal > 20" class="page-controls"><el-button :disabled="historyLoading || busy || historyPage <= 1" @click="loadHistory(historyPage - 1)">上一页历史</el-button><span>第 {{ historyPage }} 页</span><el-button :disabled="historyLoading || busy || historyPage * 20 >= historyTotal || historyPage >= 1000" @click="loadHistory(historyPage + 1)">下一页历史</el-button></div></section>
      <section v-else class="studio-panel empty-state">从左侧选择一部待审核作品。</section>
    </div>
  </WorkspaceShell>
</template>
