<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import WorkspaceShell from '../components/WorkspaceShell.vue'
import { appealApi } from '../api/appeals'
import type { Appeal, AppealStatus } from '../api/appeals'
import { formatTime } from '../api/creator'
import { useStudioError } from '../composables/useStudioError'

const explain = useStudioError()
const status = ref<AppealStatus | ''>('OPEN')
const items = ref<Appeal[]>([])
const page = ref(1)
const total = ref(0)
const loading = ref(false)
const busy = ref(false)
const error = ref('')
const notice = ref('')
let request: AbortController | undefined
let stopped = false

async function load(next = page.value) {
  request?.abort()
  const controller = new AbortController()
  request = controller
  loading.value = true
  error.value = ''
  try {
    const result = await appealApi.admin(status.value, next, controller.signal)
    if (stopped || controller.signal.aborted) return
    items.value = result.items
    page.value = result.page
    total.value = result.total
  } catch (failure) { if (!stopped && !controller.signal.aborted) error.value = explain(failure) }
  finally { if (!stopped && !controller.signal.aborted) loading.value = false }
}

async function resolve(appeal: Appeal, action: 'ACCEPT' | 'REJECT') {
  if (busy.value) return
  const label = action === 'ACCEPT' ? '受理' : '驳回'
  const reason = window.prompt(`${label}申诉原因（1～500 字）`)?.trim() ?? ''
  if (!reason) return
  if (!window.confirm(`${label}这条申诉？${action === 'ACCEPT' ? '作品会解除封禁。' : '作品会保持封禁。'}`)) return
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    await appealApi.resolve(appeal.id, action, reason)
    if (stopped) return
    notice.value = action === 'ACCEPT' ? '申诉已受理，作品已解禁。' : '申诉已驳回。'
    await load()
  } catch (failure) { if (!stopped) error.value = explain(failure) }
  finally { if (!stopped) busy.value = false }
}

function filter(next: AppealStatus | '') { status.value = next; void load(1) }
onMounted(() => void load())
onBeforeUnmount(() => { stopped = true; request?.abort() })
</script>

<template>
  <WorkspaceShell>
    <div class="studio-heading"><div><p class="eyebrow">FRAME ROOM / APPEALS</p><h1>申诉处理</h1><p class="muted">复核被举报或封禁作品的作者申诉；受理会解除封禁。</p></div><el-button :loading="loading" :disabled="busy" @click="load()">刷新</el-button></div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" />
    <p v-if="notice" class="muted" role="status">{{ notice }}</p>
    <div class="filter-tabs" role="group" aria-label="申诉状态"><button type="button" :class="{ active: status === 'OPEN' }" @click="filter('OPEN')">待处理</button><button type="button" :class="{ active: status === 'ACCEPTED' }" @click="filter('ACCEPTED')">已受理</button><button type="button" :class="{ active: status === 'REJECTED' }" @click="filter('REJECTED')">已驳回</button><button type="button" :class="{ active: status === '' }" @click="filter('')">全部</button></div>
    <section class="studio-panel" :aria-busy="loading">
      <p v-if="loading" class="empty-state" role="status">正在读取申诉…</p>
      <article v-for="appeal in items" :key="appeal.id" class="history-item">
        <div class="section-heading"><div><strong>作品 #{{ appeal.targetId }} · {{ appeal.targetTitle || '标题不可用' }}</strong><p class="muted">申诉人 {{ appeal.appellantName }} · {{ formatTime(appeal.createdAt) }}</p></div><el-tag :type="appeal.status === 'OPEN' ? 'warning' : appeal.status === 'ACCEPTED' ? 'success' : 'danger'">{{ appeal.status === 'OPEN' ? '待处理' : appeal.status === 'ACCEPTED' ? '已受理' : '已驳回' }}</el-tag></div>
        <p class="history-reason">{{ appeal.reason }}</p>
        <p v-if="appeal.resolutionReason" class="muted">处理说明：{{ appeal.resolutionReason }}</p>
        <div v-if="appeal.status === 'OPEN'" class="inline-actions"><el-button type="primary" :loading="busy" @click="resolve(appeal, 'ACCEPT')">受理并解禁</el-button><el-button type="danger" :disabled="busy" @click="resolve(appeal, 'REJECT')">驳回申诉</el-button></div>
      </article>
      <p v-if="!loading && !items.length" class="empty-state">当前筛选下没有申诉。</p>
      <div class="page-controls"><el-button :disabled="loading || busy || page <= 1" @click="load(page - 1)">上一页</el-button><span>第 {{ page }} 页</span><el-button :disabled="loading || busy || page * 20 >= total || page >= 1000" @click="load(page + 1)">下一页</el-button></div>
    </section>
  </WorkspaceShell>
</template>

<style scoped>
.filter-tabs { display: flex; flex-wrap: wrap; gap: 8px; margin: 0 0 16px; }
.filter-tabs button { border: 1px solid var(--color-control-border); border-radius: 999px; background: var(--color-surface); color: var(--color-secondary); padding: 5px 14px; font: inherit; font-size: 12px; cursor: pointer; }
.filter-tabs button.active { background: var(--color-primary); border-color: var(--color-primary); color: var(--color-surface); }
</style>
