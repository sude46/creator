<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { publicApi, durationLabel } from '../api/public'
import type { PublicVideo } from '../api/public'
import { cachedCover } from '../api/coverCache'
const props = defineProps<{ video: PublicVideo }>(); const root = ref<HTMLElement>(); const cover = ref(''); const coverError = ref(false); const controller = new AbortController(); let observer: IntersectionObserver | undefined; let refresh: ReturnType<typeof setTimeout> | undefined; let visible = false
async function loadCover() {
  if (!props.video.hasCover) return
  try {
    const entry = await cachedCover(props.video.coverKey ?? `public:${props.video.id}`, () => publicApi.media(props.video.id, 'COVER', controller.signal))
    if (controller.signal.aborted) return
    coverError.value = false
    cover.value = entry.url
    clearTimeout(refresh)
    refresh = setTimeout(() => { if (visible) void loadCover() }, Math.max(30_000, entry.expiresAt - Date.now() - 30_000))
  } catch { if (!controller.signal.aborted) { cover.value = ''; coverError.value = true } }
}
onMounted(() => {
  observer = new IntersectionObserver(entries => {
    visible = entries.some(entry => entry.isIntersecting)
    if (visible) void loadCover()
  })
  if (root.value) observer.observe(root.value)
})
onBeforeUnmount(() => { observer?.disconnect(); controller.abort(); clearTimeout(refresh); cover.value = '' })
</script>
<template><article ref="root" class="public-video-card"><RouterLink class="public-cover" :to="`/videos/${video.id}`" :aria-label="`观看：${video.title}`"><img v-if="cover" :src="cover" alt="" loading="lazy" referrerpolicy="no-referrer" @error="cover = ''; coverError = true" /><span v-else class="cover-unavailable">{{ coverError || !video.hasCover ? '封面暂不可用' : '正在加载封面' }}</span><span class="cover-play" aria-hidden="true">▶</span><span class="video-duration">{{ durationLabel(video.durationMillis) }}</span></RouterLink><p class="public-category">{{ video.categoryName }}</p><h2><RouterLink :to="`/videos/${video.id}`">{{ video.title }}</RouterLink></h2><p class="public-author"><RouterLink :to="`/authors/${video.authorId}`">{{ video.authorName }}</RouterLink> · {{ video.viewCount }} 次播放 · {{ video.favoriteCount }} 收藏 · {{ video.likeCount }} 赞</p></article></template>
