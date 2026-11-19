<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { publicApi } from '../api/public'
import type { PublicVideo } from '../api/public'
import { cachedCover } from '../api/coverCache'
import { useStudioError } from '../composables/useStudioError'

const route = useRoute()
const router = useRouter()
const explain = useStudioError()
const scroller = ref<HTMLElement>()
const slides = ref<HTMLElement[]>([])
const videoRefs = ref<HTMLVideoElement[]>([])
const items = ref<PublicVideo[]>([])
const categories = ref<{ id: string; name: string }[]>([])
const category = ref('')
const current = ref(0)
const sources = ref<Record<string, string>>({})
const covers = ref<Record<string, string>>({})
const loading = ref(true)
const error = ref('')
const notice = ref('')
const muted = ref(localStorage.getItem('frame-feed-muted') !== 'false')
const favorited = ref<boolean>(); const liked = ref<boolean>(); const likeBusy = ref(false)
const favoriteBusy = ref(false)
const progress = ref(0)
const signing = new Set<string>()
let observer: IntersectionObserver | undefined
let request: AbortController | undefined
let categoryRequest: AbortController | undefined
let favoriteRequest: AbortController | undefined
let stopped = false

function scrollToCurrent() {
  const slide = slides.value[current.value]
  if (slide && scroller.value) scroller.value.scrollTo({ top: slide.offsetTop, behavior: 'auto' })
}
function openDetail() { const item = items.value[current.value]; if (item) void router.push(`/videos/${item.id}`) }
function savePosition() { const item = items.value[current.value]; if (item) sessionStorage.setItem('frame-feed-anchor', item.id) }
function signed(id: string) { return !!sources.value[id] }

async function loadSource(item: PublicVideo) {
  if (signed(item.id) || signing.has(item.id)) return
  signing.add(item.id)
  try {
    const media = await publicApi.media(item.id, 'SOURCE', request!.signal)
    if (!stopped && !request?.signal.aborted) sources.value = { ...sources.value, [item.id]: media.url }
  } catch { /* keep the poster if the source cannot be signed */ }
  finally { signing.delete(item.id) }
}

async function loadCover(item: PublicVideo) {
  if (!item.hasCover || covers.value[item.id]) return
  try {
    const entry = await cachedCover(item.coverKey ?? `feed:${item.id}`, () => publicApi.media(item.id, 'COVER', request!.signal))
    if (!stopped && !request?.signal.aborted) covers.value = { ...covers.value, [item.id]: entry.url }
  } catch { /* keep the dark backdrop */ }
}

async function recordView(item: PublicVideo, index: number) {
  if (sessionStorage.getItem(`frame-viewed-${item.id}`)) return
  sessionStorage.setItem(`frame-viewed-${item.id}`, '1')
  try {
    const result = await publicApi.view(item.id)
    if (!stopped && items.value[index]) items.value[index] = { ...items.value[index], viewCount: result.viewCount }
  } catch { /* best effort */ }
}
async function loadMedia(index = current.value) {
  if (items.value[index]) void recordView(items.value[index], index)
  const range = [index - 1, index, index + 1].filter(i => i >= 0 && i < items.value.length)
  await Promise.all(range.flatMap(i => { const item = items.value[i]; return [loadSource(item), loadCover(item)] }))
  await nextTick()
  playCurrent()
}

function playCurrent() {
  items.value.forEach((_, index) => {
    const video = videoRefs.value[index]
    if (!video) return
    video.muted = muted.value
    if (index === current.value) video.play().catch(() => {})
    else video.pause()
  })
}

function setCurrent(index: number) {
  if (index < 0 || index >= items.value.length || index === current.value) return
  current.value = index
  progress.value = 0
  savePosition()
  void loadMedia(index)
  void loadFavorite(); void loadLike()
}

async function loadFavorite() {
  const item = items.value[current.value]
  if (!item) return
  favoriteRequest?.abort()
  const controller = new AbortController()
  favoriteRequest = controller
  favorited.value = undefined
  try {
    const result = await publicApi.favoriteState(item.id, controller.signal)
    if (!stopped && !controller.signal.aborted) favorited.value = result.favorited
  } catch { if (!stopped && !controller.signal.aborted) favorited.value = undefined }
}

