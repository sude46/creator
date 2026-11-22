<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import WorkspaceShell from '../components/WorkspaceShell.vue'
import { adminApi } from '../api/admin'
import type { AdminTag } from '../api/admin'
import { useStudioError } from '../composables/useStudioError'

const explain = useStudioError()
const items = ref<AdminTag[]>([])
const loading = ref(false)
const busy = ref(false)
const error = ref('')
const notice = ref('')
const mergeTarget = ref<Record<string, string>>({})
let request: AbortController | undefined
let stopped = false

async function load() {
  request?.abort()
  const controller = new AbortController()
  request = controller
  loading.value = true
  error.value = ''
  try {
    const result = await adminApi.tags(controller.signal)
    if (!stopped && !controller.signal.aborted) items.value = result
  } catch (failure) { if (!stopped && !controller.signal.aborted) error.value = explain(failure) }
  finally { if (!stopped && !controller.signal.aborted) loading.value = false }
}

async function rename(tag: AdminTag) {
  if (busy.value) return
  const name = window.prompt('新的标签名称（最多 20 字）', tag.name)?.trim() ?? ''
  if (!name || name === tag.name) return
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    const updated = await adminApi.renameTag(tag, name)
    if (stopped) return
    notice.value = `标签已重命名为“${updated.name}”。`
    await load()
  } catch (failure) { if (!stopped) error.value = explain(failure) }
  finally { if (!stopped) busy.value = false }
}

async function merge(tag: AdminTag) {
  const targetId = mergeTarget.value[tag.id]
  if (!targetId || busy.value) return
  const target = items.value.find(item => item.id === targetId)
  if (!target || !window.confirm(`把“${tag.name}”合并到“${target.name}”？作品上的两个标签会去重。`)) return
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    await adminApi.mergeTag(tag, targetId)
    if (stopped) return
    notice.value = `“${tag.name}”已合并到“${target.name}”。`
    await load()
  } catch (failure) { if (!stopped) error.value = explain(failure) }
  finally { if (!stopped) busy.value = false }
}

async function remove(tag: AdminTag) {
  if (busy.value || tag.uses > 0) return
  if (!window.confirm(`删除标签“${tag.name}”？`)) return
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    await adminApi.deleteTag(tag)
    if (stopped) return
    notice.value = '标签已删除。'
    await load()
  } catch (failure) { if (!stopped) error.value = explain(failure) }
  finally { if (!stopped) busy.value = false }
}

onMounted(() => void load())
onBeforeUnmount(() => { stopped = true; request?.abort() })
</script>

<template>
  <WorkspaceShell>
    <div class="studio-heading"><div><p class="eyebrow">FRAME ROOM / TAXONOMY</p><h1>标签管理</h1><p class="muted">重命名、合并重复标签；仍被作品使用的标签不能直接删除。</p></div><el-button :loading="loading" :disabled="busy" @click="load">刷新</el-button></div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" />
    <p v-if="notice" class="muted" role="status">{{ notice }}</p>
    <section class="studio-panel" :aria-busy="loading">
      <div class="section-heading"><h2>标签 <span class="count">{{ items.length }}</span></h2></div>
      <p v-if="loading" class="empty-state" role="status">正在读取标签…</p>
      <div v-else-if="items.length" class="tag-admin-list">
        <article v-for="tag in items" :key="tag.id" class="tag-admin-row">
          <div class="tag-name"><strong>{{ tag.name }}</strong><span class="muted">{{ tag.uses }} 部作品</span></div>
          <div class="inline-actions">
            <el-button size="small" :disabled="busy" @click="rename(tag)">重命名</el-button>
            <template v-if="items.length > 1">
              <label class="sr-only" :for="`merge-${tag.id}`">合并目标</label>
              <select :id="`merge-${tag.id}`" v-model="mergeTarget[tag.id]" :disabled="busy">
                <option value="" disabled>合并到…</option>
                <option v-for="other in items.filter(item => item.id !== tag.id)" :key="other.id" :value="other.id">{{ other.name }}</option>
              </select>
              <el-button size="small" :disabled="busy || !mergeTarget[tag.id]" @click="merge(tag)">合并</el-button>
            </template>
            <el-button type="danger" size="small" :disabled="busy || tag.uses > 0" @click="remove(tag)">删除</el-button>
          </div>
        </article>
      </div>
      <p v-else-if="!error" class="empty-state">还没有标签。</p>
    </section>
  </WorkspaceShell>
</template>

<style scoped>
.tag-admin-list { display: grid; gap: 8px; }
.tag-admin-row { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 14px 16px; border: 1px solid var(--color-border); border-radius: var(--radius-card); background: var(--color-surface); }
.tag-name { display: flex; align-items: baseline; gap: 12px; min-width: 0; }
.tag-name strong { font-size: 16px; overflow-wrap: anywhere; }
.tag-name .muted { font-size: 12px; }
.tag-admin-row select { border: 1px solid var(--color-control-border); border-radius: 3px; padding: 5px 8px; font: inherit; font-size: 12px; background: var(--color-surface); color: var(--color-text); }
@media (max-width: 700px) { .tag-admin-row { flex-direction: column; align-items: flex-start; } }
</style>
