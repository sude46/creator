<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import WorkspaceShell from '../components/WorkspaceShell.vue'
import PublicVideoCard from '../components/PublicVideoCard.vue'
import { publicApi } from '../api/public'
import type { Favorite, FavoriteFolder, PublicVideo } from '../api/public'
import { formatTime } from '../api/creator'
import { useStudioError } from '../composables/useStudioError'

const items = ref<Favorite[]>([])
const folderItems = ref<PublicVideo[]>([])
const folders = ref<FavoriteFolder[]>([])
const selectedFolder = ref('')
const folderName = ref('')
const page = ref(1)
const total = ref(0)
const loading = ref(false)
const folderBusy = ref(false)
const removing = ref<string>()
const error = ref('')
const notice = ref('')
const folderPick = ref<Record<string, string>>({})
const explain = useStudioError()
let activeRequest: AbortController | undefined
let stopped = false

async function loadFolders() {
  try {
    const result = await publicApi.folders(AbortSignal.timeout(30000))
    if (!stopped) folders.value = result.items
  } catch (failure) { if (!stopped) error.value = explain(failure) }
}

async function load(next = page.value) {
  activeRequest?.abort()
  const controller = new AbortController()
  activeRequest = controller
  loading.value = true
  error.value = ''
  items.value = []
  folderItems.value = []
  const folderId = selectedFolder.value
  try {
    if (folderId) {
      const result = await publicApi.folderVideos(folderId, next, controller.signal)
      if (controller.signal.aborted) return
      folderItems.value = result.items
      total.value = result.total
      page.value = result.page
      return
    }
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
    await Promise.all([load(), loadFolders()])
  } catch (failure) {
    if (stopped) return
    const message = explain(failure)
    await load()
    if (!stopped) error.value = `${message}。已尝试刷新列表核对结果，请确认后再操作。`
  } finally {
    if (!stopped) removing.value = undefined
  }
}

async function createFolder() {
  const name = folderName.value.trim()
  if (!name || folderBusy.value) return
  folderBusy.value = true
  error.value = ''
  notice.value = ''
  try {
    const created = await publicApi.createFolder(name)
    if (stopped) return
    folderName.value = ''
    notice.value = `收藏夹“${created.name}”已创建。`
    await loadFolders()
    switchFolder(created.id)
  } catch (failure) { if (!stopped) error.value = explain(failure) }
  finally { if (!stopped) folderBusy.value = false }
}

async function renameFolder(folder: FavoriteFolder) {
  if (folderBusy.value) return
  const name = window.prompt('新的收藏夹名称', folder.name)?.trim() ?? ''
  if (!name || name === folder.name) return
  folderBusy.value = true
  error.value = ''
  notice.value = ''
  try {
    const updated = await publicApi.renameFolder(folder, name)
    if (stopped) return
    notice.value = `已重命名为“${updated.name}”。`
    await loadFolders()
  } catch (failure) { if (!stopped) error.value = explain(failure) }
  finally { if (!stopped) folderBusy.value = false }
}

async function deleteFolder(folder: FavoriteFolder) {
  if (folderBusy.value || !window.confirm(`删除收藏夹“${folder.name}”？收藏记录本身不会被取消。`)) return
  folderBusy.value = true
  error.value = ''
  notice.value = ''
  try {
    await publicApi.deleteFolder(folder)
    if (stopped) return
    notice.value = '收藏夹已删除。'
    if (selectedFolder.value === folder.id) switchFolder('')
    await loadFolders()
  } catch (failure) { if (!stopped) error.value = explain(failure) }
  finally { if (!stopped) folderBusy.value = false }
}

function switchFolder(id: string) {
  selectedFolder.value = id
  page.value = 1
  void load(1)
}

async function addToFolder(item: Favorite) {
  const folderId = folderPick.value[item.videoId]
  if (!folderId || folderBusy.value) return
  folderBusy.value = true
  error.value = ''
  notice.value = ''
  try {
    await publicApi.addToFolder(folderId, item.videoId)
    if (stopped) return
    notice.value = '已加入收藏夹。'
    await loadFolders()
  } catch (failure) { if (!stopped) error.value = explain(failure) }
  finally { if (!stopped) folderBusy.value = false }
}

async function removeFromFolder(video: PublicVideo) {
  const folderId = selectedFolder.value
  if (!folderId || folderBusy.value) return
  folderBusy.value = true
  error.value = ''
  notice.value = ''
  try {
    await publicApi.removeFromFolder(folderId, video.id)
    if (stopped) return
    notice.value = '已移出收藏夹，收藏记录仍保留。'
    await Promise.all([load(), loadFolders()])
  } catch (failure) { if (!stopped) error.value = explain(failure) }
  finally { if (!stopped) folderBusy.value = false }
}

function switchPage(next: number) { void load(next) }
onMounted(() => { void loadFolders(); void load() })
onBeforeUnmount(() => { stopped = true; activeRequest?.abort() })
</script>

