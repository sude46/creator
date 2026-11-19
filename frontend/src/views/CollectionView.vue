<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import WorkspaceShell from '../components/WorkspaceShell.vue'
import PublicVideoCard from '../components/PublicVideoCard.vue'
import { publicApi } from '../api/public'
import type { PublicCollection, PublicVideo } from '../api/public'
import { useStudioError } from '../composables/useStudioError'
const route = useRoute(); const explain = useStudioError(); const id = String(route.params.id)
const collection = ref<PublicCollection>(); const items = ref<PublicVideo[]>([]); const page = ref(1); const total = ref(0); const loading = ref(false); const error = ref('')
let request: AbortController | undefined; let stopped = false
async function load(next = page.value) { request?.abort(); const c = new AbortController(); request = c; loading.value = true; error.value = ''; try { const result = await publicApi.collection(id, next, c.signal); if (!stopped && !c.signal.aborted) { collection.value = result.collection; items.value = result.items.items; total.value = result.items.total; page.value = result.items.page } } catch (f) { if (!stopped && !c.signal.aborted) error.value = explain(f) } finally { if (!stopped && !c.signal.aborted) loading.value = false } }
onMounted(() => { if (/^[1-9]\d{0,18}$/.test(id)) void load(); else error.value = '合集编号无效' }); onBeforeUnmount(() => { stopped = true; request?.abort() })
</script>
<template><WorkspaceShell><RouterLink class="back-link" to="/">← 发现</RouterLink><el-alert v-if="error" :title="error" type="error" :closable="false" role="alert"/><p v-if="loading" class="empty-state">正在加载合集…</p><template v-if="collection"><div class="studio-heading"><div><p class="eyebrow">FRAME ROOM / COLLECTION</p><h1>{{ collection.title }}</h1><p class="muted">@{{ collection.ownerName }} · {{ collection.itemCount }} 部公开作品</p></div></div><p class="video-description">{{ collection.description || '这个合集还没有简介。' }}</p><div v-if="items.length" class="public-video-grid"><PublicVideoCard v-for="item in items" :key="item.id" :video="item"/></div><p v-else class="empty-state">这个合集暂时没有公开作品。</p><div class="page-controls"><el-button :disabled="loading || page <= 1" @click="load(page - 1)">上一页</el-button><span>第 {{ page }} 页</span><el-button :disabled="loading || page * 12 >= total || page >= 1000" @click="load(page + 1)">下一页</el-button></div></template></WorkspaceShell></template>
