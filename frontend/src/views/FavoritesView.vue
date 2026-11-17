<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import WorkspaceShell from '../components/WorkspaceShell.vue'
import PublicVideoCard from '../components/PublicVideoCard.vue'
import { publicApi } from '../api/public'
import type { Favorite } from '../api/public'
import { formatTime } from '../api/creator'
import { useStudioError } from '../composables/useStudioError'

const items = ref<Favorite[]>([])
const page = ref(1)
const total = ref(0)
const loading = ref(false)
const removing = ref<string>()
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
  items.value = []
  try {
    let result = await publicApi.favorites(next, controller.signal)
    const lastPage = Math.max(1, Math.min(1000, Math.ceil(result.total / 12)))
    if (next > lastPage) {
      next = lastPage
      result = await publicApi.favorites(next, controller.signal)
    }
    if (stopped || controller.signal.aborted) return
    items.value = result.items
    total.value = result.total
    page.value = next
  } catch (failure) {
    if (!stopped && !controller.signal.aborted) error.value = explain(failure)
  } finally {
    if (!stopped && !controller.signal.aborted) loading.value = false
  }
}

async function remove(item: Favorite) {
  if (removing.value || loading.value) return
  removing.value = item.videoId
  notice.value = ''
  error.value = ''
  try {
    await publicApi.favorite(item.videoId, false)
    if (stopped) return
    notice.value = '已取消收藏。'
    await load()
  } catch (failure) {
    if (stopped) return
    const message = explain(failure)
    await load()
    if (!stopped) error.value = `${message}。已尝试刷新列表核对结果，请确认后再操作。`
  } finally {
    if (!stopped) removing.value = undefined
  }
}

onMounted(() => void load())
onBeforeUnmount(() => { stopped = true; activeRequest?.abort() })
</script>

<template>
  <WorkspaceShell>
    <div class="studio-heading">
      <div><p class="eyebrow">FRAME ROOM / SAVED</p><h1>我的收藏</h1><p class="muted">留住想再看一遍的作品。</p></div>
      <RouterLink to="/">继续发现作品 →</RouterLink>
    </div>
    <div class="public-results-heading">
      <h2>收藏的作品</h2><span v-if="!loading && !error">共 {{ total }} 项</span>
      <el-button :loading="loading" :disabled="!!removing" @click="load()">刷新列表</el-button>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" />
    <p v-if="notice" class="muted" role="status">{{ notice }}</p>
    <section aria-label="收藏列表" :aria-busy="loading">
      <p v-if="loading" class="empty-state" role="status">正在读取收藏…</p>
      <div v-else-if="items.length" class="public-video-grid">
        <div v-for="item in items" :key="item.videoId" class="favorite-item">
          <PublicVideoCard v-if="item.available && item.video" :video="item.video" />
          <article v-else class="favorite-unavailable">
            <div class="public-cover"><span class="cover-unavailable">作品暂不可用</span></div>
            <h2>无法查看这部作品</h2><p class="muted">作品当前不可公开查看，你仍可取消收藏。</p>
          </article>
          <div class="favorite-actions">
            <time class="muted">收藏于 {{ formatTime(item.createdAt) }}</time>
            <el-button :loading="removing === item.videoId" :disabled="!!removing || loading" @click="remove(item)">取消收藏</el-button>
          </div>
        </div>
      </div>
      <p v-else-if="!error" class="empty-state">还没有收藏。打开一部作品，点击“收藏作品”，之后就能在这里找到它。</p>
    </section>
    <div class="page-controls">
      <el-button :disabled="loading || !!removing || page <= 1" @click="load(page - 1)">上一页</el-button>
      <span>第 {{ page }} 页</span>
      <el-button :disabled="loading || !!removing || !!error || page * 12 >= total || page >= 1000" @click="load(page + 1)">下一页</el-button>
    </div>
  </WorkspaceShell>
</template>

<style scoped>
.favorite-item { min-width: 0; display: flex; flex-direction: column; }
.favorite-unavailable h2 { font-size: 18px; margin: 14px 0 8px; }
.favorite-actions { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 10px; margin-top: auto; padding-top: 16px; }
.favorite-actions time { font-size: 12px; }
</style>
