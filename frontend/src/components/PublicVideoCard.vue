<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { publicApi, durationLabel } from '../api/public'
import type { PublicVideo } from '../api/public'
const props = defineProps<{ video: PublicVideo }>(); const root = ref<HTMLElement>(); const cover = ref(''); const controller = new AbortController(); let observer: IntersectionObserver | undefined
async function loadCover() { try { const signed = await publicApi.media(props.video.id, 'COVER', controller.signal); if (!controller.signal.aborted) cover.value = signed.url } catch { cover.value = '' } }
onMounted(() => { observer = new IntersectionObserver(entries => { if (entries.some(entry => entry.isIntersecting)) { observer?.disconnect(); void loadCover() } }); if (root.value) observer.observe(root.value) })
onBeforeUnmount(() => { observer?.disconnect(); controller.abort(); cover.value = '' })
</script>
<template><article ref="root" class="public-video-card"><RouterLink class="public-cover" :to="`/videos/${video.id}`" :aria-label="`观看：${video.title}`"><img v-if="cover" :src="cover" alt="" loading="lazy" referrerpolicy="no-referrer" @error="cover = ''" /><span v-else class="cover-unavailable">封面暂不可用</span><span class="video-duration">{{ durationLabel(video.durationMillis) }}</span></RouterLink><p class="public-category">{{ video.categoryName }}</p><h2><RouterLink :to="`/videos/${video.id}`">{{ video.title }}</RouterLink></h2><p class="public-author">{{ video.authorName }}</p></article></template>
