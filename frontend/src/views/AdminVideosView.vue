<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import WorkspaceShell from '../components/WorkspaceShell.vue'
import { adminApi } from '../api/admin'
import type { AdminModerationRecord, ManagedVideo } from '../api/admin'
import { formatTime } from '../api/creator'
import { adminStatus } from '../api/videoStatus'
import { useStudioError } from '../composables/useStudioError'

const explain = useStudioError()
const items = ref<ManagedVideo[]>([])
const page = ref(1)
const total = ref(0)
const loading = ref(false)
const selected = ref<ManagedVideo>()
const selectedId = ref('')
const detailLoading = ref(false)
const busy = ref(false)
const needsRefresh = ref(false)
const reason = ref('')
const error = ref('')
const detailError = ref('')
const notice = ref('')
const records = ref<AdminModerationRecord[]>([])
const historyPage = ref(1)
const historyTotal = ref(0)
const historyLoading = ref(false)
const historyError = ref('')
let listRequest: AbortController | undefined
let detailRequest: AbortController | undefined
let historyRequest: AbortController | undefined
let stopped = false

async function load(next = page.value) {
  if (busy.value) return
  listRequest?.abort()
  const controller = new AbortController()
  listRequest = controller
  loading.value = true
  error.value = ''
  items.value = []
  try {
    const result = await adminApi.managed(next, AbortSignal.any([controller.signal, AbortSignal.timeout(30000)]))
    if (controller.signal.aborted || stopped) return
    items.value = result.items
    total.value = result.total
    page.value = next
  } catch (failure) { if (!controller.signal.aborted && !stopped) error.value = explain(failure) }
  finally { if (!controller.signal.aborted && !stopped) loading.value = false }
}

async function history(next = 1) {
  historyRequest?.abort()
  const controller = new AbortController()
  historyRequest = controller
  historyLoading.value = true
  historyError.value = ''
  records.value = []
  try {
    const result = await adminApi.moderationHistory(selectedId.value, next, controller.signal)
    if (controller.signal.aborted || stopped) return
    records.value = result.items
    historyPage.value = next
    historyTotal.value = result.total
  } catch (failure) { if (!controller.signal.aborted && !stopped) historyError.value = explain(failure) }
  finally { if (!controller.signal.aborted && !stopped) historyLoading.value = false }
}

async function select(id: string) {
  if (busy.value) return
  if (id !== selectedId.value) { reason.value = ''; notice.value = '' }
  selectedId.value = id
  selected.value = undefined
  needsRefresh.value = true
  detailRequest?.abort()
  historyRequest?.abort()
  records.value = []
  historyTotal.value = 0
  historyLoading.value = false
  historyError.value = ''
  const controller = new AbortController()
  detailRequest = controller
  detailLoading.value = true
  detailError.value = ''
  try {
    const result = await adminApi.managedDetail(id, controller.signal)
    if (controller.signal.aborted || stopped) return
    selected.value = result
    needsRefresh.value = false
    void history()
  } catch (failure) { if (!controller.signal.aborted && !stopped) detailError.value = explain(failure) }
  finally { if (!controller.signal.aborted && !stopped) detailLoading.value = false }
}

async function moderate() {
  if (!selected.value || busy.value || loading.value || detailLoading.value || needsRefresh.value) return
  const explanation = reason.value.trim()
  if (!explanation || explanation.length > 500) { detailError.value = '请填写 1～500 字的处理原因。'; return }
  const snapshot = selected.value.video
  const unblock = snapshot.moderationBlocked
  if (!window.confirm(unblock ? '解禁后作品仍需作者重新送审，确认解禁？' : '封禁后作品将退出公开展示，确认封禁？')) return
  busy.value = true
  detailError.value = ''
  notice.value = ''
  try {
    const result = await (unblock ? adminApi.unblock(snapshot, explanation) : adminApi.block(snapshot, explanation))
    if (stopped) return
    selected.value = { ...selected.value, video: result }
    items.value = items.value.map(item => item.video.id === result.id ? { ...item, video: result } : item)
    reason.value = ''
    notice.value = unblock ? '已解禁，作者需要重新提交审核。' : '已封禁，作品不再公开展示。'
    void history()
  } catch (failure) {
    if (stopped) return
    needsRefresh.value = true
    detailError.value = `${explain(failure)}。请刷新作品状态核对结果后再操作，填写的原因已保留。`
  } finally { if (!stopped) busy.value = false }
}

