<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import 'element-plus/es/components/message-box/style/css'
import WorkspaceShell from '../components/WorkspaceShell.vue'
import { categoryApi } from '../api/categories'
import type { ManagedCategory } from '../api/categories'
import { ApiFailure } from '../api/http'
import { useStudioError } from '../composables/useStudioError'

const explain = useStudioError()
const items = ref<ManagedCategory[]>([])
const page = ref(1)
const total = ref(0)
const loading = ref(false)
const busy = ref(false)
const needsRefresh = ref(false)
const editorStale = ref(false)
const editing = ref<ManagedCategory>()
const name = ref('')
const sortOrder = ref<number | string>(0)
const enabled = ref(true)
const error = ref('')
const listError = ref('')
const notice = ref('')
let activeRequest: AbortController | undefined
let stopped = false

async function confirmAction(message: string, label: string) {
  try {
    await ElMessageBox.confirm(message, '确认分类操作', {
      confirmButtonText: label,
      cancelButtonText: '取消',
      type: 'warning',
      closeOnClickModal: false,
      distinguishCancelAndClose: true,
    })
    return !stopped
  } catch (failure) {
    if (failure === 'cancel' || failure === 'close') return false
    throw failure
  }
}

async function load(next = page.value) {
  activeRequest?.abort()
  const controller = new AbortController()
  activeRequest = controller
  loading.value = true
  listError.value = ''
  items.value = []
  try {
    let result = await categoryApi.list(next, controller.signal)
    const last = Math.max(1, Math.min(1000, Math.ceil(result.total / 20)))
    if (next > last) { next = last; result = await categoryApi.list(next, controller.signal) }
    if (stopped || controller.signal.aborted) return
    items.value = result.items
    page.value = next
    total.value = result.total
    needsRefresh.value = false
  } catch (failure) {
    if (!stopped && !controller.signal.aborted) { listError.value = explain(failure); needsRefresh.value = true }
  } finally { if (!stopped && !controller.signal.aborted) loading.value = false }
}

function select(category?: ManagedCategory) {
  if (busy.value || loading.value) return
  editing.value = category ? { ...category } : undefined
  editorStale.value = false
  name.value = category?.name ?? ''
  sortOrder.value = category?.sortOrder ?? 0
  enabled.value = category?.enabled ?? true
  error.value = ''
  notice.value = ''
}

function failed(failure: unknown) {
  error.value = explain(failure)
  if (!(failure instanceof ApiFailure) || failure.status === 0 || failure.status >= 500 || failure.code === 'STALE_WRITE' || failure.status === 404) {
    needsRefresh.value = true
    editorStale.value = !!editing.value
    error.value += '。请刷新列表核对结果；编辑时需重新选择分类，输入暂保留供核对。'
  }
}

async function save() {
  if (busy.value || loading.value || needsRefresh.value || editorStale.value) return
  const normalized = name.value.normalize('NFKC').trim()
  const order = Number(sortOrder.value)
  if (!normalized || normalized.length > 32 || /[\u0000-\u001f\u007f-\u009f]/.test(normalized) || sortOrder.value === '' || !Number.isInteger(order) || order < 0 || order > 10000) {
    error.value = '名称需为 1～32 位非控制字符，排序值需为 0～10000 的整数。'
    return
  }
  const snapshot = editing.value ? { ...editing.value } : undefined
  const targetEnabled = enabled.value
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    if (snapshot && snapshot.enabled !== targetEnabled) {
      const label = targetEnabled ? '确认启用' : '确认停用'
      const message = targetEnabled ? `启用分类“${normalized}”？` : `停用分类“${normalized}”后，作者无法选择此分类或使用它送审。`
      if (!await confirmAction(message, label)) return
    }
    const input = { name: normalized, sortOrder: order }
    const result = snapshot ? await categoryApi.update(snapshot, input, targetEnabled) : await categoryApi.create(input)
    if (stopped) return
    editing.value = { ...result }
    name.value = result.name
    sortOrder.value = result.sortOrder
    enabled.value = result.enabled
    notice.value = '分类已保存。'
    await load()
  } catch (failure) { if (!stopped) failed(failure) }
  finally { if (!stopped) busy.value = false }
}

