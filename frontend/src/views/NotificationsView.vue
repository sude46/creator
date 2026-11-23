<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import WorkspaceShell from '../components/WorkspaceShell.vue'
import { notificationApi } from '../api/notifications'
import { notificationState } from '../stores/notifications'
import type { Notification, NotificationType } from '../api/notifications'
import { formatTime } from '../api/creator'
import { useStudioError } from '../composables/useStudioError'
const router = useRouter()
const explain = useStudioError(); const items = ref<Notification[]>([]); const page = ref(1); const total = ref(0); const unread = ref(0); const loading = ref(false); const reading = ref<string>(); const unreadOnly = ref(false); const type = ref<NotificationType | ''>(''); const error = ref(''); const notice = ref('')
let request: AbortController | undefined; let stopped = false
async function load(next = page.value) { request?.abort(); const c = new AbortController(); request = c; loading.value = true; error.value = ''; try { const [list, count] = await Promise.all([notificationApi.list(next, unreadOnly.value, type.value, c.signal), notificationApi.unread(c.signal)]); if (!stopped && !c.signal.aborted) { items.value = list.items; page.value = list.page; total.value = list.total; unread.value = count.unread; notificationState.set(count.unread) } } catch (f) { if (!stopped && !c.signal.aborted) error.value = explain(f) } finally { if (!stopped && !c.signal.aborted) loading.value = false } }
async function markRead(item: Notification, reportError = true) {
  if (item.readAt || reading.value) return true
  reading.value = item.id
  try {
    await notificationApi.read(item.id)
    if (!stopped) {
      item.readAt = new Date().toISOString()
      unread.value = Math.max(0, unread.value - 1)
      notificationState.decrement()
      if (unreadOnly.value) await load()
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
async function remove(item: Notification) {
  if (!window.confirm('删除这条通知？')) return
  try {
    await notificationApi.remove(item.id)
    if (stopped) return
    if (!item.readAt) notificationState.decrement()
    notice.value = '通知已删除。'
    await load()
  } catch (failure) { if (!stopped) error.value = explain(failure) }
}
async function clearRead() {
  if (!window.confirm('清空所有已读通知？')) return
  try {
    await notificationApi.clearRead()
    if (stopped) return
    notice.value = '已清空已读通知。'
    notificationState.refresh()
    await load(1)
  } catch (failure) { if (!stopped) error.value = explain(failure) }
}
function setFilter(nextUnread: boolean, nextType: NotificationType | '') {
  unreadOnly.value = nextUnread
  type.value = nextType
  void load(1)
}
onMounted(() => { void load() }); onBeforeUnmount(() => { stopped = true; request?.abort() })
</script>
<template><WorkspaceShell><div class="studio-heading"><div><p class="eyebrow">FRAME ROOM / INBOX</p><h1>通知</h1><p class="muted">审核、评论和关注消息。未读 {{ unread }} 条。</p></div><div class="inline-actions"><el-button :disabled="!unread" @click="readAll">全部标为已读</el-button><el-button @click="clearRead">清空已读</el-button></div></div><el-alert v-if="error" :title="error" type="error" :closable="false" role="alert"/><p v-if="notice" class="muted" role="status">{{ notice }}</p><div class="filter-tabs" role="group" aria-label="筛选通知"><button type="button" :class="{ active: !unreadOnly && !type }" @click="setFilter(false, '')">全部</button><button type="button" :class="{ active: unreadOnly }" @click="setFilter(true, '')">未读</button><button type="button" :class="{ active: type === 'REVIEW' }" @click="setFilter(false, 'REVIEW')">审核</button><button type="button" :class="{ active: type === 'COMMENT' }" @click="setFilter(false, 'COMMENT')">评论</button><button type="button" :class="{ active: type === 'FOLLOW' }" @click="setFilter(false, 'FOLLOW')">关注</button><button type="button" :class="{ active: type === 'REPORT' }" @click="setFilter(false, 'REPORT')">举报</button><button type="button" :class="{ active: type === 'APPEAL' }" @click="setFilter(false, 'APPEAL')">申诉</button><button type="button" :class="{ active: type === 'MODERATION' }" @click="setFilter(false, 'MODERATION')">治理</button><button type="button" :class="{ active: type === 'MENTION' }" @click="setFilter(false, 'MENTION')">提及</button></div><section class="studio-panel" :aria-busy="loading"><p v-if="loading" class="muted">正在加载通知…</p><article v-for="item in items" :key="item.id" class="history-item"><div class="section-heading"><div><strong>{{ item.title }}</strong><p v-if="item.body" class="muted">{{ item.body }}</p></div><time>{{ formatTime(item.createdAt) }}</time></div><div class="notification-actions"><button v-if="item.link" type="button" class="link-action" @click="open(item)">查看详情</button><el-button v-if="!item.readAt" size="small" :loading="reading === item.id" :disabled="!!reading && reading !== item.id" @click="markRead(item)">标为已读</el-button><el-tag v-else type="info">已读</el-tag><el-button size="small" @click="remove(item)">删除</el-button></div></article><p v-if="!loading && !items.length" class="empty-state">还没有通知。</p><div class="page-controls"><el-button :disabled="loading || page <= 1" @click="load(page - 1)">上一页</el-button><span>第 {{ page }} 页</span><el-button :disabled="loading || page * 20 >= total || page >= 1000" @click="load(page + 1)">下一页</el-button></div></section></WorkspaceShell></template>

<style scoped>
.notification-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; margin-top: 8px; }
.link-action { border: 0; background: none; padding: 0; color: var(--color-primary); font: inherit; font-size: 14px; cursor: pointer; text-decoration: underline; text-underline-offset: 3px; }
.link-action:hover { color: var(--color-primary-dark); }
.link-action:focus-visible { outline: 2px solid var(--color-focus); outline-offset: 3px; border-radius: 2px; }
.filter-tabs { display: flex; flex-wrap: wrap; gap: 8px; margin: 0 0 16px; }
.filter-tabs button { border: 1px solid var(--color-control-border); border-radius: 999px; background: var(--color-surface); color: var(--color-secondary); padding: 5px 14px; font: inherit; font-size: 12px; cursor: pointer; }
.filter-tabs button.active { background: var(--color-primary); border-color: var(--color-primary); color: var(--color-surface); }
</style>