async function remove() {
  if (!selected.value || busy.value || loading.value || detailLoading.value || needsRefresh.value) return
  const explanation = reason.value.trim()
  if (!explanation || explanation.length > 500) { detailError.value = '请填写 1～500 字的删除原因。'; return }
  const snapshot = selected.value.video
  if (!window.confirm('删除后作品和源文件将进入回收流程，且不可恢复。确认删除？')) return
  busy.value = true
  detailError.value = ''
  notice.value = ''
  let succeeded = false
  try {
    await adminApi.remove(snapshot, explanation)
    notice.value = '作品已删除，资源进入回收流程。'
    selected.value = undefined
    selectedId.value = ''
    reason.value = ''
    succeeded = true
  } catch (failure) {
    if (!stopped) { needsRefresh.value = true; detailError.value = `${explain(failure)}。请刷新状态核对结果。` }
  } finally { if (!stopped) busy.value = false }
  if (succeeded && !stopped) await load(1)
}

function beforeUnload(event: BeforeUnloadEvent) { if (busy.value) event.preventDefault() }
onBeforeRouteLeave(() => !busy.value || window.confirm('处理请求尚未结束，离开后需重新查询结果。确认离开？'))
onMounted(() => { void load(); window.addEventListener('beforeunload', beforeUnload) })
onBeforeUnmount(() => {
  stopped = true
  listRequest?.abort(); detailRequest?.abort(); historyRequest?.abort()
  window.removeEventListener('beforeunload', beforeUnload)
})
</script>