async function loadLike() { const item = items.value[current.value]; if (!item) return; liked.value = undefined; try { const r = await publicApi.likeState(item.id, new AbortController().signal); if (!stopped) liked.value = r.liked } catch { if (!stopped) liked.value = undefined } }
async function toggleLike() { const item = items.value[current.value]; if (!item || liked.value === undefined || likeBusy.value) return; const add = !liked.value; likeBusy.value = true; try { await publicApi.like(item.id, add); if (!stopped) { liked.value = add; items.value[current.value] = { ...item, likeCount: Math.max(0, item.likeCount + (add ? 1 : -1)) } } } catch { /* best effort */ } finally { if (!stopped) likeBusy.value = false } }
async function toggleFavorite() {
  const item = items.value[current.value]
  if (!item || favorited.value === undefined || favoriteBusy.value) return
  favoriteBusy.value = true; notice.value = ''
  try {
    await publicApi.favorite(item.id, !favorited.value)
    if (stopped) return
    favorited.value = !favorited.value
    items.value[current.value] = { ...item, favoriteCount: Math.max(0, item.favoriteCount + (favorited.value ? 1 : -1)) }
    notice.value = favorited.value ? '已收藏。' : '已取消收藏。'
  } catch (failure) { if (!stopped) notice.value = explain(failure) }
  finally { if (!stopped) favoriteBusy.value = false }
}

async function share() {
  const item = items.value[current.value]
  if (!item) return
  const url = `${window.location.origin}/feed/${item.id}`
  try {
    if (navigator.share) await navigator.share({ title: item.title, text: item.description || item.title, url })
    else { await navigator.clipboard.writeText(url); notice.value = '链接已复制。' }
  } catch { /* user cancelled sharing */ }
}

function toggleMute() {
  muted.value = !muted.value
  localStorage.setItem('frame-feed-muted', String(muted.value))
  playCurrent()
}

function onTimeUpdate(event: Event, index: number) {
  if (index !== current.value) return
  const video = event.target as HTMLVideoElement
  progress.value = video.duration ? (video.currentTime / video.duration) * 100 : 0
}

async function loadCategories() {
  categoryRequest?.abort(); const controller = new AbortController(); categoryRequest = controller
  try {
    const result = await publicApi.categories(controller.signal)
    if (!stopped && !controller.signal.aborted) categories.value = result.items
  } catch { /* category tabs are optional */ }
}

async function loadFeed(nextCategory = category.value) {
  request?.abort(); const controller = new AbortController(); request = controller
  loading.value = true; error.value = ''; items.value = []; sources.value = {}; covers.value = {}; current.value = 0; category.value = nextCategory
  const deepId = typeof route.params.id === 'string' ? route.params.id : (sessionStorage.getItem('frame-feed-anchor') ?? '')
  try {
    if (deepId && /^[1-9]\d{0,18}$/.test(deepId)) {
      const page = await publicApi.anchor(deepId, nextCategory, 2, 5, controller.signal)
      if (stopped || controller.signal.aborted) return
      items.value = page.items
      current.value = page.anchorIndex ?? 0
    } else {
      const page = await publicApi.list('', nextCategory, 1, controller.signal)
      if (stopped || controller.signal.aborted) return
      items.value = page.items
    }
    loading.value = false
    await nextTick()
    observer?.disconnect()
    scrollToCurrent()
    await new Promise(resolve => setTimeout(resolve, 150))
    observer = new IntersectionObserver(entries => {
      const visible = entries.filter(entry => entry.isIntersecting && entry.intersectionRatio >= 0.6)
        .sort((a, b) => b.intersectionRatio - a.intersectionRatio)[0]
      if (visible) setCurrent(Number((visible.target as HTMLElement).dataset.index))
    }, { threshold: [0, 0.6, 1] })
    slides.value.forEach(slide => slide && observer?.observe(slide))
    savePosition()
    await loadMedia(current.value)
    void loadFavorite(); void loadLike()
  } catch (failure) { if (!stopped && !controller.signal.aborted) error.value = explain(failure) }
  finally { if (!stopped && !controller.signal.aborted) loading.value = false }
}

watch(current, () => { savePosition(); void nextTick().then(playCurrent) })
watch(sources, () => { void nextTick().then(playCurrent) }, { deep: true })
onMounted(async () => { await loadCategories(); await loadFeed() })
onBeforeUnmount(() => {
  stopped = true; request?.abort(); categoryRequest?.abort(); favoriteRequest?.abort(); observer?.disconnect()
  videoRefs.value.forEach(video => { video.pause(); video.removeAttribute('src'); video.load() })
})
</script>

