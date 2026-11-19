<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import WorkspaceShell from '../components/WorkspaceShell.vue'
import { adminApi } from '../api/admin'
import type { AdminOverview } from '../api/admin'
import { formatTime } from '../api/creator'
import { useStudioError } from '../composables/useStudioError'

const explain = useStudioError()
const overview = ref<AdminOverview>()
const loading = ref(false)
const error = ref('')
let activeRequest: AbortController | undefined
let stopped = false

async function load() {
  activeRequest?.abort()
  const controller = new AbortController()
  activeRequest = controller
  loading.value = true
  error.value = ''
  try {
    const result = await adminApi.overview(controller.signal)
    if (!stopped && !controller.signal.aborted) overview.value = result
  } catch (failure) { if (!stopped && !controller.signal.aborted) error.value = explain(failure) }
  finally { if (!stopped && !controller.signal.aborted) loading.value = false }
}

onMounted(() => { void load() })
onBeforeUnmount(() => { stopped = true; activeRequest?.abort() })
</script>

<template>
  <WorkspaceShell>
    <div class="studio-heading">
      <div><p class="eyebrow">FRAME ROOM / ADMIN</p><h1>概览</h1><p class="muted">处理待审、异常和账号，不让队列积压。</p></div>
      <el-button :loading="loading" @click="load">刷新数据</el-button>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" />
    <div v-if="loading && !overview" class="admin-metrics" aria-busy="true">
      <div v-for="index in 4" :key="index" class="metric-card metric-skeleton" aria-hidden="true"></div>
    </div>
    <div v-else-if="overview" class="admin-metrics">
      <RouterLink class="metric-card" to="/admin/reviews">
        <span>待审核</span><strong>{{ overview.pendingReviews }}</strong><small>进入审核队列 →</small>
      </RouterLink>
      <RouterLink class="metric-card" to="/admin/processing">
        <span>处理失败</span><strong>{{ overview.failedProcessing }}</strong><small>查看异常任务 →</small>
      </RouterLink>
      <RouterLink class="metric-card" to="/admin/users">
        <span>今日新增用户</span><strong>{{ overview.todayUsers }}</strong><small>管理用户 →</small>
      </RouterLink>
      <RouterLink class="metric-card" to="/admin/audit">
        <span>今日审核</span><strong>{{ overview.todayDecisions }}</strong><small>查看审计记录 →</small>
      </RouterLink>
    </div>
    <section v-if="overview" class="studio-panel">
      <div class="section-heading"><h2>最近审核</h2><RouterLink to="/admin/audit">查看审计记录 →</RouterLink></div>
      <p v-if="!overview.recentDecisions.length" class="empty-state">还没有审核记录。</p>
      <div v-else class="admin-table-wrap">
        <table>
          <thead><tr><th scope="col">作品</th><th scope="col">结果</th><th scope="col">说明</th><th scope="col">审核人</th><th scope="col">时间</th></tr></thead>
          <tbody>
            <tr v-for="decision in overview.recentDecisions" :key="decision.id">
              <td>{{ decision.title }}</td>
              <td><el-tag :type="decision.outcome === 'APPROVED' ? 'success' : 'danger'">{{ decision.outcome === 'APPROVED' ? '通过' : '驳回' }}</el-tag></td>
              <td>{{ decision.reason || '—' }}</td>
              <td>#{{ decision.reviewerId }}</td>
              <td>{{ formatTime(decision.createdAt) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </WorkspaceShell>
</template>

<style scoped>
.admin-metrics { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 16px; margin: 22px 0; }
.metric-card { display: grid; gap: 8px; padding: 22px; border: 1px solid #d9ddd5; border-radius: 4px; background: #fff; color: #182522; }
.metric-card:hover { border-color: #245e52; }
.metric-card span { color: #748277; font-size: 13px; }
.metric-card strong { font-size: 34px; line-height: 1; letter-spacing: -.04em; }
.metric-card small { color: #c65b3d; font-size: 12px; }
.metric-skeleton { min-height: 124px; background: linear-gradient(90deg, #f1f0ea, #faf9f4, #f1f0ea); background-size: 200% 100%; animation: pulse 1.4s infinite; }
.admin-table-wrap { overflow-x: auto; }
.admin-table-wrap table { width: 100%; border-collapse: collapse; font-size: 13px; }
.admin-table-wrap th, .admin-table-wrap td { text-align: left; padding: 11px 8px; border-bottom: 1px solid #e5e8e2; }
@keyframes pulse { to { background-position: -200% 0; } }
@media (max-width: 900px) { .admin-metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); } }@media (max-width: 560px) { .admin-metrics { grid-template-columns: 1fr; } }
</style>
