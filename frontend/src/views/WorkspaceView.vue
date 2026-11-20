<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import WorkspaceShell from '../components/WorkspaceShell.vue'
import PublicVideoCard from '../components/PublicVideoCard.vue'
import { publicApi } from '../api/public'
import type { PublicVideo, TagCount } from '../api/public'
import { useStudioError } from '../composables/useStudioError'

const route = useRoute()
const router = useRouter()
const explain = useStudioError()
const title = ref('')
const category = ref('')
const tag = ref('')
const sort = ref('latest')
const categories = ref<{ id: string; name: string }[]>([])
const tags = ref<TagCount[]>([])
const videos = ref<PublicVideo[]>([])
const page = ref(1)
const total = ref(0)
const loading = ref(false)
const categoryError = ref('')
const tagError = ref('')
const error = ref('')
let activeRequest: AbortController | undefined
let categoryRequest: AbortController | undefined
let tagRequest: AbortController | undefined
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

async function loadTags() {
  tagRequest?.abort()
  const controller = new AbortController()
  tagRequest = controller
  tagError.value = ''
  try {
    const result = await publicApi.tags(controller.signal)
    if (!stopped && !controller.signal.aborted) tags.value = result.items
  } catch (failure) { if (!stopped && !controller.signal.aborted) tagError.value = explain(failure) }
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
  const queryTag = typeof route.query.tag === 'string' ? route.query.tag : ''
  const querySort = typeof route.query.sort === 'string' ? route.query.sort : 'latest'
  const queryPage = Number(route.query.page ?? 1)
  title.value = queryTitle
  category.value = queryCategory
  tag.value = queryTag
  sort.value = querySort === 'popular' ? 'popular' : 'latest'
  if (queryTitle.length > 120 || !Number.isInteger(queryPage) || queryPage < 1 || queryPage > 1000
      || (queryCategory && !/^[1-9]\d{0,18}$/.test(queryCategory))
      || queryTag.length > 20 || (querySort !== 'latest' && querySort !== 'popular')) {
    error.value = '搜索词最长 120 字，标签最长 20 字，分类无效或页码不在 1～1000 之间。'
    loading.value = false
    return
  }
  page.value = queryPage
  try {
    const result = await publicApi.list(queryTitle, queryCategory, queryTag, sort.value, queryPage, controller.signal)
    if (controller.signal.aborted) return
    videos.value = result.items
    total.value = result.total
  } catch (failure) { if (!controller.signal.aborted) error.value = explain(failure) }
  finally { if (!controller.signal.aborted) loading.value = false }
}

function navigate(next = 1, nextCategory = category.value, nextTag = tag.value, nextSort = sort.value) {
  const query: Record<string, string> = {}
  if (title.value.trim()) query.title = title.value.trim()
  if (nextCategory) query.category = nextCategory
  if (nextTag) query.tag = nextTag
  if (nextSort === 'popular') query.sort = 'popular'
  if (next > 1) query.page = String(next)
  void router.push({ path: '/', query })
}

watch(() => route.fullPath, load, { immediate: true })
onMounted(() => { void loadCategories(); void loadTags() })
onBeforeUnmount(() => { stopped = true; activeRequest?.abort(); categoryRequest?.abort(); tagRequest?.abort() })
</script>

<template>
  <WorkspaceShell>
    <div class="studio-heading public-hero">
      <div><p class="eyebrow">FRAME ROOM / WATCH</p><h1>{{ route.query.title ? '搜索作品' : tag ? `标签：${tag}` : '发现' }}</h1></div>
      <div class="home-actions"><RouterLink class="home-action secondary" to="/feed"><span aria-hidden="true">▷</span>竖屏浏览</RouterLink><RouterLink class="home-action primary" to="/studio"><span aria-hidden="true">＋</span>发布作品</RouterLink></div>
    </div>

    <form class="public-search" role="search" @submit.prevent="navigate()">
      <label class="sr-only" for="video-search">按标题找作品</label>
      <input id="video-search" v-model="title" type="search" maxlength="120" placeholder="按标题搜索作品" />
      <el-button type="primary" native-type="submit">搜索</el-button>
    </form>
    <div class="category-chips" aria-label="按分类筛选">
      <button type="button" :class="{ active: !category }" @click="navigate(1, '', tag, sort)">全部</button>
      <button v-for="item in categories" :key="item.id" type="button" :class="{ active: category === item.id }" @click="navigate(1, item.id, tag, sort)">{{ item.name }}</button>
    </div>
    <div v-if="tags.length || tagError" class="tag-chips" aria-label="按标签筛选">
      <span>标签</span>
      <button type="button" :class="{ active: !tag }" @click="navigate(1, category, '', sort)">全部</button>
      <button v-for="item in tags" :key="item.name" type="button" :class="{ active: tag === item.name }" @click="navigate(1, category, item.name, sort)">{{ item.name }} <small>{{ item.uses }}</small></button>
    </div>
    <el-alert v-if="tagError" :title="tagError" type="error" :closable="false" role="alert" />
    <el-alert v-if="categoryError" :title="categoryError" type="error" :closable="false" role="alert" />

    <div class="public-results-heading">
      <h2>{{ route.query.title ? `“${route.query.title}” 的搜索结果` : tag ? `标签“${tag}”` : '最新发布' }}</h2>
      <span v-if="!loading && !error">{{ total }} 部作品</span>
      <div class="sort-toggle" role="group" aria-label="排序方式">
        <button type="button" :class="{ active: sort === 'latest' }" @click="navigate(1, category, tag, 'latest')">最新</button>
        <button type="button" :class="{ active: sort === 'popular' }" @click="navigate(1, category, tag, 'popular')">最热</button>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" />
    <div v-if="loading" class="public-video-grid" aria-busy="true" aria-label="正在加载作品">
      <div v-for="index in 6" :key="index" class="public-card-skeleton" aria-hidden="true"></div>
    </div>
    <div v-else-if="videos.length" class="public-video-grid">
      <PublicVideoCard v-for="video in videos" :key="video.id" :video="video" />
    </div>
    <p v-else-if="!error" class="empty-state">{{ route.query.title || category || tag ? '没有找到匹配的作品，换个关键词、标签或分类试试。' : '还没有已发布的作品。你可以进入“我的作品”上传视频并提交审核。' }}</p>
    <div v-if="error" class="inline-actions empty-retry"><el-button @click="load">重新加载</el-button></div>
    <div class="page-controls">
      <el-button :disabled="loading || page <= 1" @click="navigate(page - 1, category, tag, sort)">上一页</el-button>
      <span>第 {{ page }} 页</span>
      <el-button :disabled="loading || !!error || page * 12 >= total || page >= 1000" @click="navigate(page + 1, category, tag, sort)">下一页</el-button>
    </div>
  </WorkspaceShell>
</template>
