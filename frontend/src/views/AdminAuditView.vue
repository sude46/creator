<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import WorkspaceShell from '../components/WorkspaceShell.vue'
import { adminApi } from '../api/admin'
import type { AuditRecord } from '../api/admin'
import { formatTime } from '../api/creator'
import { useStudioError } from '../composables/useStudioError'

const explain = useStudioError()
const items = ref<AuditRecord[]>([])
const page = ref(1)
const total = ref(0)
const loading = ref(false)
const error = ref('')
const filters = ref({ actorId: '', action: '', targetType: '', from: '', to: '' })
let activeRequest: AbortController | undefined
let stopped = false

async function load(next = page.value) {
  activeRequest?.abort()
  const controller = new AbortController()
  activeRequest = controller
  loading.value = true
  error.value = ''
  items.value = []
  try {
    const result = await adminApi.audit(next, filters.value, controller.signal)
    if (stopped || controller.signal.aborted) return
    items.value = result.items
    total.value = result.total
    page.value = result.page
  } catch (failure) { if (!stopped && !controller.signal.aborted) error.value = explain(failure) }
  finally { if (!stopped && !controller.signal.aborted) loading.value = false }
}

onMounted(() => { void load() })
onBeforeUnmount(() => { stopped = true; activeRequest?.abort() })
</script>

<template>
  <WorkspaceShell>
    <div class="studio-heading">
      <div><p class="eyebrow">FRAME ROOM / AUDIT</p><h1>审计记录</h1><p class="muted">只读查询管理操作、审核和资源变更记录。</p></div>
      <el-button @click="adminApi.auditExport(filters)">导出 CSV</el-button>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" />
    <section class="studio-panel">
      <div class="audit-filters">
        <label>操作者 ID<input v-model=filters.actorId placeholder="可选" /></label>
        <label>动作<input v-model=filters.action placeholder="例如 VIDEO_DELETED" /></label>
        <label>目标类型<input v-model=filters.targetType placeholder="VIDEO / USER / CATEGORY" /></label>
        <label>开始时间<input v-model=filters.from type="datetime-local" /></label>
        <label>结束时间<input v-model=filters.to type="datetime-local" /></label>
        <el-button type="primary" :loading="loading" @click="load(1)">查询</el-button>
      </div>
      <p v-if="loading" role="status">正在读取审计记录…</p>
      <div v-else class="audit-table">
        <table>
          <thead><tr><th>时间</th><th>操作者</th><th>动作</th><th>目标</th><th>原因</th><th>请求 ID</th></tr></thead>
          <tbody><tr v-for="item in items" :key="item.id"><td>{{ formatTime(item.createdAt) }}</td><td>#{{ item.actorId }}</td><td>{{ item.action }}</td><td>{{ item.targetType }} #{{ item.targetId }}</td><td>{{ item.reason || '—' }}</td><td class="request-id">{{ item.requestId || '—' }}</td></tr></tbody>
        </table>
      </div>
      <p v-if="!loading && !items.length" class="empty-state">没有匹配记录。</p>
      <div class="page-controls"><el-button :disabled="loading || page <= 1" @click="load(page - 1)">上一页</el-button><span>第 {{ page }} 页 · 共 {{ total }} 条</span><el-button :disabled="loading || page * 20 >= total || page >= 1000" @click="load(page + 1)">下一页</el-button></div>
    </section>
  </WorkspaceShell>
</template>

<style scoped>
.audit-filters { display: flex; flex-wrap: wrap; gap: 12px; align-items: end; margin-bottom: 20px; }
.audit-filters label { display: grid; gap: 6px; font-size: 12px; color: var(--color-muted); }
.audit-filters input { min-width: 140px; padding: 8px; border: 1px solid #ccd8cd; border-radius: 3px; }
.audit-table { overflow-x: auto; }
.audit-table table { width: 100%; border-collapse: collapse; font-size: 13px; }
.audit-table th, .audit-table td { text-align: left; padding: 10px 8px; border-bottom: 1px solid #e5e8e2; white-space: nowrap; }
.request-id { max-width: 220px; overflow: hidden; text-overflow: ellipsis; }
</style>