<template>
  <main class="feed-shell">
    <header class="feed-top">
      <RouterLink class="feed-brand" to="/">帧间</RouterLink>
      <nav class="feed-tabs" aria-label="分类"><button :class="{ active: !category }" @click="loadFeed('')">全部</button><button v-for="item in categories" :key="item.id" :class="{ active: category === item.id }" @click="loadFeed(item.id)">{{ item.name }}</button></nav>
      <div class="feed-actions"><RouterLink class="feed-action" to="/favorites" aria-label="我的收藏">♡</RouterLink><RouterLink class="feed-action feed-publish" to="/studio" aria-label="发布作品">＋</RouterLink><RouterLink class="feed-action feed-grid-link" to="/" aria-label="切换到网格">▦</RouterLink></div>
    </header>
    <p v-if="notice" class="feed-notice" role="status">{{ notice }}</p>
    <div v-if="loading" class="feed-state">正在加载…</div>
    <div v-else-if="error" class="feed-state">{{ error }} <button @click="loadFeed()">重试</button></div>
    <div v-else-if="!items.length" class="feed-state">还没有作品。<RouterLink to="/studio">去发布</RouterLink></div>
    <div v-else ref="scroller" class="feed-scroller">
      <section v-for="(item, index) in items" :key="item.id" :ref="el => slides[index] = el as HTMLElement" class="feed-slide" :data-index="index" :aria-label="item.title">
        <div class="feed-backdrop" :style="covers[item.id] ? { backgroundImage: `url(${covers[item.id]})` } : undefined"></div>
        <video v-if="Math.abs(index - current) <= 1 && sources[item.id]" :ref="el => videoRefs[index] = el as HTMLVideoElement" class="feed-video" :src="sources[item.id]" :muted="muted" playsinline preload="metadata" loop @click="openDetail" @canplay="index === current && playCurrent()" @timeupdate="onTimeUpdate($event, index)" />
        <img v-else-if="covers[item.id]" class="feed-poster" :src="covers[item.id]" alt="" referrerpolicy="no-referrer" @click="openDetail" />
        <div class="feed-shade"></div>
        <aside class="feed-rail">
          <button :disabled="likeBusy || liked === undefined" :aria-pressed="liked" @click="toggleLike">{{ liked ? '♥' : '♡' }}<span>{{ liked ? '已赞' : '点赞' }}</span></button>
          <button :disabled="favoriteBusy || favorited === undefined" :aria-pressed="favorited" @click="toggleFavorite">{{ favorited ? '♥' : '♡' }}<span>{{ favorited ? '已收藏' : '收藏' }}</span></button>
          <button @click="share">↗<span>分享</span></button>
        </aside>
        <div class="feed-copy">
          <p class="feed-category">{{ item.categoryName }}</p>
          <h1>{{ item.title }}</h1>
          <p class="feed-description">{{ item.description || '作者没有留下简介。' }}</p>
          <p class="feed-author"><RouterLink :to="`/authors/${item.authorId}`">@{{ item.authorName }}</RouterLink> · {{ item.viewCount }} 次播放 · {{ item.favoriteCount }} 收藏 · {{ item.likeCount }} 赞 · <RouterLink class="feed-detail" :to="`/videos/${item.id}`">查看详情</RouterLink></p>
        </div>
        <button class="feed-mute" :aria-label="muted ? '取消静音' : '静音'" @click="toggleMute">{{ muted ? '静音' : '声音' }}</button>
        <div class="feed-progress"><span :style="{ width: `${progress}%` }"></span></div>
      </section>
    </div>
  </main>
</template>

