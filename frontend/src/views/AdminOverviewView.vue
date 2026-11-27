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
const decisionsTarget = computed(() => superAccess.value ? '/admin/audit' : '/admin/reviews')
const lede = computed(() => reviewAccess.value
  ? '先处理待审和异常，再看今天的审核记录。'
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
    <p v-if="loading && !overview" class="overview-loading" role="status">正在读取管理概览…</p>
    <template v-if="overview">
      <div v-if="reviewAccess" class="overview-priority">
        <RouterLink class="priority-action" to="/admin/reviews">
          <span>待审核</span><strong>{{ overview.pendingReviews }}</strong><small>进入审核队列 →</small>
        </RouterLink>
        <RouterLink class="priority-action" to="/admin/processing">
          <span>处理失败</span><strong>{{ overview.failedProcessing }}</strong><small>查看异常任务 →</small>
        </RouterLink>
      </div>
      <div v-if="reviewAccess" class="overview-stats">
        <RouterLink :to="decisionsTarget">
          今日审核 <strong>{{ overview.todayDecisions }}</strong><span>→</span>
        </RouterLink>
        <RouterLink v-if="superAccess" to="/admin/users">
          今日新增用户 <strong>{{ overview.todayUsers }}</strong><span>→</span>
        </RouterLink>
      </div>
      <section v-if="governanceOnly" class="studio-panel">
        <div class="section-heading"><h2>内容治理</h2></div>
        <p class="muted">当前账号负责作品封禁、举报和申诉处理。</p>
        <div class="inline-actions"><RouterLink to="/admin/videos">作品管理 →</RouterLink><RouterLink to="/admin/reports">举报处理 →</RouterLink><RouterLink to="/admin/appeals">申诉处理 →</RouterLink></div>
      </section>
      <section v-if="reviewAccess" class="studio-panel">
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
    </template>
  </WorkspaceShell>
</template>

<style scoped>
.overview-loading { color: var(--color-muted-2); line-height: 1.8; margin: 22px 0; }
.overview-priority { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; margin: 22px 0 0; }
.priority-action { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 7px 18px; align-items: center; padding: 21px 22px; border: 1px solid var(--color-border); border-radius: 4px; background: var(--color-surface); color: var(--color-text); text-decoration: none; transition: border-color .2s ease; }
.priority-action span { font-size: 13px; color: var(--color-muted); }
.priority-action strong { grid-column: 2; grid-row: 1 / span 2; font-size: 38px; line-height: 1; letter-spacing: -.04em; }
.priority-action small { align-self: end; color: var(--color-accent-text); font-size: 12px; }
.priority-action:hover { border-color: var(--color-primary); }
.priority-action:focus-visible { outline: 2px solid var(--color-focus); outline-offset: 2px; }
.overview-stats { display: flex; flex-wrap: wrap; margin: 14px 0 0; border-top: 1px solid var(--color-border); border-bottom: 1px solid var(--color-border); }
.overview-stats a { display: inline-flex; align-items: baseline; gap: 8px; padding: 11px 20px 11px 0; margin-right: 20px; border-right: 1px solid var(--color-border-soft); color: var(--color-muted); font-size: 13px; text-decoration: none; }
.overview-stats a:last-child { border-right: 0; margin-right: 0; }
.overview-stats strong { color: var(--color-text); font-size: 18px; font-weight: 600; }
.overview-stats a:hover, .overview-stats a:hover strong { color: var(--color-accent-text); }
.overview-stats a:focus-visible { outline: 2px solid var(--color-focus); outline-offset: 2px; }
.admin-table-wrap { overflow-x: auto; }
.admin-table-wrap table { width: 100%; min-width: 640px; border-collapse: collapse; font-size: 13px; }
.admin-table-wrap th, .admin-table-wrap td { text-align: left; padding: 11px 8px; border-bottom: 1px solid #e5e8e2; }
@media (max-width: 760px) {
  .overview-priority { grid-template-columns: 1fr; }
  .priority-action { padding: 18px; }
  .overview-stats a { padding: 10px 14px 10px 0; margin-right: 14px; }
}
</style>