<template>
  <WorkspaceShell>
    <div class="studio-heading">
      <div><p class="eyebrow">FRAME ROOM / SAVED</p><h1>我的收藏</h1><p class="muted">留住想再看一遍的作品，并按收藏夹整理。</p></div>
      <RouterLink to="/">继续发现作品 →</RouterLink>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" />
    <p v-if="notice" class="muted" role="status">{{ notice }}</p>

    <section class="studio-panel folder-panel" aria-label="收藏夹">
      <form class="folder-create" @submit.prevent="createFolder">
        <label class="sr-only" for="folder-name">新收藏夹名称</label>
        <input id="folder-name" v-model="folderName" maxlength="30" placeholder="新收藏夹名称，最多 30 字" />
        <el-button type="primary" native-type="submit" :loading="folderBusy" :disabled="!folderName.trim()">新建收藏夹</el-button>
      </form>
      <div class="folder-chips">
        <button type="button" :class="{ active: !selectedFolder }" @click="switchFolder('')">全部收藏</button>
        <button v-for="folder in folders" :key="folder.id" type="button" :class="{ active: selectedFolder === folder.id }" @click="switchFolder(folder.id)">{{ folder.name }} <small>{{ folder.itemCount }}</small></button>
      </div>
      <div v-if="selectedFolder" class="inline-actions folder-actions">
        <template v-for="folder in folders" :key="folder.id">
          <template v-if="folder.id === selectedFolder">
            <el-button :disabled="folderBusy" @click="renameFolder(folder)">重命名</el-button>
            <el-button type="danger" :disabled="folderBusy" @click="deleteFolder(folder)">删除收藏夹</el-button>
          </template>
        </template>
      </div>
    </section>

    <div class="public-results-heading">
      <h2>{{ selectedFolder ? folders.find(item => item.id === selectedFolder)?.name ?? '收藏夹' : '收藏的作品' }}</h2>
      <span v-if="!loading && !error">共 {{ total }} 项</span>
      <el-button :loading="loading" :disabled="folderBusy" @click="load()">刷新列表</el-button>
    </div>

    <section aria-label="收藏列表" :aria-busy="loading">
      <p v-if="loading" class="empty-state" role="status">正在读取收藏…</p>
      <div v-else-if="selectedFolder && folderItems.length" class="public-video-grid">
        <div v-for="video in folderItems" :key="video.id" class="favorite-item">
          <PublicVideoCard :video="video" />
          <div class="favorite-actions">
            <el-button :loading="folderBusy" :disabled="folderBusy" @click="removeFromFolder(video)">移出收藏夹</el-button>
          </div>
        </div>
      </div>
      <div v-else-if="!selectedFolder && items.length" class="public-video-grid">
        <div v-for="item in items" :key="item.videoId" class="favorite-item">
          <PublicVideoCard v-if="item.available && item.video" :video="item.video" />
          <article v-else class="favorite-unavailable">
            <div class="public-cover"><span class="cover-unavailable">作品暂不可用</span></div>
            <h2>无法查看这部作品</h2><p class="muted">作品当前不可公开查看，你仍可取消收藏。</p>
          </article>
          <div class="favorite-actions">
            <time class="muted">收藏于 {{ formatTime(item.createdAt) }}</time>
            <div v-if="folders.length" class="folder-add">
              <label class="sr-only" :for="`folder-${item.videoId}`">选择收藏夹</label>
              <select :id="`folder-${item.videoId}`" v-model="folderPick[item.videoId]">
                <option value="" disabled>加入收藏夹</option>
                <option v-for="folder in folders" :key="folder.id" :value="folder.id">{{ folder.name }}</option>
              </select>
              <el-button size="small" :disabled="folderBusy || !folderPick[item.videoId]" @click="addToFolder(item)">加入</el-button>
            </div>
            <el-button :loading="removing === item.videoId" :disabled="!!removing || loading" @click="remove(item)">取消收藏</el-button>
          </div>
        </div>
      </div>
      <p v-else-if="!error" class="empty-state">{{ selectedFolder ? '这个收藏夹还没有作品。回到“全部收藏”，把作品加入这个收藏夹。' : '还没有收藏。打开一部作品，点击“收藏作品”，之后就能在这里找到它。' }}</p>
    </section>
    <div class="page-controls">
      <el-button :disabled="loading || !!removing || page <= 1" @click="switchPage(page - 1)">上一页</el-button>
      <span>第 {{ page }} 页</span>
      <el-button :disabled="loading || !!removing || !!error || page * 12 >= total || page >= 1000" @click="switchPage(page + 1)">下一页</el-button>
    </div>
  </WorkspaceShell>
</template>

<style scoped>
.favorite-item { min-width: 0; display: flex; flex-direction: column; }
.favorite-unavailable h2 { font-size: 18px; margin: 14px 0 8px; }
.favorite-actions { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 10px; margin-top: auto; padding-top: 16px; }
.favorite-actions time { font-size: 12px; }
.folder-panel { margin-bottom: 24px; }
.folder-create { display: flex; gap: 10px; align-items: center; margin-bottom: 14px; }
.folder-create input { flex: 1; min-width: 0; border: 1px solid var(--color-control-border); border-radius: 999px; padding: 8px 15px; font: inherit; color: var(--color-text); background: var(--color-surface); }
.folder-chips { display: flex; flex-wrap: wrap; gap: 8px; }
.folder-chips button { border: 1px solid var(--color-control-border); background: var(--color-surface); color: var(--color-secondary); border-radius: 999px; padding: 6px 14px; font: inherit; font-size: 12px; cursor: pointer; }
.folder-chips button small { color: var(--color-muted-soft); margin-left: 4px; }
.folder-chips button.active { background: var(--color-primary); border-color: var(--color-primary); color: var(--color-surface); }
.folder-chips button.active small { color: inherit; opacity: .75; }
.folder-actions { margin-top: 14px; }
.folder-add { display: flex; gap: 6px; align-items: center; }
.folder-add select { border: 1px solid var(--color-control-border); border-radius: 3px; padding: 5px 8px; font: inherit; font-size: 12px; background: var(--color-surface); color: var(--color-text); }
</style>
