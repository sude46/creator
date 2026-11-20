<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import WorkspaceShell from '../components/WorkspaceShell.vue'
import { creatorApi } from '../api/creator'
import type { Collection, CollectionItem, CreatorVideo } from '../api/creator'
import { creatorStatus } from '../api/videoStatus'
import { useStudioError } from '../composables/useStudioError'

const route = useRoute()
const explain = useStudioError()
const id = String(route.params.id)
const collection = ref<Collection>()
const items = ref<CollectionItem[]>([])
const candidates = ref<CreatorVideo[]>([])
const pick = ref('')
const loading = ref(false)
const busy = ref(false)
const error = ref('')
const notice = ref('')
let request: AbortController | undefined
let stopped = false

const availableCandidates = computed(() => candidates.value.filter(video => !items.value.some(item => item.videoId === video.id)))

function statusLabel(item: CollectionItem) {
  if (item.deleted) return '已删除'
  if (item.processingStatus !== 'READY') return item.processingStatus === 'FAILED' ? '处理失败' : '处理中'
  if (item.reviewStatus === 'PENDING') return '审核中'
  if (item.reviewStatus === 'REJECTED') return '未通过审核'
  if (item.publicationStatus === 'PUBLISHED') return '已发布'
  if (item.publicationStatus === 'OFFLINE') return '已下架'
  return '未发布'
}

async function load() {
  request?.abort()
  const controller = new AbortController()
  request = controller
  loading.value = true
  error.value = ''
  try {
    const [owned, list] = await Promise.all([creatorApi.collection(id, controller.signal), creatorApi.collectionItems(id, controller.signal)])
    if (stopped || controller.signal.aborted) return
    collection.value = owned
    items.value = list
    void loadCandidates(controller.signal)
  } catch (failure) { if (!stopped && !controller.signal.aborted) error.value = explain(failure) }
  finally { if (!stopped && !controller.signal.aborted) loading.value = false }
}

async function loadCandidates(signal: AbortSignal) {
  try {
    const all: CreatorVideo[] = []
    for (let page = 1; page <= 10; page++) {
      const result = await creatorApi.list(page, signal)
      if (signal.aborted) return
      all.push(...result.items)
      if (page * 10 >= result.total) break
    }
    if (!stopped && !signal.aborted) candidates.value = all
  } catch { /* adding is optional when the work list fails */ }
}

async function move(item: CollectionItem, direction: 'UP' | 'DOWN') {
  if (busy.value) return
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    await creatorApi.moveCollectionItem(id, item.videoId, direction)
    if (!stopped) { notice.value = '顺序已更新。'; await load() }
  } catch (failure) { if (!stopped) error.value = explain(failure) }
  finally { if (!stopped) busy.value = false }
}

async function remove(item: CollectionItem) {
  if (busy.value || !window.confirm(`把「${item.title}」移出合集？作品本身不会被删除。`)) return
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    await creatorApi.removeFromCollection(id, item.videoId)
    if (!stopped) { notice.value = '已移出合集。'; await load() }
  } catch (failure) { if (!stopped) error.value = explain(failure) }
  finally { if (!stopped) busy.value = false }
}

async function add() {
  if (busy.value || !pick.value) return
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    await creatorApi.addToCollection(id, pick.value)
    if (!stopped) { pick.value = ''; notice.value = '已加入合集。'; await load() }
  } catch (failure) { if (!stopped) error.value = explain(failure) }
  finally { if (!stopped) busy.value = false }
}

onMounted(() => { if (/^[1-9]\d{0,18}$/.test(id)) void load(); else error.value = '合集编号无效' })
onBeforeUnmount(() => { stopped = true; request?.abort() })
</script>

<template>
  <WorkspaceShell>
    <RouterLink class="back-link" to="/studio/collections">← 返回我的合集</RouterLink>
    <div class="studio-heading">
      <div><p class="eyebrow">FRAME ROOM / COLLECTION</p><h1>{{ collection?.title || '合集管理' }}</h1><p class="muted">{{ collection?.visibility === 'PRIVATE' ? '仅自己可见' : '公开' }} · {{ items.length }} 部作品 · 顺序会显示在公开合集页</p></div>
      <el-button :loading="loading || busy" @click="load">刷新</el-button>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" />
    <p v-if="notice" class="muted" role="status">{{ notice }}</p>

    <section class="studio-panel">
      <div class="section-heading"><h2>添加作品</h2></div>
      <div class="inline-actions add-row">
        <label class="sr-only" for="collection-pick">选择作品</label>
        <select id="collection-pick" v-model="pick" :disabled="busy || !availableCandidates.length">
          <option value="" disabled>{{ availableCandidates.length ? '选择要加入的作品' : '没有可加入的作品' }}</option>
          <option v-for="video in availableCandidates" :key="video.id" :value="video.id">{{ video.title }} · {{ creatorStatus(video).label }}</option>
        </select>
        <el-button type="primary" :loading="busy" :disabled="!pick" @click="add">加入合集</el-button>
      </div>
    </section>

    <section class="studio-panel" :aria-busy="loading">
      <div class="section-heading"><h2>合集作品 <span class="count">{{ items.length }}</span></h2></div>
      <p v-if="loading" class="muted" role="status">正在读取合集…</p>
      <div v-else-if="items.length" class="collection-item-list">
        <article v-for="(item, index) in items" :key="item.videoId" class="collection-item-row">
          <span class="item-order">{{ String(index + 1).padStart(2, '0') }}</span>
          <div class="item-copy"><RouterLink :to="`/studio/${item.videoId}`">{{ item.title }}</RouterLink><p class="muted">{{ statusLabel(item) }}</p></div>
          <div class="inline-actions item-actions">
            <el-button size="small" :disabled="busy || index === 0" @click="move(item, 'UP')">上移</el-button>
            <el-button size="small" :disabled="busy || index === items.length - 1" @click="move(item, 'DOWN')">下移</el-button>
            <el-button type="danger" size="small" :disabled="busy" @click="remove(item)">移出</el-button>
          </div>
        </article>
      </div>
      <p v-else-if="!error" class="empty-state">合集还没有作品。先从上方选择一部作品加入。</p>
    </section>
  </WorkspaceShell>
</template>

<style scoped>
.add-row select { min-width: min(420px, 100%); border: 1px solid var(--color-control-border); border-radius: 3px; padding: 9px 11px; font: inherit; background: var(--color-surface); color: var(--color-text); }
.collection-item-list { display: grid; gap: 8px; }
.collection-item-row { display: flex; align-items: center; gap: 14px; padding: 14px 16px; border: 1px solid var(--color-border); border-radius: var(--radius-card); background: var(--color-surface); }
.item-order { flex: none; font-size: 18px; font-weight: 600; color: var(--color-muted); font-variant-numeric: tabular-nums; }
.item-copy { flex: 1; min-width: 0; }
.item-copy a { color: var(--color-text); font-weight: 600; overflow-wrap: anywhere; }
.item-copy a:hover { color: var(--color-primary); }
.item-copy .muted { margin: 3px 0 0; font-size: 12px; }
.item-actions { margin: 0; flex: none; }
@media (max-width: 600px) { .collection-item-row { flex-wrap: wrap; } .item-actions { width: 100%; padding-left: 32px; } }
</style>
