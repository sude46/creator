<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import WorkspaceShell from '../components/WorkspaceShell.vue'
import { adminApi } from '../api/admin'
import type { HlsBackfillStatus, ManagedVideo } from '../api/admin'
import { formatTime } from '../api/creator'
import { adminStatus } from '../api/videoStatus'
import { useStudioError } from '../composables/useStudioError'

const explain = useStudioError()
const items = ref<ManagedVideo[]>([])
const page = ref(1)
const total = ref(0)
const loading = ref(false)
const busy = ref(false)
const error = ref('')
const notice = ref('')
const selectedId = ref('')
const hlsStatus = ref<HlsBackfillStatus>()
const hlsBusy = ref(false)
const hlsError = ref('')
const reason = ref('')
let activeRequest: AbortController | undefined
let stopped = false

async function load(next = page.value) {
  if (busy.value) return
  activeRequest?.abort()
  const controller = new AbortController()
  activeRequest = controller
  loading.value = true
  error.value = ''
  items.value = []
  try {
    const result = await adminApi.processing(next, controller.signal)
    if (stopped || controller.signal.aborted) return
    items.value = result.items
    total.value = result.total
    page.value = result.page
    if (!result.items.some(item => item.video.id === selectedId.value)) {
      selectedId.value = result.items[0]?.video.id ?? ''
      reason.value = ''
    }
  } catch (failure) { if (!stopped && !controller.signal.aborted) error.value = explain(failure) }
  finally { if (!stopped && !controller.signal.aborted) loading.value = false }
}

function select(item: ManagedVideo) {
  if (busy.value) return
  selectedId.value = item.video.id
  reason.value = ''
  notice.value = ''
}

const selected = computed(() => items.value.find(item => item.video.id === selectedId.value))
const selectedActive = computed(() => !!selected.value && ['PENDING', 'PROCESSING'].includes(selected.value.video.processingStatus))

async function cancel() {
  const item = selected.value
  if (!item || busy.value || loading.value) return
  const explanation = reason.value.trim()
  if (!explanation || explanation.length > 500) { error.value = '请填写 1～500 字的取消原因。'; return }
  if (!window.confirm(`取消作品「${item.video.title}」的当前处理任务？`)) return
  busy.value = true
  error.value = ''
  notice.value = ''
  let succeeded = false
  try {
    const result = await adminApi.cancelProcessing(item.video, explanation)
    if (stopped) return
    notice.value = `已取消处理，作者可在失败状态下手动重试。当前状态：${adminStatus(result).label}。`
    selectedId.value = ''
    reason.value = ''
    succeeded = true
  } catch (failure) { if (!stopped) error.value = explain(failure) }
  finally { if (!stopped) busy.value = false }
  if (succeeded && !stopped) await load()
}

async function loadHls() {
  try { hlsStatus.value = await adminApi.hlsBackfillStatus(new AbortController().signal) }
  catch (failure) { hlsError.value = explain(failure) }
}
async function enqueueHls() {
  if (hlsBusy.value) return
  if (!window.confirm('扫描最多 20 部 READY 且没有 HLS 的历史作品，并交给 Worker 低优先级回填。继续？')) return
  hlsBusy.value = true
  hlsError.value = ''
  notice.value = ''
  try {
    const result = await adminApi.hlsBackfill(20)
    hlsStatus.value = result
    notice.value = `已排队 ${result.enqueued} 部作品；Worker 会自动逐个回填，失败任务会按次数重试。`
  } catch (failure) { hlsError.value = explain(failure) }
  finally { if (!stopped) hlsBusy.value = false }
}
onMounted(() => { void load(); void loadHls() })
onBeforeUnmount(() => { stopped = true; activeRequest?.abort() })
</script>

