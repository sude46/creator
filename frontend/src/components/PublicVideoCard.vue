<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { publicApi, durationLabel } from '../api/public'
import type { PublicVideo } from '../api/public'
import { cachedCover } from '../api/coverCache'
const props = defineProps<{ video: PublicVideo; featured?: boolean }>(); const cover = ref(''); const coverError = ref(false); const signing = ref(false); const controller = new AbortController(); let refresh: ReturnType<typeof setTimeout> | undefined; let retry: ReturnType<typeof setTimeout> | undefined; let attempts = 0
async function loadCover(force = false) {
  if (!props.video.hasCover || controller.signal.aborted) return
  if (signing.value) return
  signing.value = true
  clearTimeout(retry)
  try {
    const entry = await cachedCover(props.video.coverKey ?? `public:${props.video.id}`, () => publicApi.media(props.video.id, 'COVER', controller.signal), force)
    if (controller.signal.aborted) return
    coverError.value = false
    attempts = 0
    cover.value = entry.url
    clearTimeout(refresh)
    refresh = setTimeout(() => void loadCover(true), Math.max(30_000, entry.expiresAt - Date.now() - 30_000))
  } catch {
    if (controller.signal.aborted) return
    cover.value = ''
    if (attempts < 1) { attempts += 1; retry = setTimeout(() => void loadCover(true), 1200) }
    else coverError.value = true
  } finally { if (!controller.signal.aborted) signing.value = false }
}
function coverFailed() {
  if (controller.signal.aborted) return
  cover.value = ''
  if (attempts < 1) { attempts += 1; retry = setTimeout(() => void loadCover(true), 800) }
  else coverError.value = true
}
onMounted(() => void loadCover())
onBeforeUnmount(() => { controller.abort(); clearTimeout(refresh); clearTimeout(retry); cover.value = '' })
</script>
<template>
  <article class="public-video-card" :class="{ featured }">
    <RouterLink class="public-cover" :to="`/videos/${video.id}`" :aria-label="`观看：${video.title}`">
      <img v-if="cover" :src="cover" alt="" decoding="async" loading="lazy" referrerpolicy="no-referrer" @error="coverFailed" />
      <span v-else class="cover-unavailable">{{ coverError || !video.hasCover ? '封面暂不可用' : '正在加载封面' }}</span>
      <span class="cover-play" aria-hidden="true"><svg viewBox="0 0 20 20" width="14" height="14" aria-hidden="true"><path d="M7.2 5.4v9.2L15 10Z" fill="currentColor" /></svg></span>
      <span class="video-duration">{{ durationLabel(video.durationMillis) }}</span>
    </RouterLink>
    <div class="public-card-copy">
      <p class="public-category">{{ video.categoryName }}</p>
      <h2><RouterLink :to="`/videos/${video.id}`">{{ video.title }}</RouterLink></h2>
      <p v-if="featured && video.description" class="public-description">{{ video.description }}</p>
      <p class="public-author"><RouterLink :to="`/authors/${video.authorId}`">{{ video.authorName }}</RouterLink> · {{ video.viewCount }} 次播放 · {{ video.favoriteCount }} 收藏 · {{ video.likeCount }} 赞</p>
      <RouterLink v-if="featured" class="featured-link" :to="`/videos/${video.id}`">观看作品 →</RouterLink>
    </div>
  </article>
</template>
