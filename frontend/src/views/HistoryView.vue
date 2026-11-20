<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import WorkspaceShell from '../components/WorkspaceShell.vue'
import PublicVideoCard from '../components/PublicVideoCard.vue'
import { publicApi } from '../api/public'
import type { HistoryEntry } from '../api/public'
import { formatTime } from '../api/creator'
import { useStudioError } from '../composables/useStudioError'

const items = ref<HistoryEntry[]>([])
const page = ref(1)
const total = ref(0)
const loading = ref(false)
const busy = ref(false)
const error = ref('')
const notice = ref('')
const explain = useStudioError()
let activeRequest: AbortController | undefined
let stopped = false

async function load(next = page.value) {
  activeRequest?.abort()
  const controller = new AbortController()
  activeRequest = controller
  loading.value = true
  error.value = ''
  try {
    let result = await publicApi.history(next, controller.signal)
    const lastPage = Math.max(1, Math.min(1000, Math.ceil(result.total / 12)))
    if (next > lastPage) {
      next = lastPage
      result = await publicApi.history(next, controller.signal)
    }
    if (stopped || controller.signal.aborted) return
    items.value = result.items
    page.value = result.page
    total.value = result.total
  } catch (failure) { if (!stopped && !controller.signal.aborted) error.value = explain(failure) }
  finally { if (!stopped && !controller.signal.aborted) loading.value = false }
}

async function remove(item: HistoryEntry) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    await publicApi.removeHistory(item.videoId)
    if (stopped) return
    notice.value = '已从观看历史移除。'
    await load()
  } catch (failure) { if (!stopped) error.value = explain(failure) }
  finally { if (!stopped) busy.value = false }
}

async function clearAll() {
  if (busy.value || !window.confirm('清空全部观看历史？')) return
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    await publicApi.clearHistory()
    if (stopped) return
    notice.value = '观看历史已清空。'
    await load(1)
  } catch (failure) { if (!stopped) error.value = explain(failure) }
  finally { if (!stopped) busy.value = false }
}

onMounted(() => void load())
onBeforeUnmount(() => { stopped = true; activeRequest?.abort() })
</script>

<template>
  <WorkspaceShell>
    <div class="studio-heading">
      <div><p class="eyebrow">FRAME ROOM / HISTORY</p><h1>观看历史</h1><p class="muted">按最近播放排序，同一个作品只保留最近一次。</p></div>
      <div class="inline-actions"><el-button :disabled="busy || !total" @click="clearAll">清空历史</el-button><RouterLink to="/">继续发现作品 →</RouterLink></div>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" />
    <p v-if="notice" class="muted" role="status">{{ notice }}</p>
    <div class="public-results-heading"><h2>最近观看</h2><span v-if="!loading && !error">共 {{ total }} 项</span><el-button :loading="loading" :disabled="busy" @click="load()">刷新列表</el-button></div>
    <section aria-label="观看历史" :aria-busy="loading">
      <p v-if="loading" class="empty-state" role="status">正在读取观看历史…</p>
      <div v-else-if="items.length" class="public-video-grid">
        <div v-for="entry in items" :key="entry.videoId" class="history-card">
          <PublicVideoCard v-if="entry.available && entry.video" :video="entry.video" />
          <article v-else class="favorite-unavailable"><div class="public-cover"><span class="cover-unavailable">作品暂不可用</span></div><h2>作品当前不可查看</h2></article>
          <div class="history-actions"><time class="muted">观看于 {{ formatTime(entry.viewedAt) }}</time><el-button size="small" :disabled="busy" @click="remove(entry)">移除</el-button></div>
        </div>
      </div>
      <p v-else-if="!error" class="empty-state">还没有观看记录。播放作品后会出现在这里。</p>
    </section>
    <div class="page-controls">
      <el-button :disabled="loading || busy || page <= 1" @click="load(page - 1)">上一页</el-button>
      <span>第 {{ page }} 页</span>
      <el-button :disabled="loading || busy || !!error || page * 12 >= total || page >= 1000" @click="load(page + 1)">下一页</el-button>
    </div>
  </WorkspaceShell>
</template>

<style scoped>
.history-card { min-width: 0; display: flex; flex-direction: column; }
.history-actions { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-top: auto; padding-top: 12px; }
.history-actions time { font-size: 12px; }
.favorite-unavailable h2 { font-size: 18px; margin: 14px 0 8px; }
</style>