<style scoped>
.feed-shell{position:relative;height:100dvh;overflow:hidden;background:var(--color-cover);color:var(--color-surface)}
.feed-top{position:absolute;z-index:4;top:0;left:0;right:0;display:flex;align-items:center;gap:18px;padding:16px 20px;background:linear-gradient(to bottom,rgba(10,18,15,.8),transparent)}
.feed-brand{color:var(--color-surface);font-size:20px;font-weight:700;letter-spacing:.08em}
.feed-tabs{display:flex;gap:16px;overflow-x:auto;flex:1;scrollbar-width:none}.feed-tabs::-webkit-scrollbar{display:none}.feed-tabs button{border:1px solid transparent;background:none;color:rgba(247,244,236,.72);font:inherit;font-size:13px;white-space:nowrap;cursor:pointer;padding:6px 11px;border-radius:999px}.feed-tabs button.active{color:var(--color-surface);font-weight:700;background:rgba(247,244,236,.16);border-color:rgba(247,244,236,.22)}
.feed-actions{display:flex;align-items:center;gap:8px;color:var(--color-surface);font-size:17px}.feed-action{display:grid;place-items:center;width:38px;height:38px;border:1px solid rgba(247,244,236,.25);border-radius:12px;background:rgba(10,18,15,.35);color:var(--color-surface);text-decoration:none}.feed-action:hover{background:rgba(247,244,236,.16)}.feed-publish{background:var(--color-accent);border-color:var(--color-accent);font-size:20px}.feed-grid-link{font-size:20px}
.feed-scroller{height:100%;overflow-y:auto;scroll-snap-type:y mandatory;scrollbar-width:none}.feed-scroller::-webkit-scrollbar{display:none}
.feed-slide{position:relative;height:100dvh;scroll-snap-align:start;display:grid;place-items:center;overflow:hidden;background:#0f1a17}
.feed-backdrop{pointer-events:none;position:absolute;inset:-30px;background-size:cover;background-position:center;filter:blur(28px);opacity:.38;transform:scale(1.08)}
.feed-shade{pointer-events:none;position:absolute;inset:0;background:linear-gradient(to bottom,rgba(10,18,15,.25),transparent 35%,rgba(10,18,15,.82))}
.feed-video,.feed-poster{cursor:pointer;position:relative;z-index:1;max-width:100%;max-height:100%;width:auto;height:auto;object-fit:contain}
.feed-poster{width:100%;height:100%;object-fit:contain}
.feed-rail{position:absolute;right:18px;bottom:150px;z-index:2;display:grid;gap:18px;justify-items:center}.feed-rail button{display:grid;gap:5px;place-items:center;border:0;background:rgba(10,18,15,.35);color:var(--color-surface);font:inherit;font-size:22px;cursor:pointer;padding:9px;border-radius:50%;width:52px;height:52px}.feed-rail button span{font-size:10px;line-height:1}.feed-rail button[aria-pressed="true"]{color:#f0a096}
.feed-copy{position:absolute;z-index:2;left:20px;right:90px;bottom:34px}.feed-category{color:#f0a096;font-size:11px;letter-spacing:.1em;margin:0 0 6px}.feed-copy h1{font-size:24px;line-height:1.25;margin:0 0 8px;overflow-wrap:anywhere}.feed-description{font-size:13px;line-height:1.6;color:rgba(247,244,236,.8);margin:0 0 8px;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}.feed-detail{color:#f0a096;text-decoration:underline}.feed-author{font-size:13px;color:rgba(247,244,236,.72);margin:0}
.feed-mute{position:absolute;z-index:2;right:18px;bottom:100px;border:1px solid rgba(247,244,236,.35);background:rgba(10,18,15,.35);color:var(--color-surface);border-radius:999px;padding:5px 9px;font-size:11px;cursor:pointer}
.feed-progress{position:absolute;z-index:3;left:0;right:0;bottom:0;height:2px;background:rgba(247,244,236,.2)}.feed-progress span{display:block;height:100%;background:var(--color-accent)}
.feed-state{height:100dvh;display:grid;place-items:center;padding:30px;text-align:center;color:var(--color-on-primary);background:var(--color-cover)}.feed-state a,.feed-state button{color:#f0a096;background:none;border:0;font:inherit;text-decoration:underline;cursor:pointer}
.feed-notice{position:absolute;z-index:5;top:62px;left:50%;transform:translateX(-50%);background:rgba(10,18,15,.75);color:var(--color-surface);padding:8px 14px;border-radius:999px;font-size:12px;margin:0}
@media(min-width:900px){.feed-shell{max-width:520px;margin:0 auto;border-left:1px solid rgba(247,244,236,.12);border-right:1px solid rgba(247,244,236,.12)}.feed-copy h1{font-size:28px}}
</style>
