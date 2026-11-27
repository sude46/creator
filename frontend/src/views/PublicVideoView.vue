<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import Hls from 'hls.js'
import { useRoute } from 'vue-router'
import WorkspaceShell from '../components/WorkspaceShell.vue'
import PublicVideoCard from '../components/PublicVideoCard.vue'
import { publicApi } from '../api/public'
import type { PublicVideo } from '../api/public'
import { cachedCover } from '../api/coverCache'
import type { SignedMedia, SubtitleTrack } from '../api/creator'
import { formatTime } from '../api/creator'
import { useStudioError } from '../composables/useStudioError'
import { auth } from '../stores/auth'
import type { Comment } from '../api/public'
type Playback = { url: string; expiresAt?: string; hls?: boolean }
const id = String(useRoute().params.id); const explain = useStudioError(); const video = ref<PublicVideo>(); const media = ref<Playback>(); const player = ref<HTMLVideoElement>(); const cover = ref(''); const playing = ref(false); const buffering = ref(false); const hasFrame = ref(false); const loading = ref(false); const signing = ref(false); const favoriteBusy = ref(false); const favorited = ref<boolean>(); const liked = ref<boolean>(); const likeBusy = ref(false); const error = ref(''); const mediaError = ref(''); const playbackNotice = ref(''); const favoriteError = ref(''); const notice = ref(''); const related = ref<PublicVideo[]>([]); const comments = ref<Comment[]>([]); const replies = ref<Record<string, Comment[]>>({}); const repliesOpen = ref<Record<string, boolean>>({}); const replyForm = ref<Record<string, boolean>>({}); const replyText = ref<Record<string, string>>({}); const replyBusy = ref<string>(); const commentLikeBusy = ref<string>(); const commentPage = ref(1); const commentSort = ref<'latest' | 'oldest' | 'hot'>('latest'); const commentTotal = ref(0); const commentLoading = ref(false); const commentBusy = ref(false); const commentText = ref(''); const videoTags = ref<string[]>([]); const subtitleTracks = ref<SubtitleTrack[]>([]); const subtitleChoice = ref('off'); const SUBTITLE_KEY = 'frame-subtitle-choice'; const relatedLoading = ref(false); const relatedError = ref(''); let hlsPlayer: Hls | undefined
const portraitVideo = computed(() => {
  const width = Number(video.value?.width)
  const height = Number(video.value?.height)
  return width > 0 && height > 0 && height > width
})
const watchStyle = computed(() => {
  const width = Number(video.value?.width)
  const height = Number(video.value?.height)
  if (!(width > 0) || !(height > 0)) return undefined
  const style: Record<string, string> = { aspectRatio: `${width} / ${height}` }
  if (height > width) style.width = `min(100%, calc(min(72vh, 720px) * ${(width / height).toFixed(6)}))`
  return style
})
let activeRequest: AbortController | undefined; let mediaRequest: AbortController | undefined; let relatedRequest: AbortController | undefined; let commentRequest: AbortController | undefined; let coverRequest: AbortController | undefined; const lifetime = new AbortController()
function clearMedia() { hlsPlayer?.destroy(); hlsPlayer = undefined; playing.value = false; buffering.value = false; hasFrame.value = false; player.value?.pause(); player.value?.removeAttribute('src'); player.value?.load(); media.value = undefined }
function playerError() { playing.value = false; buffering.value = false; if (!media.value?.hls) mediaError.value = '视频无法播放：源文件可能不受当前浏览器支持，或播放地址已过期。请重新加载或更换浏览器。' }
function startPlayback(element: HTMLVideoElement | null | undefined) {
  if (!element) return
  element.play().catch((failure: unknown) => {
    if (element.muted || (failure as DOMException)?.name !== 'NotAllowedError') return
    element.muted = true
    void element.play().then(() => {
      playbackNotice.value = '已静音播放，可在播放器音量键中打开声音。'
    }).catch(() => {
      // Stay quiet: the player shows a play control the viewer can tap.
      playbackNotice.value = ''
    })
  })
}
function togglePlayer() {
  const element = player.value
  if (!element) { void play(); return }
  if (!element.paused) { element.pause(); return }
  element.play().catch(() => {
    element.muted = true
    void element.play().catch(() => {})
  })
}
function onPlaying() { playing.value = true; buffering.value = false; hasFrame.value = true; if (media.value && !media.value.hls) mediaError.value = '' }
function onPause() { playing.value = false; buffering.value = false }
function onWaiting() { buffering.value = true }
function onVolumeChange() { if (player.value && !player.value.muted) playbackNotice.value = '' }
function attachHls(fallback: SignedMedia) {
  const element = player.value
  const url = media.value?.url
  if (!element || !url) return
  hlsPlayer?.destroy()
  // Chrome reports "maybe" for HLS but cannot demux MPEG-TS natively; prefer hls.js whenever available.
  if (Hls.isSupported()) {
    const instance = new Hls({ maxBufferLength: 30 })
    hlsPlayer = instance
    instance.on(Hls.Events.ERROR, (_event, data) => {
      if (!data.fatal) return
      instance.destroy()
      if (hlsPlayer === instance) hlsPlayer = undefined
      media.value = fallback
      mediaError.value = 'HLS 播放失败，已切换为源文件。'
      void nextTick().then(() => startPlayback(player.value))
    })
    instance.loadSource(url)
    instance.attachMedia(element)
    startPlayback(element)
    return
  }
  if (element.canPlayType('application/vnd.apple.mpegurl')) {
    element.src = url
    startPlayback(element)
    return
  }
  media.value = fallback
  mediaError.value = '当前浏览器不支持 HLS 播放，已尝试直接播放源文件。建议使用最新版 Chrome、Edge、Firefox 或 Safari。'
}
async function loadCover() {
  if (!video.value?.hasCover) return
  coverRequest?.abort()
  const controller = new AbortController()
  coverRequest = controller
  try {
    const entry = await cachedCover(video.value.coverKey ?? `public:${id}`, () => publicApi.media(id, 'COVER', controller.signal))
    if (!controller.signal.aborted) cover.value = entry.url
  } catch { /* the stage keeps its dark background */ }
}
async function load() { activeRequest?.abort(); mediaRequest?.abort(); relatedRequest?.abort(); signing.value = false; clearMedia(); cover.value = ''; related.value = []; relatedError.value = ''; const controller = new AbortController(); activeRequest = controller; loading.value = true; error.value = ''; video.value = undefined; try { const result = await publicApi.get(id, controller.signal); if (!controller.signal.aborted) { video.value = result; void loadCover(); void loadRelated(result); void loadTags(); void loadSubtitles(); void play() } } catch (failure) { if (!controller.signal.aborted) error.value = explain(failure) } finally { if (!controller.signal.aborted) loading.value = false } }
async function loadComments(next = commentPage.value) {
  commentRequest?.abort()
  const c = new AbortController()
  commentRequest = c
  commentLoading.value = true
  try {
    const result = await publicApi.comments(id, next, commentSort.value, c.signal)
    if (!lifetime.signal.aborted && !c.signal.aborted) {
      comments.value = result.items
      replies.value = {}
      repliesOpen.value = {}
      replyForm.value = {}
      commentPage.value = result.page
      commentTotal.value = result.total
    }
  } catch { /* comments are optional */ }
  finally { if (!lifetime.signal.aborted && !c.signal.aborted) commentLoading.value = false }
}
function setCommentSort(sort: 'latest' | 'oldest' | 'hot') {
  if (commentSort.value === sort) return
  commentSort.value = sort
  void loadComments(1)
}
async function loadReplies(comment: Comment) {
  try {
    const result = await publicApi.replies(comment.id, lifetime.signal)
    if (lifetime.signal.aborted) return
    replies.value = { ...replies.value, [comment.id]: result.items }
    comments.value = comments.value.map(item => item.id === comment.id ? { ...item, replyCount: result.total } : item)
  } catch { /* replies are optional */ }
}
async function toggleReplies(comment: Comment) {
  if (repliesOpen.value[comment.id]) { repliesOpen.value = { ...repliesOpen.value, [comment.id]: false }; return }
  repliesOpen.value = { ...repliesOpen.value, [comment.id]: true }
  if (!replies.value[comment.id]) await loadReplies(comment)
}
function toggleReplyForm(comment: Comment) {
  replyForm.value = { ...replyForm.value, [comment.id]: !replyForm.value[comment.id] }
}
async function addReply(comment: Comment) {
  const content = (replyText.value[comment.id] ?? '').trim()
  if (!content || replyBusy.value) return
  replyBusy.value = comment.id
  try {
    await publicApi.addComment(id, content, comment.id)
    if (lifetime.signal.aborted) return
    replyText.value = { ...replyText.value, [comment.id]: '' }
    replyForm.value = { ...replyForm.value, [comment.id]: false }
    await loadComments(commentPage.value)
    await loadReplies(comment)
    repliesOpen.value = { ...repliesOpen.value, [comment.id]: true }
    notice.value = '回复已发表。'
  } catch (failure) { if (!lifetime.signal.aborted) mediaError.value = explain(failure) }
  finally { if (!lifetime.signal.aborted) replyBusy.value = undefined }
}
async function toggleCommentLike(comment: Comment) {
  if (commentLikeBusy.value) return
  commentLikeBusy.value = comment.id
  const add = !comment.liked
  try {
    await publicApi.likeComment(comment.id, add)
    if (lifetime.signal.aborted) return
    const update = (item: Comment) => item.id === comment.id
      ? { ...item, liked: add, likeCount: Math.max(0, item.likeCount + (add ? 1 : -1)) }
      : item
    comments.value = comments.value.map(update)
    const nextReplies: Record<string, Comment[]> = {}
    for (const [key, list] of Object.entries(replies.value)) nextReplies[key] = list.map(update)
    replies.value = nextReplies
  } catch (failure) { if (!lifetime.signal.aborted) mediaError.value = explain(failure) }
  finally { if (!lifetime.signal.aborted) commentLikeBusy.value = undefined }
}
async function reportComment(comment: Comment) {
  const reason = window.prompt('举报这条评论的原因（1～500 字）')?.trim() ?? ''
  if (!reason) return
  try {
    await publicApi.report('COMMENT', comment.id, reason)
    if (!lifetime.signal.aborted) notice.value = '评论举报已提交。'
  } catch (failure) { if (!lifetime.signal.aborted) mediaError.value = explain(failure) }
}
async function addComment() { const content = commentText.value.trim(); if (!content || commentBusy.value) return; commentBusy.value = true; try { await publicApi.addComment(id, content); if (!lifetime.signal.aborted) { commentText.value = ''; await loadComments(1) } } catch (failure) { if (!lifetime.signal.aborted) mediaError.value = explain(failure) } finally { if (!lifetime.signal.aborted) commentBusy.value = false } }
async function removeComment(comment: Comment) { if (!window.confirm('删除这条评论？回复也会一并隐藏。')) return; try { await publicApi.deleteComment(comment.id); if (!lifetime.signal.aborted) await loadComments() } catch (failure) { if (!lifetime.signal.aborted) mediaError.value = explain(failure) } }
function canDeleteComment(comment: Comment) { return auth.user.value?.role === 'ADMIN' || auth.user.value?.id === comment.userId }
async function loadSubtitles() {
  try {
    const result = await publicApi.subtitles(id, lifetime.signal)
    if (lifetime.signal.aborted) return
    subtitleTracks.value = result.items
    let stored: string | null = null
    try { stored = localStorage.getItem(SUBTITLE_KEY) } catch { /* storage can be unavailable */ }
    if (stored === 'off') subtitleChoice.value = 'off'
    else if (stored && result.items.some(track => track.language === stored)) subtitleChoice.value = stored
    else subtitleChoice.value = result.items.find(track => track.language === 'zh-CN')?.language ?? result.items[0]?.language ?? 'off'
    await nextTick()
    applySubtitlePreference()
  } catch { subtitleTracks.value = []; subtitleChoice.value = 'off' }
}
function applySubtitlePreference() {
  const element = player.value
  if (!element) return
  for (let index = 0; index < element.textTracks.length; index++) {
    const track = element.textTracks[index]
    track.mode = subtitleChoice.value !== 'off' && track.language === subtitleChoice.value ? 'showing' : 'disabled'
  }
}
async function changeSubtitle() {
  try { localStorage.setItem(SUBTITLE_KEY, subtitleChoice.value) } catch { /* storage can be unavailable */ }
  await nextTick()
  applySubtitlePreference()
}
async function loadTags() { try { const result = await publicApi.videoTags(id, lifetime.signal); if (!lifetime.signal.aborted) videoTags.value = result.tags } catch { /* tags are optional */ } }
async function loadRelated(current: PublicVideo) { relatedRequest?.abort(); const controller = new AbortController(); relatedRequest = controller; relatedLoading.value = true; relatedError.value = ''; try { const result = await publicApi.list('', current.categoryId, '', '', 'latest', 1, controller.signal); if (!controller.signal.aborted) related.value = result.items.filter(item => item.id !== current.id).slice(0, 3) } catch (failure) { if (!controller.signal.aborted) relatedError.value = explain(failure) } finally { if (!controller.signal.aborted) relatedLoading.value = false } }
async function recordView() {
  if (sessionStorage.getItem(`frame-viewed-${id}`)) return
  sessionStorage.setItem(`frame-viewed-${id}`, '1')
  try {
    const result = await publicApi.view(id)
    if (!lifetime.signal.aborted && video.value) video.value = { ...video.value, viewCount: result.viewCount }
  } catch { /* views are best-effort */ }
}
async function play() { if (!video.value || signing.value) return; mediaRequest?.abort(); const controller = new AbortController(); mediaRequest = controller; signing.value = true; mediaError.value = ''; playbackNotice.value = ''; clearMedia(); void recordView(); try { const result = await publicApi.media(id, 'SOURCE', controller.signal); if (controller.signal.aborted) return; if (!video.value?.hasHls) { media.value = result; await nextTick(); applySubtitlePreference(); startPlayback(player.value); return } media.value = { url: `/api/videos/${id}/hls/master.m3u8`, hls: true }; await nextTick(); if (!controller.signal.aborted) { attachHls(result); applySubtitlePreference() } } catch (failure) { if (!controller.signal.aborted) mediaError.value = explain(failure) } finally { if (!controller.signal.aborted) signing.value = false } }
async function reportVideo() { const reason = window.prompt('举报原因（1～500 字）')?.trim() ?? ''; if (!reason) return; try { await publicApi.report('VIDEO', id, reason); mediaError.value = ''; notice.value = '举报已提交。' } catch (failure) { if (!lifetime.signal.aborted) mediaError.value = explain(failure) } }
async function likeState() { try { const result = await publicApi.likeState(id, lifetime.signal); if (!lifetime.signal.aborted) liked.value = result.liked } catch { if (!lifetime.signal.aborted) liked.value = undefined } }
async function toggleLike() { if (liked.value === undefined || likeBusy.value) return; const add = !liked.value; likeBusy.value = true; try { await publicApi.like(id, add); if (!lifetime.signal.aborted) { liked.value = add; if (video.value) video.value = { ...video.value, likeCount: Math.max(0, video.value.likeCount + (add ? 1 : -1)) } } } catch { /* best effort */ } finally { if (!lifetime.signal.aborted) likeBusy.value = false } }
async function favoriteState() { favoriteBusy.value = true; favoriteError.value = ''; try { const result = await publicApi.favoriteState(id, lifetime.signal); if (!lifetime.signal.aborted) favorited.value = result.favorited } catch (failure) { if (!lifetime.signal.aborted) { favorited.value = undefined; favoriteError.value = explain(failure) } } finally { if (!lifetime.signal.aborted) favoriteBusy.value = false } }
async function toggleFavorite() { if (favoriteBusy.value || favorited.value === undefined) return; const add = !favorited.value; favoriteBusy.value = true; favoriteError.value = ''; notice.value = ''; try { await publicApi.favorite(id, add); if (!lifetime.signal.aborted) { favorited.value = add; if (video.value) video.value = { ...video.value, favoriteCount: Math.max(0, video.value.favoriteCount + (add ? 1 : -1)) }; notice.value = add ? '已加入收藏。' : '已取消收藏。' } } catch (failure) { if (!lifetime.signal.aborted) { favoriteError.value = `${explain(failure)}。正在核对收藏状态。`; favorited.value = undefined; await favoriteState() } } finally { if (!lifetime.signal.aborted) favoriteBusy.value = false } }
onMounted(() => { if (/^[1-9]\d{0,18}$/.test(id)) { void load(); if (auth.user.value) { void favoriteState(); void likeState() } void loadComments() } else error.value = '作品编号无效' }); onBeforeUnmount(() => { lifetime.abort(); activeRequest?.abort(); mediaRequest?.abort(); relatedRequest?.abort(); commentRequest?.abort(); coverRequest?.abort(); clearMedia() })
</script>
<template>
  <WorkspaceShell>
    <RouterLink class="back-link" to="/">← 发现作品</RouterLink>
    <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" />
    <p v-if="loading" class="empty-state" role="status">正在读取作品…</p>
    <el-button v-if="error" @click="load">重新读取</el-button>
    <template v-if="video">
      <div class="watch-top" :class="{ portrait: portraitVideo }">
        <div class="watch-stage">
          <section class="watch-screen" :class="{ portrait: portraitVideo }" :style="watchStyle" aria-label="视频播放器">
            <img v-if="cover && !hasFrame" class="watch-cover" :src="cover" alt="" aria-hidden="true" referrerpolicy="no-referrer" />
            <div v-if="cover && !hasFrame" class="watch-cover-scrim" aria-hidden="true"></div>
            <video v-if="media" ref="player" :src="media.hls ? undefined : media.url" controls playsinline webkit-playsinline x5-playsinline x5-video-player-type="h5" x5-video-player-fullscreen="false" preload="auto" @loadedmetadata="applySubtitlePreference" @loadeddata="hasFrame = true" @error="playerError" @volumechange="onVolumeChange" @playing="onPlaying" @pause="onPause" @waiting="onWaiting">
              <track v-for="track in subtitleTracks" :key="track.language" kind="subtitles" :srclang="track.language" :label="track.label" :src="`/api/videos/${id}/subtitles/${track.language}.vtt`" />
            </video>
            <div v-if="!media || !playing" class="watch-center">
              <button v-if="signing || buffering" class="watch-play-icon" type="button" disabled aria-label="正在加载视频"><span class="watch-spinner" aria-hidden="true"></span></button>
              <template v-else>
                <button class="watch-play-icon" type="button" :aria-label="media ? '播放视频' : '重新加载视频'" @click="media ? togglePlayer() : play()">
                  <svg viewBox="0 0 24 24" width="24" height="24" aria-hidden="true"><path d="M8 5.4v13.2L19 12Z" fill="currentColor" /></svg>
                </button>
                <span v-if="mediaError && !media" class="watch-center-label">重新加载</span>
              </template>
            </div>
          </section>
          <el-alert v-if="playbackNotice" :title="playbackNotice" type="info" :closable="true" role="status" @close="playbackNotice = ''" />
          <el-alert v-if="mediaError" :title="mediaError" type="error" :closable="false" role="alert" />
        </div>
        <div class="watch-info">
          <div class="watch-toolbar">
            <span>分类：{{ video.categoryName }} · 作者：{{ video.authorName }}</span>
            <label v-if="subtitleTracks.length" class="subtitle-select">字幕<select v-model="subtitleChoice" @change="changeSubtitle"><option value="off">关闭</option><option v-for="track in subtitleTracks" :key="track.language" :value="track.language">{{ track.label }}</option></select></label>
            <el-button :loading="signing" @click="play">重新加载</el-button>
          </div>
          <div class="watch-heading">
            <div>
              <h1>{{ video.title }}</h1>
              <p class="muted"><RouterLink :to="`/authors/${video.authorId}`">{{ video.authorName }}</RouterLink> · {{ video.viewCount }} 次播放 · {{ video.favoriteCount }} 收藏 · {{ video.likeCount }} 赞 · 发布于 {{ formatTime(video.publishedAt) }}</p>
            </div>
            <div class="inline-actions">
              <template v-if="auth.user.value">
                <el-button :loading="likeBusy" :disabled="liked === undefined" @click="toggleLike">{{ liked ? '已赞' : '点赞' }}</el-button>
                <el-button :loading="favoriteBusy" :disabled="favorited === undefined" @click="toggleFavorite">{{ favorited ? '取消收藏' : '收藏作品' }}</el-button>
                <el-button @click="reportVideo">举报</el-button>
              </template>
              <RouterLink v-else to="/login">登录后可点赞、收藏 →</RouterLink>
            </div>
          </div>
          <el-alert v-if="favoriteError" :title="favoriteError" type="error" :closable="false" role="alert" />
          <el-button v-if="auth.user.value && favorited === undefined" :disabled="favoriteBusy" @click="favoriteState">查询收藏状态</el-button>
          <p v-if="notice" role="status" class="muted">{{ notice }}</p>
          <div v-if="videoTags.length" class="watch-tags">
            <RouterLink v-for="item in videoTags" :key="item" :to="{ path: '/', query: { tag: item } }">{{ item }}</RouterLink>
          </div>
          <p class="video-description">{{ video.description || '作者未填写简介。' }}</p>
          <p v-if="media?.expiresAt" class="muted">播放地址到期：{{ formatTime(media.expiresAt) }}（北京时间）。过期后可重新获取。</p>
        </div>
      </div>
      <section class="studio-panel comment-section">
        <div class="section-heading"><h2>评论 <span class="count">{{ commentTotal }}</span></h2><el-button :loading="commentLoading" @click="loadComments()">刷新</el-button></div>
        <div v-if="auth.user.value" class="comment-form"><textarea v-model="commentText" maxlength="500" rows="3" placeholder="说点什么… 使用 @用户名 提醒对方" :disabled="commentBusy"/><el-button type="primary" :loading="commentBusy" :disabled="!commentText.trim()" @click="addComment">发表评论</el-button></div>
        <p v-else class="muted"><RouterLink to="/login">登录</RouterLink>后参与评论。</p>
        <div class="comment-sort" role="group" aria-label="评论排序"><span>排序</span><button type="button" :class="{ active: commentSort === 'latest' }" @click="setCommentSort('latest')">最新</button><button type="button" :class="{ active: commentSort === 'oldest' }" @click="setCommentSort('oldest')">最早</button><button type="button" :class="{ active: commentSort === 'hot' }" @click="setCommentSort('hot')">最热</button></div>
        <article v-for="comment in comments" :key="comment.id" class="history-item comment-item">
          <div class="comment-head"><strong>@{{ comment.username }}</strong><time>{{ formatTime(comment.createdAt) }}</time></div>
          <p class="comment-content">{{ comment.content }}</p>
          <div class="comment-actions">
            <button v-if="auth.user.value" type="button" :aria-pressed="comment.liked" :disabled="commentLikeBusy === comment.id" @click="toggleCommentLike(comment)">{{ comment.liked ? '♥ 已赞' : '♡ 赞' }} <span>{{ comment.likeCount }}</span></button>
            <button v-if="auth.user.value" type="button" @click="toggleReplyForm(comment)">回复</button>
            <button v-if="comment.replyCount" type="button" @click="toggleReplies(comment)">{{ repliesOpen[comment.id] ? '收起回复' : `查看 ${comment.replyCount} 条回复` }}</button>
            <button v-if="auth.user.value" type="button" @click="reportComment(comment)">举报</button>
            <el-button v-if="canDeleteComment(comment)" type="danger" size="small" @click="removeComment(comment)">删除</el-button>
          </div>
          <form v-if="replyForm[comment.id]" class="reply-form" @submit.prevent="addReply(comment)">
            <label class="sr-only" :for="`reply-${comment.id}`">回复 @{{ comment.username }}</label>
            <textarea :id="`reply-${comment.id}`" v-model="replyText[comment.id]" maxlength="500" rows="2" :placeholder="`回复 @${comment.username}，也可用 @用户名 提醒`" :disabled="replyBusy === comment.id" />
            <div class="inline-actions"><el-button type="primary" size="small" native-type="submit" :loading="replyBusy === comment.id" :disabled="!(replyText[comment.id] ?? '').trim()">发表回复</el-button><el-button size="small" @click="toggleReplyForm(comment)">取消</el-button></div>
          </form>
          <div v-if="repliesOpen[comment.id]" class="comment-replies">
            <article v-for="reply in replies[comment.id]" :key="reply.id" class="comment-reply">
              <div class="comment-head"><strong>@{{ reply.username }}</strong><time>{{ formatTime(reply.createdAt) }}</time></div>
              <p class="comment-content">{{ reply.content }}</p>
              <div class="comment-actions">
                <button v-if="auth.user.value" type="button" :aria-pressed="reply.liked" :disabled="commentLikeBusy === reply.id" @click="toggleCommentLike(reply)">{{ reply.liked ? '♥ 已赞' : '♡ 赞' }} <span>{{ reply.likeCount }}</span></button>
                <button v-if="auth.user.value" type="button" @click="reportComment(reply)">举报</button>
                <el-button v-if="canDeleteComment(reply)" type="danger" size="small" @click="removeComment(reply)">删除</el-button>
              </div>
            </article>
            <p v-if="!replies[comment.id]" class="muted">正在加载回复…</p>
          </div>
        </article>
        <p v-if="!commentLoading && !comments.length" class="muted">还没有评论。</p>
        <div v-if="commentTotal > 10" class="page-controls"><el-button :disabled="commentLoading || commentPage <= 1" @click="loadComments(commentPage - 1)">上一页评论</el-button><span>第 {{ commentPage }} 页</span><el-button :disabled="commentLoading || commentPage * 10 >= commentTotal || commentPage >= 1000" @click="loadComments(commentPage + 1)">下一页评论</el-button></div>
      </section>
      <section v-if="relatedLoading || related.length || relatedError" class="studio-panel related-section">
        <div class="related-heading"><h2>同类作品</h2><RouterLink :to="{ path: '/', query: { category: video.categoryId } }">查看全部 →</RouterLink></div>
        <p v-if="relatedLoading" class="muted" role="status">正在读取同类作品…</p>
        <p v-else-if="relatedError" class="muted">同类作品暂时无法加载。</p>
        <div v-else class="public-video-grid related-grid"><PublicVideoCard v-for="item in related" :key="item.id" :video="item" /></div>
      </section>
    </template>
  </WorkspaceShell>
