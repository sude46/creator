<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import WorkspaceShell from '../components/WorkspaceShell.vue'
import PublicVideoCard from '../components/PublicVideoCard.vue'
import { publicApi } from '../api/public'
import type { PublicVideo, SearchSuggestions, TagCount } from '../api/public'
import { useStudioError } from '../composables/useStudioError'

const route = useRoute()
const router = useRouter()
const explain = useStudioError()
const title = ref('')
const category = ref('')
const tag = ref('')
const author = ref('')
const sort = ref('latest')
const categories = ref<{ id: string; name: string }[]>([])
const tags = ref<TagCount[]>([])
const suggestions = ref<SearchSuggestions>()
const suggestOpen = ref(false)
const suggestLoading = ref(false)
let suggestTimer: ReturnType<typeof setTimeout> | undefined
let suggestRequest: AbortController | undefined
let searchFocused = false
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

const heading = computed(() => {
  const query = typeof route.query.title === 'string' ? route.query.title.trim() : ''
  if (query) return `搜索“${query.length > 18 ? `${query.slice(0, 18)}…` : query}”`
  if (tag.value) return `标签“${tag.value}”`
  if (category.value) return categories.value.find(item => item.id === category.value)?.name ?? '分类作品'
  return '今天，看点什么'
})
const sortLabel = computed(() => sort.value === 'popular' ? '最多播放' : sort.value === 'recommended' ? '为你推荐' : '最新发布')
const hasFilters = computed(() => Boolean(title.value.trim() || category.value || tag.value || sort.value !== 'latest'))
const featuredVideo = computed(() => !hasFilters.value && page.value === 1 && videos.value.length > 2 ? videos.value[0] : undefined)
const gridVideos = computed(() => featuredVideo.value ? videos.value.slice(1) : videos.value)
const lede = computed(() => loading.value ? '正在整理片单…' : error.value ? '片单暂时没有加载出来。' : '按分类、标签和热度，慢慢挑一部。')

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
  const queryAuthor = typeof route.query.author === 'string' ? route.query.author : ''
  const querySort = typeof route.query.sort === 'string' ? route.query.sort : 'latest'
  const queryPage = Number(route.query.page ?? 1)
  title.value = queryTitle
  category.value = queryCategory
  tag.value = queryTag
  author.value = queryAuthor
  sort.value = querySort === 'popular' || querySort === 'recommended' ? querySort : 'latest'
  if (queryTitle.length > 120 || !Number.isInteger(queryPage) || queryPage < 1 || queryPage > 1000
      || (queryCategory && !/^[1-9]\d{0,18}$/.test(queryCategory))
      || queryTag.length > 20 || queryAuthor.length > 32 || !['latest', 'popular', 'recommended'].includes(querySort)) {
    error.value = '搜索词最长 120 字，标签最长 20 字，作者最长 32 字，分类无效或页码不在 1～1000 之间。'
    loading.value = false
    return
  }
  page.value = queryPage
  try {
    const result = await publicApi.list(queryTitle, queryCategory, queryTag, queryAuthor, sort.value, queryPage, controller.signal)
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
  if (author.value.trim()) query.author = author.value.trim()
  if (nextSort !== 'latest') query.sort = nextSort
  if (next > 1) query.page = String(next)
  void router.push({ path: '/', query })
}

function clearFilters() {
  title.value = ''
  author.value = ''
  navigate(1, '', '', 'latest')
}