<template>
  <WorkspaceShell>
    <div class="studio-heading">
      <div><p class="eyebrow">FRAME ROOM / PIPELINE</p><h1>异常处理</h1><p class="muted">查看处理中或处理失败的作品；处理中可取消，失败任务已停止自动重试，由作者重试或删除。</p></div>
      <RouterLink to="/admin/videos">已完成作品 →</RouterLink>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" />
    <el-alert v-if="notice" :title="notice" type="success" :closable="false" role="status" />
    <section class="studio-panel">
      <div class="section-heading"><h2>历史 HLS 回填</h2><div class="inline-actions"><el-button :loading="hlsBusy" @click="loadHls">刷新状态</el-button><el-button type="primary" :loading="hlsBusy" @click="enqueueHls">扫描并排队</el-button></div></div>
      <p class="muted">只处理 READY、已有源文件且没有 HLS 的作品；不重新审核、不改变发布状态，成功前继续使用 MP4。</p>
      <el-alert v-if="hlsError" :title="hlsError" type="error" :closable="false" role="alert" />
      <p v-if="hlsStatus" class="muted">待处理 {{ hlsStatus.states.PENDING ?? 0 }} · 运行中 {{ hlsStatus.states.RUNNING ?? 0 }} · 等待重试 {{ hlsStatus.states.RETRY_WAIT ?? 0 }} · 完成 {{ hlsStatus.states.DONE ?? 0 }} · 失败 {{ hlsStatus.states.FAILED ?? 0 }}</p>
    </section>
    <div class="management-layout">
      <section class="studio-panel" :aria-busy="loading" aria-label="处理中作品">
        <div class="section-heading"><h2>处理任务 <span class="count">{{ total }}</span></h2><el-button :loading="loading" :disabled="busy" @click="load()">刷新列表</el-button></div>
        <p v-if="loading" role="status">正在读取处理任务…</p>
        <button v-for="item in items" v-else :key="item.video.id" class="managed-row" :class="{ active: selectedId === item.video.id }" :disabled="busy" :aria-pressed="selectedId === item.video.id" @click="select(item)">
          <strong>{{ item.video.title }}</strong><span>作品 #{{ item.video.id }} · 作者 #{{ item.ownerId }} · {{ adminStatus(item.video).label }} · 任务 {{ item.video.taskState || '—' }}</span>
        </button>
        <p v-if="!loading && !error && !items.length" class="empty-state">当前没有处理中或处理失败的作品。</p>
        <div class="page-controls"><el-button :disabled="loading || busy || page <= 1" @click="load(page - 1)">上一页</el-button><span>{{ page }}</span><el-button :disabled="loading || busy || page * 20 >= total || page >= 1000" @click="load(page + 1)">下一页</el-button></div>
      </section>
      <section class="studio-panel" aria-label="取消处理">
        <div class="section-heading"><h2>任务操作 <span class="count">仅处理中可取消</span></h2></div>
        <template v-if="selected">
          <h3>{{ selected.video.title }}</h3>
          <p class="muted">作品 #{{ selected.video.id }} · {{ adminStatus(selected.video).label }} · 版本 {{ selected.video.version }}</p>
          <dl class="video-facts"><div><dt>任务状态</dt><dd>{{ selected.video.taskState || '—' }}</dd></div><div><dt>执行代次</dt><dd>{{ selected.video.processingGeneration }}</dd></div><div><dt>已尝试</dt><dd>{{ selected.video.executionAttempts }} 次</dd></div><div><dt>下次重试</dt><dd>{{ selected.video.taskState === 'FAILED' ? '自动重试已结束' : selected.video.nextAttemptAt ? formatTime(selected.video.nextAttemptAt) : '—' }}</dd></div><div><dt>失败码</dt><dd>{{ selected.video.failureCode || '—' }}</dd></div></dl>
          <p class="video-description">{{ selected.video.description || '作者未填写简介。' }}</p>
          <p class="muted">{{ adminStatus(selected.video).detail }}</p>
          <form v-if="selectedActive" class="studio-form" @submit.prevent="cancel">
            <label for="cancel-reason">取消原因（必填）</label>
            <textarea id="cancel-reason" v-model="reason" required maxlength="500" rows="4" :disabled="busy" />
            <p class="muted">取消会停止当前任务并保留源文件；作者之后可以重新发起处理。</p>
            <div class="inline-actions"><el-button type="danger" native-type="submit" :loading="busy" :disabled="loading">确认取消处理</el-button></div>
          </form>
          <p v-else class="muted">当前任务已失败或已结束。作者可在作品详情重试；管理员可查看诊断后决定是否删除作品。</p>
        </template>
        <p v-else class="empty-state">从左侧选择一部处理任务。</p>
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
