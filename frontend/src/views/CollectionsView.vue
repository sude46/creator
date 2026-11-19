<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import WorkspaceShell from '../components/WorkspaceShell.vue'
import { creatorApi } from '../api/creator'
import type { Collection } from '../api/creator'
import { formatTime } from '../api/creator'
import { useStudioError } from '../composables/useStudioError'

const explain = useStudioError()
const items = ref<Collection[]>([]); const loading = ref(false); const busy = ref(false); const error = ref(''); const notice = ref('')
const title = ref(''); const description = ref(''); const visibility = ref<'PUBLIC' | 'PRIVATE'>('PUBLIC')
const editing = ref<Collection>()
let request: AbortController | undefined; let stopped = false
async function load() { request?.abort(); const c = new AbortController(); request = c; loading.value = true; error.value = ''; try { const p = await creatorApi.collections(c.signal); if (!stopped && !c.signal.aborted) items.value = p.items } catch (f) { if (!stopped && !c.signal.aborted) error.value = explain(f) } finally { if (!stopped && !c.signal.aborted) loading.value = false } }
function reset() { title.value = ''; description.value = ''; visibility.value = 'PUBLIC'; editing.value = undefined }
function edit(c: Collection) { editing.value = c; title.value = c.title; description.value = c.description; visibility.value = c.visibility === 'PRIVATE' ? 'PRIVATE' : 'PUBLIC' }
async function save() {
  if (busy.value) return
  if (!title.value.trim()) { error.value = '请填写合集标题。'; return }
  busy.value = true; error.value = ''; notice.value = ''
  try { if (editing.value) await creatorApi.updateCollection(editing.value, title.value.trim(), description.value.trim(), visibility.value); else await creatorApi.createCollection(title.value.trim(), description.value.trim(), visibility.value); if (!stopped) { notice.value = editing.value ? '合集已更新。' : '合集已创建。'; reset(); await load() } }
  catch (f) { if (!stopped) error.value = explain(f) }
  finally { if (!stopped) busy.value = false }
}
async function remove(c: Collection) { if (busy.value || !window.confirm(`删除合集「${c.title}」？作品不会被删除。`)) return; busy.value = true; error.value = ''; try { await creatorApi.removeCollection(c); if (!stopped) { notice.value = '合集已删除。'; await load() } } catch (f) { if (!stopped) error.value = explain(f) } finally { if (!stopped) busy.value = false } }
onMounted(() => { void load() }); onBeforeUnmount(() => { stopped = true; request?.abort() })
</script>
<template><WorkspaceShell><div class="studio-heading"><div><p class="eyebrow">FRAME ROOM / COLLECTIONS</p><h1>我的合集</h1><p class="muted">把作品整理成系列；公开合集会出现在作者主页。</p></div><RouterLink class="create-link" to="/studio">返回我的作品</RouterLink></div><el-alert v-if="error" :title="error" type="error" :closable="false" role="alert"/><p v-if="notice" class="muted" role="status">{{ notice }}</p><section class="studio-panel"><h2>{{ editing ? '编辑合集' : '新建合集' }}</h2><form class="studio-form" @submit.prevent="save"><label for="collection-title">标题</label><input id="collection-title" v-model="title" maxlength="80" required/><label for="collection-description">简介</label><textarea id="collection-description" v-model="description" maxlength="500" rows="3"/><label for="collection-visibility">可见范围</label><select id="collection-visibility" v-model="visibility"><option value="PUBLIC">公开</option><option value="PRIVATE">仅自己可见</option></select><div class="inline-actions"><el-button type="primary" native-type="submit" :loading="busy">{{ editing ? '保存合集' : '创建合集' }}</el-button><el-button v-if="editing" :disabled="busy" @click="reset">取消</el-button></div></form></section><section class="studio-panel" :aria-busy="loading"><div class="section-heading"><h2>合集列表 <span class="count">{{ items.length }}</span></h2><el-button :loading="loading" @click="load">刷新</el-button></div><p v-if="loading" class="muted">正在加载合集…</p><article v-for="item in items" :key="item.id" class="history-item"><div class="section-heading"><div><strong>{{ item.title }}</strong><p class="muted">{{ item.visibility === 'PUBLIC' ? '公开' : '仅自己可见' }} · {{ item.itemCount }} 部作品 · {{ formatTime(item.createdAt) }}</p></div><div class="inline-actions"><el-button @click="edit(item)">编辑</el-button><el-button type="danger" @click="remove(item)">删除</el-button></div></div><p>{{ item.description || '暂无简介。' }}</p></article><p v-if="!loading && !items.length" class="empty-state">还没有合集。</p></section></WorkspaceShell></template>