async function remove(category: ManagedCategory) {
  if (busy.value || loading.value || needsRefresh.value) return
  const snapshot = { ...category }
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    if (!await confirmAction(`删除分类“${snapshot.name}”？已被作品引用的分类不能删除，可改为停用。`, '确认删除')) return
    await categoryApi.remove(snapshot)
    if (stopped) return
    if (editing.value?.id === category.id) { editing.value = undefined; name.value = ''; sortOrder.value = 0; enabled.value = true }
    notice.value = '分类已删除。'
    await load()
  } catch (failure) { if (!stopped) failed(failure) }
  finally { if (!stopped) busy.value = false }
}

async function refresh() {
  if (busy.value) return
  if (editing.value) {
    editorStale.value = true
    error.value = '请核对下方最新列表，再点击“编辑”重新载入分类；当前输入暂保留。'
  }
  await load()
}
function leave(event: BeforeUnloadEvent) { if (busy.value) event.preventDefault() }
onBeforeRouteLeave(() => !busy.value || window.confirm('请求仍在进行，离开后需核对结果。确认离开？'))
onMounted(() => { void load(); window.addEventListener('beforeunload', leave) })
onBeforeUnmount(() => { stopped = true; activeRequest?.abort(); ElMessageBox.close(); window.removeEventListener('beforeunload', leave) })
</script>

<template>
  <WorkspaceShell>
    <div class="studio-heading"><div><p class="eyebrow">FRAME ROOM / CATEGORIES</p><h1>分类管理</h1><p class="muted">设置作者可选择的分类及显示顺序。</p></div><el-button :disabled="busy || loading" @click="select()">新增分类</el-button></div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" />
    <p v-if="notice" role="status">{{ notice }}</p>
    <section class="studio-panel">
      <h2>{{ editing ? `编辑分类 #${editing.id}` : '新增分类' }}</h2>
      <form class="studio-form" @submit.prevent="save">
        <fieldset :disabled="busy">
          <label for="category-name">分类名称</label><input id="category-name" v-model="name" required maxlength="128" aria-describedby="category-name-hint" />
          <p id="category-name-hint" class="muted">规范化后最多 32 字；名称不能重复。</p>
          <label for="category-order">排序值</label><input id="category-order" v-model="sortOrder" type="number" min="0" max="10000" step="1" required /><p class="muted">排序值允许相同；相同值按创建顺序稳定排列。</p>
          <template v-if="editing"><label for="category-enabled">状态</label><select id="category-enabled" v-model="enabled"><option :value="true">启用</option><option :value="false">停用</option></select></template>
          <p v-else class="muted">新增分类默认启用。</p>
          <div class="inline-actions"><el-button native-type="submit" type="primary" :loading="busy" :disabled="loading || needsRefresh || editorStale">保存分类</el-button></div>
        </fieldset>
      </form>
    </section>
    <section class="studio-panel" :aria-busy="loading">
      <div class="section-heading"><h2>全部分类 <span class="count">{{ total }}</span></h2><el-button :loading="loading" :disabled="busy" @click="refresh">刷新列表</el-button></div>
      <el-alert v-if="listError" :title="listError" type="error" :closable="false" role="alert" />
      <p v-if="loading" role="status">正在读取分类…</p>
      <div v-else class="category-table"><table><caption>包含启用和停用的分类</caption><thead><tr><th scope="col">名称</th><th scope="col">排序</th><th scope="col">状态</th><th scope="col">操作</th></tr></thead><tbody><tr v-for="category in items" :key="category.id"><td>{{ category.name }}</td><td>{{ category.sortOrder }}</td><td>{{ category.enabled ? '启用' : '停用' }}</td><td><el-button :disabled="busy" @click="select(category)">编辑</el-button><el-button type="danger" :disabled="busy || needsRefresh" @click="remove(category)">删除</el-button></td></tr></tbody></table></div>
      <p v-if="!loading && !listError && !items.length" class="empty-state">暂无分类，可在上方创建。</p>
      <div class="page-controls"><el-button :disabled="busy || loading || page <= 1" @click="load(page - 1)">上一页</el-button><span>第 {{ page }} 页</span><el-button :disabled="busy || loading || !!listError || page * 20 >= total || page >= 1000" @click="load(page + 1)">下一页</el-button></div>
    </section>
  </WorkspaceShell>
</template>

<style scoped>
.category-table { overflow-x: auto; }
table { width: 100%; border-collapse: collapse; text-align: left; font-size: 14px; }
caption { text-align: left; padding-bottom: 14px; color: #59665f; }
th, td { padding: 14px 10px; border-bottom: 1px solid #d9ddd5; }
td:first-child { overflow-wrap: anywhere; min-width: 110px; }
td:last-child { white-space: nowrap; }
</style>