<template>
  <WorkspaceShell>
    <div class="studio-heading">
      <div><p class="eyebrow">FRAME ROOM / MODERATION</p><h1>作品管理</h1><p class="muted">管理处理完成的作品，查看封禁与解禁记录。</p></div>
      <div class="inline-actions"><RouterLink to="/admin/reviews">待审核队列 →</RouterLink><RouterLink to="/admin/processing">处理中作品 →</RouterLink></div>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" />
    <div class="management-layout">
      <section class="studio-panel" :aria-busy="loading" aria-label="可管理作品">
        <div class="section-heading"><h2>作品列表 <span class="count">{{ total }}</span></h2><el-button :loading="loading" :disabled="busy" @click="load()">刷新列表</el-button></div>
        <p v-if="loading" role="status">正在读取作品…</p>
        <button v-for="item in items" :key="item.video.id" class="managed-row" :class="{ active: selectedId === item.video.id }" :disabled="busy" :aria-pressed="selectedId === item.video.id" @click="select(item.video.id)">
          <strong>{{ item.video.title }}</strong><span>作者 #{{ item.ownerId }} · {{ adminStatus(item.video).label }}</span>
        </button>
        <p v-if="!loading && !error && !items.length" class="empty-state">本页没有处理完成的作品。</p>
        <div class="page-controls"><el-button :disabled="loading || busy || page <= 1" @click="load(page - 1)">上一页</el-button><span>{{ page }}</span><el-button :disabled="loading || busy || !!error || page * 20 >= total || page >= 1000" @click="load(page + 1)">下一页</el-button></div>
      </section>
      <section class="studio-panel" aria-label="作品处理" :aria-busy="detailLoading">
        <div class="section-heading"><h2>作品处理</h2><el-button v-if="selectedId" :loading="detailLoading" :disabled="busy" @click="select(selectedId)">刷新作品状态</el-button></div>
        <el-alert v-if="detailError" :title="detailError" type="error" :closable="false" role="alert" />
        <p v-if="notice" role="status">{{ notice }}</p>
        <p v-if="detailLoading" role="status">正在核对作品状态…</p>
        <template v-if="selected">
          <h3>{{ selected.video.title }}</h3>
          <p class="muted">作品 #{{ selected.video.id }} · 作者 #{{ selected.ownerId }} · 版本 {{ selected.video.version }}</p>
          <div class="status-group"><el-tag :type="adminStatus(selected.video).tone">{{ adminStatus(selected.video).label }}</el-tag><el-tag type="info">{{ adminStatus(selected.video).visibilityLabel }}</el-tag></div><p class="muted">{{ adminStatus(selected.video).detail }}</p>
          <p class="video-description">{{ selected.video.description || '作者未填写简介。' }}</p>
          <form class="studio-form" @submit.prevent="moderate">
            <label for="moderation-reason">{{ selected.video.moderationBlocked ? '解禁原因' : '封禁原因' }}（必填）</label>
            <textarea id="moderation-reason" v-model="reason" required maxlength="500" rows="4" :disabled="busy" />
            <p class="muted">解禁不会自动发布，作者需要重新送审。</p>
            <el-button native-type="submit" :type="selected.video.moderationBlocked ? 'primary' : 'danger'" :loading="busy" :disabled="loading || detailLoading || needsRefresh">{{ selected.video.moderationBlocked ? '确认解禁' : '确认封禁' }}</el-button><el-button type="danger" :loading="busy" :disabled="loading || detailLoading || needsRefresh" @click="remove">删除作品</el-button>
          </form>
          <div class="section-heading history-subheading"><h2>处理记录</h2><el-button :disabled="historyLoading || busy" @click="history(historyPage)">刷新记录</el-button></div>
          <el-alert v-if="historyError" :title="historyError" type="error" :closable="false" role="alert" />
          <p v-if="historyLoading" role="status">正在加载记录…</p>
          <article v-for="record in records" :key="record.id" class="history-item"><strong>{{ record.action === 'VIDEO_BLOCKED' ? '封禁' : record.action === 'VIDEO_UNBLOCKED' ? '解禁' : '处理记录' }} · 管理员 #{{ record.actorId }}</strong><p class="history-reason">{{ record.reason }}</p><time>{{ formatTime(record.createdAt) }}</time></article>
          <p v-if="!historyLoading && !historyError && !records.length" class="muted">暂无处理记录。</p>
          <div v-if="historyTotal > 20" class="page-controls"><el-button :disabled="historyLoading || busy || historyPage <= 1" @click="history(historyPage - 1)">上一页</el-button><span>{{ historyPage }}</span><el-button :disabled="historyLoading || busy || historyPage * 20 >= historyTotal || historyPage >= 1000" @click="history(historyPage + 1)">下一页</el-button></div>
        </template>
        <p v-else-if="!selectedId" class="empty-state">选择一部作品，查看状态和处理记录。</p>
      </section>
    </div>
  </WorkspaceShell>
</template>

<style scoped>
.management-layout { display: grid; grid-template-columns: minmax(260px, 1fr) minmax(0, 1.6fr); gap: 24px; align-items: start; }
.management-layout .studio-panel { min-width: 0; margin-top: 0; }
.management-layout h3 { overflow-wrap: anywhere; }
.managed-row { display: block; width: 100%; text-align: left; padding: 16px 12px; border: 0; border-bottom: 1px solid #d9ddd5; background: transparent; cursor: pointer; color: #182522; }
.managed-row.active { background: #edf4f0; box-shadow: inset 3px 0 #245e52; }
.managed-row strong, .managed-row span { display: block; overflow-wrap: anywhere; }
.managed-row span { font-size: 12px; margin-top: 8px; }
.managed-row:disabled { cursor: wait; opacity: .65; }
@media (max-width: 900px) { .management-layout { grid-template-columns: 1fr; } }
</style>
