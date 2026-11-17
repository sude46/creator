<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ApiFailure } from '../api/http'
import { creatorApi } from '../api/creator'
import type { Category, CreatorVideo, Draft, Metadata } from '../api/creator'
import { useStudioError } from '../composables/useStudioError'

const props = defineProps<{ video?: CreatorVideo }>()
const original = props.video
const emit = defineEmits<{ saved: [draft: Draft]; cancel: []; busy: [value: boolean] }>()
const form = reactive<Metadata>({ title: props.video?.title ?? '', description: props.video?.description ?? '', categoryId: props.video?.categoryId ?? '' })
const categories = ref<Category[]>([])
const page = ref(1)
const total = ref(0)
const loading = ref(false)
const busy = ref(false)
const uncertain = ref(false)
const error = ref('')
const categoryError = ref('')
const explain = useStudioError()
const requestKey = crypto.randomUUID()
let submitted: Metadata | undefined
let categoryRequest: AbortController | undefined
const selectedOutsidePage = computed(() => form.categoryId && !categories.value.some(category => category.id === form.categoryId))

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

async function save() {
  if (busy.value) return
  if (!form.title.trim() || !form.categoryId) { error.value = '请填写标题并选择分类'; return }
  busy.value = true
  emit('busy', true)
  error.value = ''
  try {
    if (!props.video) {
      submitted ??= { ...form, title: form.title.trim() }
      emit('saved', await creatorApi.create(submitted, requestKey))
    } else if (original) emit('saved', await creatorApi.update(original, { ...form, title: form.title.trim() }))
  } catch (failure) {
    error.value = explain(failure)
    if (!props.video) {
      uncertain.value = !(failure instanceof ApiFailure && failure.status >= 400 && failure.status < 500)
      if (!uncertain.value) submitted = undefined
      if (uncertain.value) error.value += '。创建结果未确认，请保持原信息重试，或先在作品列表核对，勿另建同一作品。'
    } else if (failure instanceof ApiFailure && failure.status === 409) error.value += '。请关闭编辑并刷新作品后重新编辑。'
  } finally { busy.value = false; emit('busy', false) }
}
onMounted(() => void loadCategories())
onBeforeUnmount(() => categoryRequest?.abort())
</script>

<template>
  <section class="studio-panel editor-panel" aria-label="作品信息编辑">
    <h2>{{ video ? '编辑作品信息' : '创建新作品' }}</h2>
    <p class="muted">{{ video?.publicationStatus === 'PUBLISHED' ? '保存修改会撤下已发布内容，并要求重新审核。' : '先保存作品信息，再上传自己的视频。' }}</p>
    <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" />
    <form class="studio-form" @submit.prevent="save">
      <fieldset :disabled="busy || uncertain">
        <label for="video-title">标题</label><input id="video-title" v-model="form.title" maxlength="120" required placeholder="为作品取一个标题" />
        <label for="video-description">简介</label><textarea id="video-description" v-model="form.description" maxlength="2000" rows="4" placeholder="介绍你的作品（选填）"></textarea>
        <label for="video-category">分类</label>
        <select id="video-category" v-model="form.categoryId" required>
          <option value="" disabled>请选择分类</option>
          <option v-if="selectedOutsidePage" :value="form.categoryId">已选分类 #{{ form.categoryId }}（当前页未列出）</option>
          <option v-for="category in categories" :key="category.id" :value="category.id">{{ category.name }}</option>
        </select>
        <div class="inline-actions category-pages"><el-button size="small" :disabled="loading || page <= 1" @click="loadCategories(page - 1)">上一页分类</el-button><span class="muted">第 {{ page }} 页 · 共 {{ total }} 项</span><el-button size="small" :disabled="loading || page * 20 >= total || page >= 1000" @click="loadCategories(page + 1)">下一页分类</el-button><el-button v-if="categoryError" size="small" @click="loadCategories(page)">重试分类</el-button></div>
        <p v-if="categoryError" class="field-error" role="alert">{{ categoryError }}</p>
        <p v-else-if="!loading && total === 0" class="muted">暂无可选分类，请联系管理员创建或启用分类。</p>
      </fieldset>
      <div class="inline-actions"><el-button type="primary" native-type="submit" :loading="busy">{{ uncertain ? '按原信息重试创建' : '保存作品' }}</el-button><el-button :disabled="busy" @click="emit('cancel')">{{ uncertain ? '返回列表核对' : '取消' }}</el-button></div>
    </form>
  </section>
</template>
