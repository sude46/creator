<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import WorkspaceShell from '../components/WorkspaceShell.vue'
import { creatorApi } from '../api/creator'
import type { CreatorAnalytics } from '../api/creator'
import { useStudioError } from '../composables/useStudioError'

const explain = useStudioError()
const data = ref<CreatorAnalytics>()
const days = ref(30)
const loading = ref(false)
const error = ref('')
let request: AbortController | undefined
let stopped = false

const maxDaily = computed(() => Math.max(1, ...(data.value?.daily.map(item => item.views) ?? [0])))
const dayTotal = computed(() => data.value?.daily.reduce((sum, item) => sum + item.views, 0) ?? 0)
const labelEvery = computed(() => Math.max(1, Math.ceil((data.value?.daily.length ?? 0) / 7)))

function barHeight(views: number) {
  return `${Math.max(views ? 3 : 1, Math.round((views / maxDaily.value) * 100))}%`
}
function shortDate(value: string) {
  return value.slice(5).replace('-', '/')
}

async function load(next = days.value) {
  request?.abort()
  const controller = new AbortController()
  request = controller
  loading.value = true
  error.value = ''
  try {
    const result = await creatorApi.analytics(next, controller.signal)
    if (stopped || controller.signal.aborted) return
    data.value = result
    days.value = next
  } catch (failure) { if (!stopped && !controller.signal.aborted) error.value = explain(failure) }
  finally { if (!stopped && !controller.signal.aborted) loading.value = false }
}

onMounted(() => void load())
onBeforeUnmount(() => { stopped = true; request?.abort() })
</script>

<template>
  <WorkspaceShell>
    <div class="studio-heading">
      <div><p class="eyebrow">FRAME ROOM / INSIGHTS</p><h1>数据</h1><p class="muted">累计播放、互动和近 {{ days }} 天播放趋势。按天数据从本版本上线后开始累计。</p></div>
      <div class="inline-actions range-tabs" role="group" aria-label="统计周期">
        <el-button v-for="value in [7, 30, 90]" :key="value" :type="days === value ? 'primary' : 'default'" :disabled="loading" @click="load(value)">{{ value }} 天</el-button>
      </div>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" />
    <p v-if="loading" class="empty-state" role="status">正在汇总数据…</p>
    <template v-if="data && !loading">
      <section class="analytics-grid" aria-label="累计数据">
        <article class="metric"><span>作品</span><strong>{{ data.videoCount }}</strong></article>
        <article class="metric"><span>累计播放</span><strong>{{ data.views }}</strong></article>
        <article class="metric"><span>点赞</span><strong>{{ data.likes }}</strong></article>
        <article class="metric"><span>收藏</span><strong>{{ data.favorites }}</strong></article>
        <article class="metric"><span>评论</span><strong>{{ data.comments }}</strong></article>
        <article class="metric"><span>关注者</span><strong>{{ data.followers }}</strong></article>
      </section>

      <section class="studio-panel">
        <div class="section-heading"><h2>近 {{ days }} 天播放 <span class="count">{{ dayTotal }}</span></h2><span class="muted">{{ shortDate(data.from) }} – {{ shortDate(data.to) }}</span></div>
        <div class="bar-chart" role="img" :aria-label="`近 ${days} 天每日播放趋势`">
          <div v-for="(item, index) in data.daily" :key="item.date" class="bar-column" :title="`${item.date}：${item.views} 次播放`">
            <span class="bar" :style="{ height: barHeight(item.views) }"></span>
            <small v-if="index % labelEvery === 0 || index === data.daily.length - 1">{{ shortDate(item.date) }}</small>
          </div>
        </div>
        <p v-if="dayTotal === 0" class="muted">这段时间还没有新的播放记录。公开播放后会在这里出现。</p>
      </section>

      <section class="studio-panel">
        <div class="section-heading"><h2>作品表现</h2><RouterLink class="create-link" to="/studio">我的作品 →</RouterLink></div>
        <p v-if="!data.topVideos.length" class="empty-state">还没有作品数据。</p>
        <div v-else class="analytics-table">
          <table>
            <thead><tr><th scope="col">作品</th><th scope="col">播放</th><th scope="col">点赞</th><th scope="col">收藏</th><th scope="col">评论</th></tr></thead>
            <tbody><tr v-for="video in data.topVideos" :key="video.id"><td><RouterLink :to="`/studio/${video.id}`">{{ video.title }}</RouterLink></td><td>{{ video.views }}</td><td>{{ video.likes }}</td><td>{{ video.favorites }}</td><td>{{ video.comments }}</td></tr></tbody>
          </table>
        </div>
      </section>
    </template>
  </WorkspaceShell>
</template>

<style scoped>
.range-tabs { gap: 6px; }
.analytics-grid { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 12px; margin: 0 0 4px; }
.metric { padding: 18px; border: 1px solid var(--color-border); border-radius: var(--radius-card); background: var(--color-surface); }
.metric span { display: block; font-size: 12px; color: var(--color-muted-text); margin-bottom: 8px; }
.metric strong { font-size: 26px; font-weight: 600; font-variant-numeric: tabular-nums; }
.bar-chart { display: flex; align-items: flex-end; gap: 4px; height: 190px; padding: 10px 0 22px; border-bottom: 1px solid var(--color-border); }
.bar-column { flex: 1; min-width: 2px; height: 100%; display: flex; flex-direction: column; justify-content: flex-end; align-items: center; position: relative; }
.bar { display: block; width: 100%; min-height: 1px; border-radius: 2px 2px 0 0; background: var(--color-primary); opacity: .8; }
.bar-column:hover .bar { opacity: 1; }
.bar-column small { position: absolute; bottom: -19px; font-size: 10px; color: var(--color-muted-text); white-space: nowrap; }
.analytics-table { overflow-x: auto; }
.analytics-table table { width: 100%; border-collapse: collapse; font-size: 14px; }
.analytics-table th, .analytics-table td { text-align: left; padding: 11px 8px; border-bottom: 1px solid var(--color-border-soft); font-variant-numeric: tabular-nums; }
.analytics-table th:not(:first-child), .analytics-table td:not(:first-child) { text-align: right; }
.analytics-table a { color: var(--color-text); font-weight: 600; }
.analytics-table a:hover { color: var(--color-primary); }
@media (max-width: 900px) { .analytics-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
@media (max-width: 520px) { .analytics-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
</style>
