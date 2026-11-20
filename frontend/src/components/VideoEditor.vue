<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ApiFailure } from '../api/http'
import { creatorApi } from '../api/creator'
import type { Category, CreatorVideo, Draft, Metadata } from '../api/creator'
import { newRequestKey } from '../api/requestKey'
import { validateVideoFile } from '../api/uploadFile'
import { useStudioError } from '../composables/useStudioError'

const props = defineProps<{ video?: CreatorVideo }>()
const original = props.video
const emit = defineEmits<{ saved: [draft: Draft]; cancel: []; busy: [value: boolean] }>()
const form = reactive<Metadata>({ title: props.video?.title ?? '', description: props.video?.description ?? '', categoryId: props.video?.categoryId ?? '', tags: [] })
const tagText = ref('')
const categories = ref<Category[]>([])
const page = ref(1)
const total = ref(0)
const loading = ref(false)
const busy = ref(false)
const uploading = ref(false)
const uploadPercent = ref<number>()
const uncertain = ref(false)
const error = ref('')
const categoryError = ref('')
const file = ref<File>()
const createdDraft = ref<Draft>()
const explain = useStudioError()
const requestKey = newRequestKey()
let submitted: Metadata | undefined
let categoryRequest: AbortController | undefined
let uploadRequest: AbortController | undefined
const selectedOutsidePage = computed(() => form.categoryId && !categories.value.some(category => category.id === form.categoryId))
const creating = computed(() => !props.video)
const primaryLabel = computed(() => {
  if (!creating.value) return '保存修改'
  if (createdDraft.value) return uploading.value ? `正在上传 ${uploadPercent.value ?? 0}%` : '重试上传'
  return uploading.value ? `正在上传 ${uploadPercent.value ?? 0}%` : '创建并上传'
})

async function loadCategories(next = 1) {
  categoryRequest?.abort()
  const controller = new AbortController()
  categoryRequest = controller
  loading.value = true
  categoryError.value = ''
  try {
    const result = await creatorApi.categories(next, controller.signal)
    if (controller.signal.aborted) return
    categories.value = result.items
    page.value = next
    total.value = result.total
  } catch (failure) { if (!controller.signal.aborted) categoryError.value = explain(failure) }
  finally { if (!controller.signal.aborted) loading.value = false }
}

function chooseFile(event: Event) {
  const selected = (event.target as HTMLInputElement).files?.[0]
  file.value = undefined
  if (!selected) return
  const problem = validateVideoFile(selected)
  if (problem) { error.value = problem; return }
  file.value = selected
  error.value = ''
}

async function ensureDraft(): Promise<Draft> {
  if (createdDraft.value) return createdDraft.value
  submitted ??= { ...form, title: form.title.trim() }
  const draft = await creatorApi.create(submitted, requestKey)
  createdDraft.value = draft
  return draft
}

async function uploadDraft(draft: Draft) {
  if (!file.value) return
  const controller = new AbortController()
  uploadRequest = controller
  const deadline = setTimeout(() => controller.abort(), 180000)
  uploading.value = true
  uploadPercent.value = 0
  try {
    await creatorApi.upload(draft, file.value, controller.signal, value => { uploadPercent.value = value })
  } finally {
    clearTimeout(deadline)
    uploading.value = false
    uploadRequest = undefined
    setTimeout(() => { if (!uploading.value) uploadPercent.value = undefined }, 800)
  }
}

