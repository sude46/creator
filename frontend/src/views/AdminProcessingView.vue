<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import WorkspaceShell from '../components/WorkspaceShell.vue'
import { adminApi } from '../api/admin'
import type { ManagedVideo } from '../api/admin'
import { formatTime, processingLabels } from '../api/creator'
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
    notice.value = `已取消处理，作者可在失败状态下手动重试。当前状态：${processingLabels[result.processingStatus]}。`
    selectedId.value = ''
    reason.value = ''
    succeeded = true
  } catch (failure) { if (!stopped) error.value = explain(failure) }
  finally { if (!stopped) busy.value = false }
  if (succeeded && !stopped) await load()
}

onMounted(() => { void load() })
onBeforeUnmount(() => { stopped = true; activeRequest?.abort() })
</script>

<template>
  <WorkspaceShell>
    <div class="studio-heading">
      <div><p class="eyebrow">FRAME ROOM / PIPELINE</p><h1>处理中作品</h1><p class="muted">查看等待或正在转码的作品；异常时可由管理员取消当前任务并记录原因。</p></div>
      <RouterLink to="/admin/videos">已完成作品 →</RouterLink>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" />
    <el-alert v-if="notice" :title="notice" type="success" :closable="false" role="status" />
    <div class="management-layout">
      <section class="studio-panel" :aria-busy="loading" aria-label="处理中作品">
        <div class="section-heading"><h2>处理中 <span class="count">{{ total }}</span></h2><el-button :loading="loading" :disabled="busy" @click="load()">刷新列表</el-button></div>
        <p v-if="loading" role="status">正在读取处理任务…</p>
        <button v-for="item in items" v-else :key="item.video.id" class="managed-row" :class="{ active: selectedId === item.video.id }" :disabled="busy" :aria-pressed="selectedId === item.video.id" @click="select(item)">
          <strong>{{ item.video.title }}</strong><span>作品 #{{ item.video.id }} · 作者 #{{ item.ownerId }} · {{ processingLabels[item.video.processingStatus] }} · 任务 {{ item.video.taskState || '—' }}</span>
        </button>
        <p v-if="!loading && !error && !items.length" class="empty-state">当前没有等待或处理中的作品。</p>
        <div class="page-controls"><el-button :disabled="loading || busy || page <= 1" @click="load(page - 1)">上一页</el-button><span>{{ page }}</span><el-button :disabled="loading || busy || page * 20 >= total || page >= 1000" @click="load(page + 1)">下一页</el-button></div>
      </section>
      <section class="studio-panel" aria-label="取消处理">
        <div class="section-heading"><h2>取消处理</h2></div>
        <template v-if="selected">
          <h3>{{ selected.video.title }}</h3>
          <p class="muted">作品 #{{ selected.video.id }} · 当前状态 {{ processingLabels[selected.video.processingStatus] }} · 版本 {{ selected.video.version }}</p>
          <dl class="video-facts"><div><dt>任务状态</dt><dd>{{ selected.video.taskState || '—' }}</dd></div><div><dt>执行代次</dt><dd>{{ selected.video.processingGeneration }}</dd></div><div><dt>已尝试</dt><dd>{{ selected.video.executionAttempts }} 次</dd></div><div><dt>下次重试</dt><dd>{{ selected.video.nextAttemptAt ? formatTime(selected.video.nextAttemptAt) : '—' }}</dd></div><div><dt>失败码</dt><dd>{{ selected.video.failureCode || '—' }}</dd></div></dl>
          <p class="video-description">{{ selected.video.description || '作者未填写简介。' }}</p>
          <form class="studio-form" @submit.prevent="cancel">
            <label for="cancel-reason">取消原因（必填）</label>
            <textarea id="cancel-reason" v-model="reason" required maxlength="500" rows="4" :disabled="busy" />
            <p class="muted">取消会停止当前任务并保留源文件；作者之后可以重新发起处理。</p>
            <div class="inline-actions"><el-button type="danger" native-type="submit" :loading="busy" :disabled="loading">确认取消处理</el-button></div>
          </form>
        </template>
        <p v-else class="empty-state">从左侧选择一部处理中的作品。</p>
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