function hasSuggestions(value?: SearchSuggestions) {
  return !!value && (value.videos.length > 0 || value.tags.length > 0 || value.authors.length > 0)
}
async function loadSuggestions(query: string) {
  suggestRequest?.abort()
  const controller = new AbortController()
  suggestRequest = controller
  suggestLoading.value = true
  try {
    const result = await publicApi.suggestions(query, controller.signal)
    if (!stopped && !controller.signal.aborted && searchFocused && title.value.trim() === query) {
      suggestions.value = result
      suggestOpen.value = hasSuggestions(result)
    }
  } catch { if (!stopped && !controller.signal.aborted) { suggestions.value = undefined; suggestOpen.value = false } }
  finally { if (!stopped && !controller.signal.aborted) suggestLoading.value = false }
}
function onSearchFocus() {
  searchFocused = true
  if (title.value.trim()) void loadSuggestions(title.value.trim())
}
function onSearchBlur() {
  setTimeout(() => { searchFocused = false; suggestOpen.value = false }, 160)
}
function submitSearch() {
  suggestOpen.value = false
  navigate()
}
function pickVideo(item: { id: string }) { suggestOpen.value = false; void router.push(`/videos/${item.id}`) }
function pickTag(item: TagCount) { title.value = ''; suggestOpen.value = false; navigate(1, category.value, item.name, sort.value) }
function pickAuthor(item: { username: string }) { title.value = ''; suggestOpen.value = false; author.value = item.username; navigate() }
watch(title, value => {
  clearTimeout(suggestTimer)
  if (!searchFocused) return
  const query = value.trim()
  if (!query) { suggestions.value = undefined; suggestOpen.value = false; return }
  suggestTimer = setTimeout(() => void loadSuggestions(query), 250)
})
watch(() => route.fullPath, load, { immediate: true })
onMounted(() => { void loadCategories(); void loadTags() })
onBeforeUnmount(() => { stopped = true; clearTimeout(suggestTimer); activeRequest?.abort(); suggestRequest?.abort(); categoryRequest?.abort(); tagRequest?.abort() })
</script>

