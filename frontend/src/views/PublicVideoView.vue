<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import WorkspaceShell from '../components/WorkspaceShell.vue'
import PublicVideoCard from '../components/PublicVideoCard.vue'
import { publicApi, durationLabel } from '../api/public'
import type { PublicVideo } from '../api/public'
import type { SignedMedia } from '../api/creator'
import { formatTime } from '../api/creator'
import { useStudioError } from '../composables/useStudioError'
import { auth } from '../stores/auth'
import type { Comment } from '../api/public'
const id = String(useRoute().params.id); const explain = useStudioError(); const video = ref<PublicVideo>(); const media = ref<SignedMedia>(); const player = ref<HTMLVideoElement>(); const loading = ref(false); const signing = ref(false); const favoriteBusy = ref(false); const favorited = ref<boolean>(); const liked = ref<boolean>(); const likeBusy = ref(false); const error = ref(''); const mediaError = ref(''); const favoriteError = ref(''); const notice = ref(''); const related = ref<PublicVideo[]>([]); const comments = ref<Comment[]>([]); const replies = ref<Record<string, Comment[]>>({}); const repliesOpen = ref<Record<string, boolean>>({}); const replyForm = ref<Record<string, boolean>>({}); const replyText = ref<Record<string, string>>({}); const replyBusy = ref<string>(); const commentLikeBusy = ref<string>(); const commentPage = ref(1); const commentTotal = ref(0); const commentLoading = ref(false); const commentBusy = ref(false); const commentText = ref(''); const videoTags = ref<string[]>([]); const relatedLoading = ref(false); const relatedError = ref(''); let activeRequest: AbortController | undefined; let mediaRequest: AbortController | undefined; let relatedRequest: AbortController | undefined; let commentRequest: AbortController | undefined; const lifetime = new AbortController()
function clearMedia() { player.value?.pause(); player.value?.removeAttribute('src'); player.value?.load(); media.value = undefined }
async function load() { activeRequest?.abort(); mediaRequest?.abort(); relatedRequest?.abort(); signing.value = false; clearMedia(); related.value = []; relatedError.value = ''; const controller = new AbortController(); activeRequest = controller; loading.value = true; error.value = ''; video.value = undefined; try { const result = await publicApi.get(id, controller.signal); if (!controller.signal.aborted) { video.value = result; void loadRelated(result); void loadTags() } } catch (failure) { if (!controller.signal.aborted) error.value = explain(failure) } finally { if (!controller.signal.aborted) loading.value = false } }
async function loadComments(next = commentPage.value) {
  commentRequest?.abort()
  const c = new AbortController()
  commentRequest = c
  commentLoading.value = true
  try {
    const result = await publicApi.comments(id, next, c.signal)
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
async function play() { if (!video.value || signing.value || loading.value) return; mediaRequest?.abort(); const controller = new AbortController(); mediaRequest = controller; signing.value = true; mediaError.value = ''; clearMedia(); void recordView(); try { const result = await publicApi.media(id, 'SOURCE', controller.signal); if (!controller.signal.aborted) media.value = result } catch (failure) { if (!controller.signal.aborted) mediaError.value = explain(failure) } finally { if (!controller.signal.aborted) signing.value = false } }
async function reportVideo() { const reason = window.prompt('举报原因（1～500 字）')?.trim() ?? ''; if (!reason) return; try { await publicApi.report('VIDEO', id, reason); mediaError.value = ''; notice.value = '举报已提交。' } catch (failure) { if (!lifetime.signal.aborted) mediaError.value = explain(failure) } }
async function likeState() { try { const result = await publicApi.likeState(id, lifetime.signal); if (!lifetime.signal.aborted) liked.value = result.liked } catch { if (!lifetime.signal.aborted) liked.value = undefined } }
async function toggleLike() { if (liked.value === undefined || likeBusy.value) return; const add = !liked.value; likeBusy.value = true; try { await publicApi.like(id, add); if (!lifetime.signal.aborted) { liked.value = add; if (video.value) video.value = { ...video.value, likeCount: Math.max(0, video.value.likeCount + (add ? 1 : -1)) } } } catch { /* best effort */ } finally { if (!lifetime.signal.aborted) likeBusy.value = false } }
async function favoriteState() { favoriteBusy.value = true; favoriteError.value = ''; try { const result = await publicApi.favoriteState(id, lifetime.signal); if (!lifetime.signal.aborted) favorited.value = result.favorited } catch (failure) { if (!lifetime.signal.aborted) { favorited.value = undefined; favoriteError.value = explain(failure) } } finally { if (!lifetime.signal.aborted) favoriteBusy.value = false } }
async function toggleFavorite() { if (favoriteBusy.value || favorited.value === undefined) return; const add = !favorited.value; favoriteBusy.value = true; favoriteError.value = ''; notice.value = ''; try { await publicApi.favorite(id, add); if (!lifetime.signal.aborted) { favorited.value = add; if (video.value) video.value = { ...video.value, favoriteCount: Math.max(0, video.value.favoriteCount + (add ? 1 : -1)) }; notice.value = add ? '已加入收藏。' : '已取消收藏。' } } catch (failure) { if (!lifetime.signal.aborted) { favoriteError.value = `${explain(failure)}。正在核对收藏状态。`; favorited.value = undefined; await favoriteState() } } finally { if (!lifetime.signal.aborted) favoriteBusy.value = false } }
onMounted(() => { if (/^[1-9]\d{0,18}$/.test(id)) { void load(); void favoriteState(); void likeState(); void loadComments() } else error.value = '作品编号无效' }); onBeforeUnmount(() => { lifetime.abort(); activeRequest?.abort(); mediaRequest?.abort(); relatedRequest?.abort(); commentRequest?.abort(); clearMedia() })
</script>
<template><WorkspaceShell><RouterLink class="back-link" to="/">← 发现作品</RouterLink><el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" /><p v-if="loading" class="empty-state" role="status">正在读取作品…</p><el-button v-if="error" @click="load">重新读取</el-button><template v-if="video"><section class="watch-screen" aria-label="视频播放器"><video v-if="media" ref="player" :src="media.url" controls playsinline preload="metadata" @error="mediaError = '视频无法加载或链接已过期，请重新获取播放地址。'" /><div v-else class="watch-prompt"><span>{{ durationLabel(video.durationMillis) }}</span><p>准备观看《{{ video.title }}》</p><el-button :loading="signing" @click="play">加载视频</el-button></div></section><el-alert v-if="mediaError" :title="mediaError" type="error" :closable="false" role="alert" /><div class="watch-toolbar"><span>分类：{{ video.categoryName }} · 作者：{{ video.authorName }}</span><el-button :loading="signing" @click="play">重新获取播放地址</el-button></div><div class="watch-heading"><div><h1>{{ video.title }}</h1><p class="muted"><RouterLink :to="`/authors/${video.authorId}`">{{ video.authorName }}</RouterLink> · {{ video.viewCount }} 次播放 · {{ video.favoriteCount }} 收藏 · {{ video.likeCount }} 赞 · 发布于 {{ formatTime(video.publishedAt) }}</p></div><div class="inline-actions"><el-button :loading="likeBusy" :disabled="liked === undefined" @click="toggleLike">{{ liked ? '已赞' : '点赞' }}</el-button><el-button :loading="favoriteBusy" :disabled="favorited === undefined" @click="toggleFavorite">{{ favorited ? '取消收藏' : '收藏作品' }}</el-button><el-button @click="reportVideo">举报</el-button></div></div><el-alert v-if="favoriteError" :title="favoriteError" type="error" :closable="false" role="alert" /><el-button v-if="favorited === undefined" :disabled="favoriteBusy" @click="favoriteState">查询收藏状态</el-button><p v-if="notice" role="status" class="muted">{{ notice }}</p><div v-if="videoTags.length" class="watch-tags"><RouterLink v-for="item in videoTags" :key="item" :to="{ path: '/', query: { tag: item } }">{{ item }}</RouterLink></div><p class="video-description">{{ video.description || '作者未填写简介。' }}</p><p v-if="media" class="muted">播放地址到期：{{ formatTime(media.expiresAt) }}（北京时间）。过期后可重新获取。</p><section class="studio-panel comment-section"><div class="section-heading"><h2>评论 <span class="count">{{ commentTotal }}</span></h2><el-button :loading="commentLoading" @click="loadComments()">刷新</el-button></div><div class="comment-form"><textarea v-model="commentText" maxlength="500" rows="3" placeholder="说点什么…" :disabled="commentBusy"/><el-button type="primary" :loading="commentBusy" :disabled="!commentText.trim()" @click="addComment">发表评论</el-button></div><article v-for="comment in comments" :key="comment.id" class="history-item comment-item"><div class="comment-head"><strong>@{{ comment.username }}</strong><time>{{ formatTime(comment.createdAt) }}</time></div><p class="comment-content">{{ comment.content }}</p><div class="comment-actions"><button type="button" :aria-pressed="comment.liked" :disabled="commentLikeBusy === comment.id" @click="toggleCommentLike(comment)">{{ comment.liked ? '♥ 已赞' : '♡ 赞' }} <span>{{ comment.likeCount }}</span></button><button type="button" @click="toggleReplyForm(comment)">回复</button><button v-if="comment.replyCount" type="button" @click="toggleReplies(comment)">{{ repliesOpen[comment.id] ? '收起回复' : `查看 ${comment.replyCount} 条回复` }}</button><button type="button" @click="reportComment(comment)">举报</button><el-button v-if="canDeleteComment(comment)" type="danger" size="small" @click="removeComment(comment)">删除</el-button></div><form v-if="replyForm[comment.id]" class="reply-form" @submit.prevent="addReply(comment)"><label class="sr-only" :for="`reply-${comment.id}`">回复 @{{ comment.username }}</label><textarea :id="`reply-${comment.id}`" v-model="replyText[comment.id]" maxlength="500" rows="2" :placeholder="`回复 @${comment.username}`" :disabled="replyBusy === comment.id" /><div class="inline-actions"><el-button type="primary" size="small" native-type="submit" :loading="replyBusy === comment.id" :disabled="!(replyText[comment.id] ?? '').trim()">发表回复</el-button><el-button size="small" @click="toggleReplyForm(comment)">取消</el-button></div></form><div v-if="repliesOpen[comment.id]" class="comment-replies"><article v-for="reply in replies[comment.id]" :key="reply.id" class="comment-reply"><div class="comment-head"><strong>@{{ reply.username }}</strong><time>{{ formatTime(reply.createdAt) }}</time></div><p class="comment-content">{{ reply.content }}</p><div class="comment-actions"><button type="button" :aria-pressed="reply.liked" :disabled="commentLikeBusy === reply.id" @click="toggleCommentLike(reply)">{{ reply.liked ? '♥ 已赞' : '♡ 赞' }} <span>{{ reply.likeCount }}</span></button><button type="button" @click="reportComment(reply)">举报</button><el-button v-if="canDeleteComment(reply)" type="danger" size="small" @click="removeComment(reply)">删除</el-button></div></article><p v-if="!replies[comment.id]" class="muted">正在加载回复…</p></div></article><p v-if="!commentLoading && !comments.length" class="muted">还没有评论。</p><div v-if="commentTotal > 10" class="page-controls"><el-button :disabled="commentLoading || commentPage <= 1" @click="loadComments(commentPage - 1)">上一页评论</el-button><span>第 {{ commentPage }} 页</span><el-button :disabled="commentLoading || commentPage * 10 >= commentTotal || commentPage >= 1000" @click="loadComments(commentPage + 1)">下一页评论</el-button></div></section><section v-if="relatedLoading || related.length || relatedError" class="studio-panel related-section"><div class="related-heading"><h2>同类作品</h2><RouterLink :to="{ path: '/', query: { category: video.categoryId } }">查看全部 →</RouterLink></div><p v-if="relatedLoading" class="muted" role="status">正在读取同类作品…</p><p v-else-if="relatedError" class="muted">同类作品暂时无法加载。</p><div v-else class="public-video-grid related-grid"><PublicVideoCard v-for="item in related" :key="item.id" :video="item" /></div></section></template></WorkspaceShell></template>

<style scoped>
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
</style>