</template>

<style scoped>
.subtitle-select { display: inline-flex; align-items: center; gap: 6px; font-size: 12px; color: var(--color-muted-text); }
.subtitle-select select { border: 1px solid var(--color-control-border); border-radius: 6px; background: var(--color-surface); color: var(--color-text); padding: 4px 8px; font: inherit; font-size: 12px; }

.comment-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.comment-head time { font-size: 12px; color: var(--color-muted-text); }
.comment-content { margin: 8px 0 10px; white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.8; }
.comment-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; }
.comment-actions button { border: 0; background: none; padding: 4px 0; color: var(--color-muted-text); font: inherit; font-size: 12px; cursor: pointer; }
.comment-actions button:hover { color: var(--color-primary); }
.comment-actions button[aria-pressed="true"] { color: var(--color-accent-text); }
.comment-actions button:disabled { cursor: wait; opacity: .6; }
.comment-replies { margin-top: 12px; padding-left: 16px; border-left: 2px solid var(--color-border-soft); display: grid; gap: 12px; }
.comment-reply { padding: 10px 0; border-top: 1px solid var(--color-border-soft); }
.comment-reply:first-child { border-top: 0; padding-top: 0; }
.reply-form { display: grid; gap: 8px; margin-top: 12px; }
.reply-form textarea { width: 100%; resize: vertical; border: 1px solid var(--color-control-border); border-radius: 3px; padding: 9px 11px; color: var(--color-text-strong); font: inherit; background: var(--color-surface); }
.reply-form textarea:focus { outline: 2px solid var(--color-focus); outline-offset: 1px; }
.comment-sort { display: flex; align-items: center; gap: 8px; margin: 0 0 12px; font-size: 12px; color: var(--color-muted-text); }
.comment-sort button { border: 1px solid var(--color-control-border); border-radius: 999px; background: var(--color-surface); color: var(--color-secondary); padding: 4px 12px; font: inherit; font-size: 12px; cursor: pointer; }
.comment-sort button.active { background: var(--color-primary); border-color: var(--color-primary); color: var(--color-surface); }
</style>
