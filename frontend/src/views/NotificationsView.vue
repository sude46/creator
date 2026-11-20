<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import WorkspaceShell from '../components/WorkspaceShell.vue'
import { notificationApi } from '../api/notifications'
import { notificationState } from '../stores/notifications'
import type { Notification } from '../api/notifications'
import { formatTime } from '../api/creator'
import { useStudioError } from '../composables/useStudioError'
const router = useRouter()
const explain = useStudioError(); const items = ref<Notification[]>([]); const page = ref(1); const total = ref(0); const unread = ref(0); const loading = ref(false); const reading = ref<string>(); const error = ref('')
let request: AbortController | undefined; let stopped = false
async function load(next = page.value) { request?.abort(); const c = new AbortController(); request = c; loading.value = true; error.value = ''; try { const [list, count] = await Promise.all([notificationApi.list(next, c.signal), notificationApi.unread(c.signal)]); if (!stopped && !c.signal.aborted) { items.value = list.items; page.value = list.page; total.value = list.total; unread.value = count.unread; notificationState.set(count.unread) } } catch (f) { if (!stopped && !c.signal.aborted) error.value = explain(f) } finally { if (!stopped && !c.signal.aborted) loading.value = false } }
async function markRead(item: Notification, reportError = true) {
  if (item.readAt || reading.value) return true
  reading.value = item.id
  try {
    await notificationApi.read(item.id)
    if (!stopped) {
      item.readAt = new Date().toISOString()
      unread.value = Math.max(0, unread.value - 1)
      notificationState.decrement()
    }
    return true
  } catch (failure) {
    if (reportError && !stopped) error.value = explain(failure)
    return false
  } finally { if (!stopped) reading.value = undefined }
}
async function open(item: Notification) {
  await markRead(item, false)
  if (!stopped && item.link) await router.push(item.link)
}
async function readAll() { try { await notificationApi.readAll(); notificationState.clear(); await load() } catch (f) { if (!stopped) error.value = explain(f) } }
onMounted(() => { void load() }); onBeforeUnmount(() => { stopped = true; request?.abort() })
</script>
<template><WorkspaceShell><div class="studio-heading"><div><p class="eyebrow">FRAME ROOM / INBOX</p><h1>通知</h1><p class="muted">审核、评论和关注消息。未读 {{ unread }} 条。</p></div><el-button :disabled="!unread" @click="readAll">全部标为已读</el-button></div><el-alert v-if="error" :title="error" type="error" :closable="false" role="alert"/><section class="studio-panel" :aria-busy="loading"><p v-if="loading" class="muted">正在加载通知…</p><article v-for="item in items" :key="item.id" class="history-item"><div class="section-heading"><div><strong>{{ item.title }}</strong><p v-if="item.body" class="muted">{{ item.body }}</p></div><time>{{ formatTime(item.createdAt) }}</time></div><div class="notification-actions"><button v-if="item.link" type="button" class="link-action" @click="open(item)">查看详情</button><el-button v-if="!item.readAt" size="small" :loading="reading === item.id" :disabled="!!reading && reading !== item.id" @click="markRead(item)">标为已读</el-button><el-tag v-else type="info">已读</el-tag></div></article><p v-if="!loading && !items.length" class="empty-state">还没有通知。</p><div class="page-controls"><el-button :disabled="loading || page <= 1" @click="load(page - 1)">上一页</el-button><span>第 {{ page }} 页</span><el-button :disabled="loading || page * 20 >= total || page >= 1000" @click="load(page + 1)">下一页</el-button></div></section></WorkspaceShell></template>

<style scoped>
.notification-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; margin-top: 8px; }
.link-action { border: 0; background: none; padding: 0; color: var(--color-primary); font: inherit; font-size: 14px; cursor: pointer; text-decoration: underline; text-underline-offset: 3px; }
.link-action:hover { color: var(--color-primary-dark); }
.link-action:focus-visible { outline: 2px solid var(--color-focus); outline-offset: 3px; border-radius: 2px; }
</style>