<template>
  <WorkspaceShell>
    <section class="discover-hero">
      <div class="discover-intro">
        <h1>{{ heading }}</h1>
        <p class="discover-lede">{{ lede }}</p>
      </div>
      <div class="home-actions">
        <RouterLink class="home-action secondary" to="/feed">
          <svg viewBox="0 0 20 20" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true"><rect x="2.25" y="3.75" width="15.5" height="12.5" rx="1.5" /><path d="M8.5 7.5 12.5 10l-4 2.5Z" fill="currentColor" stroke="none" /></svg>
          <span>竖屏浏览</span>
        </RouterLink>
        <RouterLink class="home-action primary" to="/studio">
          <svg viewBox="0 0 20 20" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" aria-hidden="true"><path d="M10 4.5v11M4.5 10h11" /></svg>
          <span>发布作品</span>
        </RouterLink>
      </div>
    </section>

    <div class="search-shell">
      <form class="public-search" role="search" @submit.prevent="submitSearch">
        <label class="sr-only" for="video-search">按标题找作品</label>
        <svg class="search-icon" viewBox="0 0 20 20" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true"><circle cx="9" cy="9" r="5.25" /><path d="m13 13 3.5 3.5" stroke-linecap="round" /></svg>
        <input id="video-search" v-model="title" type="search" maxlength="120" placeholder="按标题、简介或标签搜索作品" autocomplete="off" @focus="onSearchFocus" @blur="onSearchBlur" @keydown.esc="suggestOpen = false" />
        <el-button class="search-submit" type="primary" native-type="submit">搜索</el-button>
      </form>
      <div v-if="suggestOpen && suggestions" class="search-suggestions" role="listbox" aria-label="搜索建议">
        <div v-if="suggestions.videos.length" class="suggestion-group"><span>作品</span><button v-for="item in suggestions.videos" :key="`video-${item.id}`" type="button" role="option" @mousedown.prevent @click="pickVideo(item)">{{ item.title }}</button></div>
        <div v-if="suggestions.tags.length" class="suggestion-group"><span>标签</span><button v-for="item in suggestions.tags" :key="`tag-${item.name}`" type="button" role="option" @mousedown.prevent @click="pickTag(item)">#{{ item.name }} <small>{{ item.uses }}</small></button></div>
        <div v-if="suggestions.authors.length" class="suggestion-group"><span>作者</span><button v-for="item in suggestions.authors" :key="`author-${item.id}`" type="button" role="option" @mousedown.prevent @click="pickAuthor(item)">{{ item.displayName || item.username }} <small>@{{ item.username }}</small></button></div>
        <p v-if="suggestLoading" class="suggestion-loading">正在查找…</p>
      </div>
    </div>
    <div class="author-filter">
      <label for="author-search">作者</label>
      <input id="author-search" v-model="author" type="search" maxlength="32" placeholder="按用户名或昵称筛选" @keyup.enter="navigate()" />
      <el-button v-if="author" size="small" @click="author = ''; navigate()">清除</el-button>
    </div>

    <div class="filter-deck">
      <div class="filter-row" role="group" aria-label="按分类筛选">
        <span class="filter-label" aria-hidden="true">分类</span>
        <div class="filter-scroll">
          <button type="button" :aria-pressed="!category" @click="navigate(1, '', tag, sort)">全部</button>
          <button v-for="item in categories" :key="item.id" type="button" :aria-pressed="category === item.id" @click="navigate(1, item.id, tag, sort)">{{ item.name }}</button>
        </div>
      </div>
      <div v-if="tags.length || tagError" class="filter-row" role="group" aria-label="按标签筛选">
        <span class="filter-label" aria-hidden="true">标签</span>
        <div class="filter-scroll">
          <button type="button" :aria-pressed="!tag" @click="navigate(1, category, '', sort)">全部</button>
          <button v-for="item in tags" :key="item.name" type="button" :aria-pressed="tag === item.name" @click="navigate(1, category, item.name, sort)">{{ item.name }} <small>{{ item.uses }}</small></button>
        </div>
      </div>
    </div>

    <el-alert v-if="route.query.denied === 'admin'" title="该页面仅管理员可访问。" type="warning" role="alert" />
    <el-alert v-if="tagError" :title="tagError" type="error" :closable="false" role="alert" />
    <el-alert v-if="categoryError" :title="categoryError" type="error" :closable="false" role="alert" />

    <div class="discover-results">
      <div class="discover-results-title">
        <h2>{{ sortLabel }}</h2>
        <p v-if="!loading && !error"><span class="tabular">{{ total }}</span> 部公开作品</p>
        <p v-else-if="loading">正在加载…</p>
      </div>
      <div class="discover-tools">
        <div class="sort-toggle" role="group" aria-label="排序方式">
          <button type="button" :aria-pressed="sort === 'recommended'" @click="navigate(1, category, tag, 'recommended')">推荐</button>
          <button type="button" :aria-pressed="sort === 'latest'" @click="navigate(1, category, tag, 'latest')">最新</button>
          <button type="button" :aria-pressed="sort === 'popular'" @click="navigate(1, category, tag, 'popular')">最热</button>
        </div>
        <button class="icon-button" type="button" :disabled="loading" aria-label="刷新作品列表" @click="load()">
          <svg viewBox="0 0 20 20" width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M16 10a6 6 0 1 1-1.76-4.24" /><path d="M16 3.6V7h-3.4" /></svg>
        </button>
      </div>
    </div>

    <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" />
    <div v-if="loading" class="public-video-grid discover-grid" aria-busy="true" aria-label="正在加载作品">
      <div v-for="index in 6" :key="index" class="public-card-skeleton" aria-hidden="true"></div>
    </div>
    <div v-else-if="videos.length" class="discover-feed">
      <PublicVideoCard v-if="featuredVideo" :video="featuredVideo" featured />
      <div class="public-video-grid discover-grid" :class="{ single: gridVideos.length === 1 }">
        <PublicVideoCard v-for="video in gridVideos" :key="video.id" :video="video" />
      </div>
    </div>
    <div v-else-if="!error" class="discover-empty">
      <h3>{{ hasFilters ? '没有找到匹配的作品' : '还没有公开作品' }}</h3>
      <p>{{ hasFilters ? '换个关键词、标签或分类试试。' : '进入“我的作品”上传视频并提交审核，通过后会出现在这里。' }}</p>
      <button v-if="hasFilters" class="home-action secondary" type="button" @click="clearFilters">清除筛选</button>
      <RouterLink v-else class="home-action primary" to="/studio">去发布</RouterLink>
    </div>
    <div v-if="error" class="inline-actions empty-retry"><el-button @click="load">重新加载</el-button></div>
    <div v-if="videos.length" class="discover-pagination">
      <el-button :disabled="loading || page <= 1" @click="navigate(page - 1, category, tag, sort)">上一页</el-button>
      <span class="tabular">第 {{ page }} 页</span>
      <el-button :disabled="loading || !!error || page * 12 >= total || page >= 1000" @click="navigate(page + 1, category, tag, sort)">下一页</el-button>
    </div>
  </WorkspaceShell>
</template>
