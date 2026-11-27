<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import WorkspaceShell from '../components/WorkspaceShell.vue'
import { adminApi } from '../api/admin'
import type { AdminOverview } from '../api/admin'
import { formatTime } from '../api/creator'
import { useStudioError } from '../composables/useStudioError'
import { auth } from '../stores/auth'
import { canModerate, canReview, isSuperAdmin } from '../permissions'

const route = useRoute()
const explain = useStudioError()
const reviewAccess = computed(() => canReview(auth.user.value))
const moderationAccess = computed(() => canModerate(auth.user.value))
const superAccess = computed(() => isSuperAdmin(auth.user.value))
const governanceOnly = computed(() => moderationAccess.value && !reviewAccess.value)
const metricCount = computed(() => reviewAccess.value ? (superAccess.value ? 4 : 3) : 0)
const decisionsTarget = computed(() => superAccess.value ? '/admin/audit' : '/admin/reviews')
const lede = computed(() => reviewAccess.value
  ? '处理待审、异常和审核记录，不让队列积压。'
  : '处理举报和申诉，维护公开内容。')
const deniedNotice = computed(() => {
  const raw = route.query.denied
  const key = Array.isArray(raw) ? raw[0] : raw
  if (!key) return ''
  const labels: Record<string, string> = {
    admin: '当前账号没有管理后台权限。',
    review: '当前账号没有审核或异常处理权限，已返回管理概览。',
    moderate: '当前账号没有内容治理权限，已返回管理概览。',
    super: '当前账号没有系统配置权限，已返回管理概览。',
  }
  return labels[key] ?? '当前账号没有该模块权限，已返回管理概览。'
})
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
      <div><p class="eyebrow">FRAME ROOM / ADMIN</p><h1>概览</h1><p class="muted">{{ lede }}</p></div>
      <el-button :loading="loading" @click="load">刷新数据</el-button>
    </div>
    <el-alert v-if="deniedNotice" :title="deniedNotice" type="info" :closable="false" role="status" />
    <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" />
    <p v-if="loading && !overview && metricCount === 0" class="muted" role="status">正在读取管理概览…</p>
    <div v-if="loading && !overview && metricCount > 0" class="admin-metrics" aria-busy="true">
      <div v-for="index in metricCount" :key="index" class="metric-card metric-skeleton" aria-hidden="true"></div>
    </div>
    <div v-else-if="overview && metricCount > 0" class="admin-metrics">
      <RouterLink v-if="reviewAccess" class="metric-card" to="/admin/reviews">
        <span>待审核</span><strong>{{ overview.pendingReviews }}</strong><small>进入审核队列 →</small>
      </RouterLink>
      <RouterLink v-if="reviewAccess" class="metric-card" to="/admin/processing">
        <span>处理失败</span><strong>{{ overview.failedProcessing }}</strong><small>查看异常任务 →</small>
      </RouterLink>
      <RouterLink v-if="superAccess" class="metric-card" to="/admin/users">
        <span>今日新增用户</span><strong>{{ overview.todayUsers }}</strong><small>管理用户 →</small>
      </RouterLink>
      <RouterLink v-if="reviewAccess" class="metric-card" :to="decisionsTarget">
        <span>今日审核</span><strong>{{ overview.todayDecisions }}</strong><small>{{ superAccess ? '查看审计记录 →' : '回到审核队列 →' }}</small>
      </RouterLink>
    </div>
    <section v-if="overview && governanceOnly" class="studio-panel">
      <div class="section-heading"><h2>内容治理</h2></div>
      <p class="muted">当前账号负责作品封禁、举报和申诉处理。</p>
      <div class="inline-actions"><RouterLink to="/admin/videos">作品管理 →</RouterLink><RouterLink to="/admin/reports">举报处理 →</RouterLink><RouterLink to="/admin/appeals">申诉处理 →</RouterLink></div>
    </section>
    <section v-if="overview && reviewAccess" class="studio-panel">
      <div class="section-heading"><h2>最近审核</h2><RouterLink :to="decisionsTarget">{{ superAccess ? '查看审计记录 →' : '返回审核队列 →' }}</RouterLink></div>
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
.admin-metrics { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 16px; margin: 22px 0; }
.metric-card { display: grid; gap: 8px; padding: 22px; border: 1px solid #d9ddd5; border-radius: 4px; background: #fff; color: #182522; }
.metric-card:hover { border-color: #245e52; }
.metric-card span { color: var(--color-muted); font-size: 13px; }
.metric-card strong { font-size: 34px; line-height: 1; letter-spacing: -.04em; }
.metric-card small { color: var(--color-accent-text); font-size: 12px; }
.metric-skeleton { min-height: 124px; background: linear-gradient(90deg, #f1f0ea, #faf9f4, #f1f0ea); background-size: 200% 100%; animation: pulse 1.4s infinite; }
.admin-table-wrap { overflow-x: auto; }
.admin-table-wrap table { width: 100%; min-width: 640px; border-collapse: collapse; font-size: 13px; }
.admin-table-wrap th, .admin-table-wrap td { text-align: left; padding: 11px 8px; border-bottom: 1px solid #e5e8e2; }
@keyframes pulse { to { background-position: -200% 0; } }
@media (max-width: 900px) { .admin-metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); } }@media (max-width: 560px) { .admin-metrics { grid-template-columns: 1fr; } }
</style>
