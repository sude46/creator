<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import WorkspaceShell from '../components/WorkspaceShell.vue'
import { notificationApi } from '../api/notifications'
import type { Notification } from '../api/notifications'
import { formatTime } from '../api/creator'
import { useStudioError } from '../composables/useStudioError'
const explain = useStudioError(); const items = ref<Notification[]>([]); const page = ref(1); const total = ref(0); const unread = ref(0); const loading = ref(false); const error = ref('')
let request: AbortController | undefined; let stopped = false
async function load(next = page.value) { request?.abort(); const c = new AbortController(); request = c; loading.value = true; error.value = ''; try { const [list, count] = await Promise.all([notificationApi.list(next, c.signal), notificationApi.unread(c.signal)]); if (!stopped && !c.signal.aborted) { items.value = list.items; page.value = list.page; total.value = list.total; unread.value = count.unread } } catch (f) { if (!stopped && !c.signal.aborted) error.value = explain(f) } finally { if (!stopped && !c.signal.aborted) loading.value = false } }
async function readAll() { try { await notificationApi.readAll(); await load() } catch (f) { if (!stopped) error.value = explain(f) } }
onMounted(() => { void load() }); onBeforeUnmount(() => { stopped = true; request?.abort() })
</script>
<template><WorkspaceShell><div class="studio-heading"><div><p class="eyebrow">FRAME ROOM / INBOX</p><h1>通知</h1><p class="muted">审核、评论和关注消息。未读 {{ unread }} 条。</p></div><el-button :disabled="!unread" @click="readAll">全部标为已读</el-button></div><el-alert v-if="error" :title="error" type="error" :closable="false" role="alert"/><section class="studio-panel" :aria-busy="loading"><p v-if="loading" class="muted">正在加载通知…</p><article v-for="item in items" :key="item.id" class="history-item"><div class="section-heading"><div><strong>{{ item.title }}</strong><p v-if="item.body" class="muted">{{ item.body }}</p></div><time>{{ formatTime(item.createdAt) }}</time></div><RouterLink v-if="item.link" :to="item.link">查看详情</RouterLink><el-tag v-if="!item.readAt" type="warning">未读</el-tag></article><p v-if="!loading && !items.length" class="empty-state">还没有通知。</p><div class="page-controls"><el-button :disabled="loading || page <= 1" @click="load(page - 1)">上一页</el-button><span>第 {{ page }} 页</span><el-button :disabled="loading || page * 20 >= total || page >= 1000" @click="load(page + 1)">下一页</el-button></div></section></WorkspaceShell></template>
