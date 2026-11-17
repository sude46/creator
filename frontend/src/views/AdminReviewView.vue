<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import WorkspaceShell from '../components/WorkspaceShell.vue'
import { adminApi } from '../api/admin'
import type { CreatorVideo, ReviewRecord, SignedMedia } from '../api/creator'
import { formatTime, processingLabels, publicationLabels, reviewLabels } from '../api/creator'
import { useStudioError } from '../composables/useStudioError'

const explain = useStudioError()
const items = ref<{ ownerId: string; video: CreatorVideo }[]>([]); const total = ref(0); const page = ref(1)
const selected = ref<CreatorVideo>(); const history = ref<ReviewRecord[]>([]); const media = ref<SignedMedia>()
const reason = ref(''); const loading = ref(false); const busy = ref(false); const error = ref(''); const notice = ref('')
let controller: AbortController | undefined
const queueLabel = computed(() => total.value ? `${total.value} 个待审核作品` : '审核队列为空')
function select(video: CreatorVideo) { selected.value = video; reason.value = ''; media.value = undefined; void loadHistory(video.id) }
async function load(next = page.value) { controller?.abort(); controller = new AbortController(); loading.value = true; error.value = ''; try { const result = await adminApi.queue(next, controller.signal); items.value = result.items; total.value = result.total; page.value = next; if (!selected.value && result.items[0]) select(result.items[0].video) } catch (e) { if (!controller.signal.aborted) error.value = explain(e) } finally { if (!controller.signal.aborted) loading.value = false } }
async function loadHistory(id: string) { try { history.value = (await adminApi.history(id, 1)).items } catch (e) { error.value = explain(e) } }
async function decide(outcome: 'APPROVED' | 'REJECTED') { if (!selected.value || busy.value) return; if (outcome === 'REJECTED' && !reason.value.trim()) { error.value = '驳回必须填写原因'; return } if (!window.confirm(outcome === 'APPROVED' ? '确认通过这部作品？' : '确认驳回这部作品？')) return; busy.value = true; error.value = ''; try { await adminApi.decide(selected.value, outcome, reason.value.trim()); notice.value = outcome === 'APPROVED' ? '已通过审核，作品进入已发布状态。' : '已驳回，作者可修改后重新送审。'; selected.value = undefined; await load(1) } catch (e) { error.value = explain(e) } finally { busy.value = false } }
async function preview() { if (!selected.value || busy.value) return; try { media.value = await adminApi.preview(selected.value) } catch (e) { error.value = explain(e) } }
onMounted(() => void load()); onBeforeUnmount(() => controller?.abort())
</script>

<template>
  <WorkspaceShell>
    <div class="studio-heading"><div><p class="eyebrow">FRAME ROOM / MODERATION</p><h1>审核工作台</h1><p class="muted">先看作品内容，再决定是否让它进入公开空间。{{ queueLabel }}</p></div></div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert"/><el-alert v-if="notice" :title="notice" type="success" :closable="false" role="status"/>
    <div class="admin-layout">
      <section class="studio-panel review-queue" :aria-busy="loading"><div class="section-heading"><h2>待审核 <span class="count">{{ total }}</span></h2><el-button :loading="loading" @click="load()">刷新</el-button></div>
        <p v-if="loading" class="empty-state">正在读取审核队列…</p><button v-for="item in items" v-else :key="item.video.id" class="queue-item" :class="{selected:selected?.id === item.video.id}" @click="select(item.video)"><span><strong>{{ item.video.title }}</strong><small>作者 #{{ item.ownerId }} · 第 {{ item.video.reviewRound }} 轮</small></span><time>{{ formatTime(item.video.reviewSubmittedAt) }}</time></button><p v-if="!loading && !items.length" class="empty-state">暂无待审核作品。</p>
        <div class="page-controls"><el-button :disabled="loading || page <= 1" @click="load(page - 1)">上一页</el-button><span>第 {{ page }} 页</span><el-button :disabled="loading || page * 20 >= total" @click="load(page + 1)">下一页</el-button></div>
      </section>
      <section class="studio-panel review-detail" v-if="selected"><div class="review-detail-head"><div><p class="card-number">作品 #{{ selected.id }} · 作者作品</p><h2>{{ selected.title }}</h2></div><el-tag>{{ reviewLabels[selected.reviewStatus] }}</el-tag></div><p class="video-description">{{ selected.description || '作者暂未填写简介。' }}</p><dl class="video-facts"><div><dt>处理状态</dt><dd>{{ processingLabels[selected.processingStatus] }}</dd></div><div><dt>视频时长</dt><dd>{{ selected.durationMillis ? `${(selected.durationMillis / 1000).toFixed(1)} 秒` : '—' }}</dd></div><div><dt>发布状态</dt><dd>{{ publicationLabels[selected.publicationStatus] }}</dd></div></dl><div class="inline-actions"><el-button :disabled="busy" @click="preview">{{ media ? '重新获取预览' : '预览视频' }}</el-button><el-button type="primary" :loading="busy" @click="decide('APPROVED')">通过审核</el-button><el-button type="danger" :loading="busy" @click="decide('REJECTED')">驳回</el-button></div><video v-if="media" class="studio-player admin-player" :src="media.url" controls preload="metadata"/><p v-if="media" class="muted">预览链接到期：{{ formatTime(media.expiresAt) }}</p><label class="reason-label" for="review-reason">处理说明 <span>驳回时必填</span></label><textarea id="review-reason" v-model="reason" maxlength="500" rows="4" placeholder="告诉作者需要修改什么，或记录本次审核依据。"/><h3 class="history-subheading">历史审核</h3><article v-for="record in history" :key="record.id" class="history-item"><div class="section-heading"><strong>第 {{ record.reviewRound }} 轮 · {{ record.outcome === 'APPROVED' ? '通过' : '驳回' }}</strong><time>{{ formatTime(record.createdAt) }}</time></div><p>{{ record.reason || '未附加说明' }}</p></article><p v-if="!history.length" class="muted">暂无历史记录。</p></section>
      <section v-else class="studio-panel empty-state">从左侧选择一部待审核作品。</section>
    </div>
  </WorkspaceShell>
</template>
