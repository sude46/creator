<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import WorkspaceShell from '../components/WorkspaceShell.vue'
import { appealApi } from '../api/appeals'
import type { Appeal, CommentAppealContext } from '../api/appeals'
import { formatTime } from '../api/creator'
import { useStudioError } from '../composables/useStudioError'

const route = useRoute()
const explain = useStudioError()
const id = String(route.params.id)
const context = ref<CommentAppealContext>()
const appeals = ref<Appeal[]>([])
const reason = ref('')
const loading = ref(false)
const busy = ref(false)
const error = ref('')
const notice = ref('')
let request: AbortController | undefined
let stopped = false

const openAppeal = computed(() => appeals.value.find(item => item.status === 'OPEN'))
function label(status: string) { return status === 'OPEN' ? '待处理' : status === 'ACCEPTED' ? '已受理' : '已驳回' }

async function load() {
  request?.abort()
  const controller = new AbortController()
  request = controller
  loading.value = true
  error.value = ''
  try {
    const [current, list] = await Promise.all([
      appealApi.commentContext(id, controller.signal),
      appealApi.mine('COMMENT', id, controller.signal),
    ])
    if (stopped || controller.signal.aborted) return
    context.value = current
    appeals.value = list.items
  } catch (failure) { if (!stopped && !controller.signal.aborted) error.value = explain(failure) }
  finally { if (!stopped && !controller.signal.aborted) loading.value = false }
}

async function submit() {
  const clean = reason.value.trim()
  if (!clean || busy.value) return
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    await appealApi.create('COMMENT', id, clean)
    if (stopped) return
    reason.value = ''
    notice.value = '申诉已提交，等待管理员处理。'
    await load()
  } catch (failure) { if (!stopped) error.value = explain(failure) }
  finally { if (!stopped) busy.value = false }
}

onMounted(() => { if (/^[1-9]\d{0,18}$/.test(id)) void load(); else error.value = '评论编号无效' })
onBeforeUnmount(() => { stopped = true; request?.abort() })
</script>

<template>
  <WorkspaceShell>
    <div class="studio-heading"><div><p class="eyebrow">FRAME ROOM / APPEAL</p><h1>评论申诉</h1><p class="muted">评论被删除后可以提交申诉，管理员复核通过会恢复显示。</p></div><RouterLink class="create-link" to="/notifications">返回通知</RouterLink></div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" />
    <p v-if="notice" class="muted" role="status">{{ notice }}</p>
    <p v-if="loading" class="empty-state" role="status">正在读取评论…</p>
    <section v-if="context" class="studio-panel">
      <div class="section-heading"><h2>原评论</h2><el-tag :type="context.status === 'ACTIVE' ? 'success' : 'info'">{{ context.status === 'ACTIVE' ? '已恢复' : '已删除' }}</el-tag></div>
      <p class="video-description">{{ context.content }}</p>
      <p class="muted">所在作品 #<RouterLink :to="`/videos/${context.videoId}`">{{ context.videoId }}</RouterLink></p>

      <article v-for="appeal in appeals" :key="appeal.id" class="history-item">
        <div class="section-heading"><strong>{{ label(appeal.status) }} · {{ formatTime(appeal.createdAt) }}</strong><el-tag :type="appeal.status === 'OPEN' ? 'warning' : appeal.status === 'ACCEPTED' ? 'success' : 'danger'">{{ label(appeal.status) }}</el-tag></div>
        <p class="history-reason">{{ appeal.reason }}</p>
        <p v-if="appeal.resolutionReason" class="muted">处理说明：{{ appeal.resolutionReason }}</p>
      </article>

      <template v-if="context.status === 'DELETED' && !openAppeal">
        <label class="reason-label" for="comment-appeal-reason">申诉说明</label>
        <textarea id="comment-appeal-reason" v-model="reason" maxlength="500" rows="4" placeholder="说明评论为什么应当恢复，1～500 字。" :disabled="busy" />
        <div class="inline-actions"><el-button type="primary" :loading="busy" :disabled="!reason.trim()" @click="submit">提交申诉</el-button></div>
      </template>
      <p v-else-if="openAppeal" class="muted">已有待处理申诉，请等待管理员处理。</p>
      <p v-else class="muted">评论已恢复，无需继续申诉。</p>
    </section>
  </WorkspaceShell>
</template>
