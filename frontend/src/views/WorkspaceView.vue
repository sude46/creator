<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import WorkspaceShell from '../components/WorkspaceShell.vue'
import PublicVideoCard from '../components/PublicVideoCard.vue'
import { publicApi } from '../api/public'
import type { PublicVideo } from '../api/public'
import { useStudioError } from '../composables/useStudioError'

const route = useRoute()
const router = useRouter()
const explain = useStudioError()
const title = ref('')
const category = ref('')
const categories = ref<{ id: string; name: string }[]>([])
const videos = ref<PublicVideo[]>([])
const page = ref(1)
const total = ref(0)
const loading = ref(false)
const categoryError = ref('')
const error = ref('')
let activeRequest: AbortController | undefined
let categoryRequest: AbortController | undefined
let stopped = false

async function loadCategories() {
  categoryRequest?.abort()
  const controller = new AbortController()
  categoryRequest = controller
  categoryError.value = ''
  try {
    const result = await publicApi.categories(controller.signal)
    if (!stopped && !controller.signal.aborted) categories.value = result.items
  } catch (failure) { if (!stopped && !controller.signal.aborted) categoryError.value = explain(failure) }
}

async function load() {
  activeRequest?.abort()
  const controller = new AbortController()
  activeRequest = controller
  loading.value = true
  error.value = ''
  videos.value = []
  const queryTitle = typeof route.query.title === 'string' ? route.query.title : ''
  const queryCategory = typeof route.query.category === 'string' ? route.query.category : ''
  const queryPage = Number(route.query.page ?? 1)
  title.value = queryTitle
  category.value = queryCategory
  if (queryTitle.length > 120 || !Number.isInteger(queryPage) || queryPage < 1 || queryPage > 1000
      || (queryCategory && !/^[1-9]\d{0,18}$/.test(queryCategory))) {
    error.value = '搜索词最长 120 字，分类无效或页码不在 1～1000 之间。'
    loading.value = false
    return
  }
  page.value = queryPage
  try {
    const result = await publicApi.list(queryTitle, queryCategory, queryPage, controller.signal)
    if (controller.signal.aborted) return
    videos.value = result.items
    total.value = result.total
  } catch (failure) { if (!controller.signal.aborted) error.value = explain(failure) }
  finally { if (!controller.signal.aborted) loading.value = false }
}

function navigate(next = 1, nextCategory = category.value) {
  const query: Record<string, string> = {}
  if (title.value.trim()) query.title = title.value.trim()
  if (nextCategory) query.category = nextCategory
  if (next > 1) query.page = String(next)
  void router.push({ path: '/', query })
}

watch(() => route.fullPath, load, { immediate: true })
onMounted(() => { void loadCategories() })
onBeforeUnmount(() => { stopped = true; activeRequest?.abort(); categoryRequest?.abort() })
</script>

<template>
  <WorkspaceShell>
    <div class="studio-heading public-hero">
      <div><p class="eyebrow">FRAME ROOM / WATCH</p><h1>{{ route.query.title ? '搜索作品' : '发现' }}</h1></div>
      <RouterLink class="create-link" to="/studio">发布作品 ↗</RouterLink>
    </div>

    <div class="public-toolbar">
      <form class="public-search" role="search" @submit.prevent="navigate()">
        <label class="sr-only" for="video-search">按标题找作品</label>
        <input id="video-search" v-model="title" type="search" maxlength="120" placeholder="按标题搜索作品" />
        <el-button type="primary" native-type="submit">搜索</el-button>
      </form>
      <div class="category-chips" aria-label="按分类筛选">
        <button type="button" :class="{ active: !category }" @click="navigate(1, '')">全部</button>
        <button v-for="item in categories" :key="item.id" type="button" :class="{ active: category === item.id }" @click="navigate(1, item.id)">{{ item.name }}</button>
      </div>
    </div>
    <el-alert v-if="categoryError" :title="categoryError" type="error" :closable="false" role="alert" />

    <div class="public-results-heading">
      <h2>{{ route.query.title ? `“${route.query.title}” 的搜索结果` : '最新发布' }}</h2>
      <span v-if="!loading && !error">{{ total }} 部作品</span>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" />
    <div v-if="loading" class="public-video-grid" aria-busy="true" aria-label="正在加载作品">
      <div v-for="index in 6" :key="index" class="public-card-skeleton" aria-hidden="true"></div>
    </div>
    <div v-else-if="videos.length" class="public-video-grid">
      <PublicVideoCard v-for="video in videos" :key="video.id" :video="video" />
    </div>
    <p v-else-if="!error" class="empty-state">{{ route.query.title || category ? '没有找到匹配的作品，换个关键词或分类试试。' : '还没有已发布的作品。你可以进入“我的作品”上传视频并提交审核。' }}</p>
    <div v-if="error" class="inline-actions empty-retry"><el-button @click="load">重新加载</el-button></div>
    <div class="page-controls">
      <el-button :disabled="loading || page <= 1" @click="navigate(page - 1)">上一页</el-button>
      <span>第 {{ page }} 页</span>
      <el-button :disabled="loading || !!error || page * 12 >= total || page >= 1000" @click="navigate(page + 1)">下一页</el-button>
    </div>
  </WorkspaceShell>
</template>