function parsedTags(): string[] | undefined {
  const names = tagText.value.split(/[,，]/).map(value => value.trim().replace(/^#+/, '').trim()).filter(Boolean)
  if (names.length > 5) { error.value = '标签最多 5 个。'; return undefined }
  if (names.some(name => [...name].length > 20)) { error.value = '每个标签最多 20 个字符。'; return undefined }
  if (new Set(names.map(name => name.toLowerCase())).size !== names.length) { error.value = '标签不能重复。'; return undefined }
  return names
}

async function save() {
  if (busy.value) return
  const tags = parsedTags()
  if (!tags) return
  form.tags = tags
  if (!form.title.trim() || !form.categoryId) { error.value = '请填写标题并选择分类'; return }
  if (creating.value && !file.value && !createdDraft.value) { error.value = '请选择视频文件，或使用“仅保存草稿”。'; return }
  busy.value = true
  emit('busy', true)
  error.value = ''
  try {
    if (!creating.value) {
      if (original) emit('saved', await creatorApi.update(original, { ...form, title: form.title.trim() }))
      return
    }
    const draft = await ensureDraft()
    if (file.value) await uploadDraft(draft)
    emit('saved', draft)
  } catch (failure) {
    if (createdDraft.value && file.value) {
      try {
        const state = await creatorApi.uploadState(createdDraft.value.id)
        if (state.sourceUploaded) { emit('saved', createdDraft.value); return }
      } catch { /* keep the draft and let the author retry from the detail page */ }
    }
    error.value = explain(failure)
    if (creating.value && !createdDraft.value) {
      uncertain.value = !(failure instanceof ApiFailure && failure.status >= 400 && failure.status < 500)
      if (!uncertain.value) submitted = undefined
      if (uncertain.value) error.value += '。创建结果未确认，请保持原信息重试，或先在作品列表核对，勿另建同一作品。'
    } else if (createdDraft.value) {
      error.value += '。作品草稿已创建，可重试上传，或返回列表打开作品继续。'
    } else if (failure instanceof ApiFailure && failure.status === 409) {
      error.value += '。请关闭编辑并刷新作品后重新编辑。'
    }
  } finally { busy.value = false; emit('busy', false) }
}

async function saveDraftOnly() {
  if (busy.value || !creating.value) return
  if (!form.title.trim() || !form.categoryId) { error.value = '请填写标题并选择分类'; return }
  busy.value = true
  emit('busy', true)
  error.value = ''
  try {
    emit('saved', await ensureDraft())
  } catch (failure) {
    error.value = explain(failure)
    uncertain.value = !(failure instanceof ApiFailure && failure.status >= 400 && failure.status < 500)
    if (uncertain.value) error.value += '。创建结果未确认，请保持原信息重试，或先在作品列表核对。'
  } finally { busy.value = false; emit('busy', false) }
}

onMounted(() => { void loadCategories(); if (props.video) void creatorApi.tags(props.video.id).then(result => { tagText.value = result.tags.join('，') }).catch(() => { /* tags can be re-entered when the detail request fails */ }) })
onBeforeUnmount(() => { categoryRequest?.abort(); uploadRequest?.abort() })
</script>

<template>
  <section class="studio-panel editor-panel" aria-label="作品信息编辑">
    <h2>{{ video ? '编辑作品信息' : '发布作品' }}</h2>
    <p class="muted">{{ video ? (video.publicationStatus === 'PUBLISHED' ? '保存修改会撤下已发布内容，并要求重新审核。' : '修改作品信息后，如已送审需要重新提交。') : '填写信息并选择视频，创建后自动上传，随后进入处理流程。' }}</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" />
    <form class="studio-form" @submit.prevent="save">
      <fieldset :disabled="busy || uncertain || !!createdDraft">
        <label for="video-title">标题</label><input id="video-title" v-model="form.title" maxlength="120" required placeholder="为作品取一个标题" />
        <label for="video-description">简介</label><textarea id="video-description" v-model="form.description" maxlength="2000" rows="4" placeholder="介绍你的作品（选填）"></textarea>
        <label for="video-category">分类</label>
        <select id="video-category" v-model="form.categoryId" required>
          <option value="" disabled>请选择分类</option>
          <option v-if="selectedOutsidePage" :value="form.categoryId">已选分类 #{{ form.categoryId }}（当前页未列出）</option>
          <option v-for="category in categories" :key="category.id" :value="category.id">{{ category.name }}</option>
        </select>
        <label for="video-tags">标签</label>
        <input id="video-tags" v-model="tagText" maxlength="200" placeholder="用逗号分隔，最多 5 个，每个不超过 20 字" />
        <p class="muted">标签用于发现和筛选；修改信息后，已发布作品需要重新提交审核。</p>
        <div class="inline-actions category-pages"><el-button size="small" :disabled="loading || page <= 1" @click="loadCategories(page - 1)">上一页分类</el-button><span class="muted">第 {{ page }} 页 · 共 {{ total }} 项</span><el-button size="small" :disabled="loading || page * 20 >= total || page >= 1000" @click="loadCategories(page + 1)">下一页分类</el-button><el-button v-if="categoryError" size="small" @click="loadCategories(page)">重试分类</el-button></div>
        <p v-if="categoryError" class="field-error" role="alert">{{ categoryError }}</p>
        <p v-else-if="!loading && total === 0" class="muted">暂无可选分类，请联系管理员创建或启用分类。</p>
        <template v-if="creating">
          <label class="file-picker" for="new-video-file">视频文件</label>
          <input id="new-video-file" type="file" accept=".mp4,video/mp4" :disabled="busy || !!createdDraft" @change="chooseFile" />
          <p v-if="file" class="muted">{{ file.name }} · {{ (file.size / 1024 / 1024).toFixed(2) }} MiB</p>
          <p v-else class="muted">仅支持 MP4，最大 200 MiB；上传后由平台转码并生成封面。</p>
          <p v-if="createdDraft" class="muted">作品草稿已创建。上传失败时可直接重试，不要重复创建作品。</p>
        </template>
      </fieldset>
      <progress v-if="uploading" class="upload-progress" :value="uploadPercent ?? 0" max="100" aria-label="上传进度"></progress>
      <div class="inline-actions">
        <el-button type="primary" native-type="submit" :loading="busy || uploading" :disabled="uncertain">{{ primaryLabel }}</el-button>
        <el-button v-if="creating && !createdDraft" :disabled="busy" @click="saveDraftOnly">仅保存草稿</el-button>
        <el-button :disabled="busy" @click="emit('cancel')">{{ uncertain ? '返回列表核对' : '取消' }}</el-button>
      </div>
    </form>
  </section>
</template>

<style scoped>
.upload-progress { display: block; width: 100%; height: 6px; margin: 12px 0 0; accent-color: var(--color-primary); }
</style>
