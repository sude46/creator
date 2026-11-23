<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { auth } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const registering = computed(() => route.path === '/register')
const form = reactive({ username: '', password: '', confirm: '' })
const busy = ref(false)
const error = ref('')
const success = ref('')
const routeNotice = computed(() => route.query.changed === '1' ? '密码已修改，请用新密码登录。' : '')

watch(() => route.path, () => {
  error.value = ''
  form.password = ''
  form.confirm = ''
})

async function submit() {
  if (busy.value) return
  error.value = ''
  success.value = ''
  if (registering.value && form.password !== form.confirm) {
    error.value = '两次输入的密码不一致'
    return
  }
  busy.value = true
  try {
    if (registering.value) {
      await auth.register(form.username, form.password)
      await router.push('/login')
      success.value = '注册成功，请登录你的账号'
    } else {
      await auth.login(form.username, form.password)
      await router.push('/')
    }
  } catch (failure) {
    error.value = failure instanceof Error ? failure.message : '操作失败，请重试'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <main class="auth-layout">
    <section class="intro-panel">
      <a class="brand" href="/">帧间<span>FRAME ROOM</span></a>
      <div class="intro-copy">
        <p class="eyebrow">每一帧，都值得被看见</p>
        <h1>让你的故事<br />有一个放映的地方。</h1>
        <p class="intro-description">从创作到分享，在这里收藏灵感，也让更多人遇见你的作品。</p>
        <div class="film-art" aria-hidden="true"><div class="film-frame"><span>▶</span></div><div class="film-line"></div></div>
      </div>
      <p class="intro-footer">帧间 · 每一帧，都值得被看见</p>
    </section>
    <section class="form-panel">
      <div class="auth-card">
        <p class="eyebrow">{{ registering ? '开始你的创作旅程' : '欢迎回来' }}</p>
        <h2>{{ registering ? '创建账号' : '登录帧间' }}</h2>
        <p class="muted">{{ registering ? '注册后即可使用平台，管理自己的作品。' : '登录后继续创作与发现。' }}</p>
        <el-alert v-if="routeNotice" :title="routeNotice" type="success" :closable="false" role="status" />
        <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" role="alert" />
        <el-alert v-if="success" :title="success" type="success" show-icon :closable="false" role="status" />
        <form @submit.prevent="submit">
          <label for="username">用户名</label>
          <el-input id="username" v-model="form.username" autocomplete="username" maxlength="32" placeholder="请输入用户名" :disabled="busy" required />
          <p v-if="registering" class="field-hint">3～32 位字母、数字或下划线</p>
          <label for="password">密码</label>
          <el-input id="password" v-model="form.password" type="password" show-password :autocomplete="registering ? 'new-password' : 'current-password'" maxlength="64" placeholder="请输入密码" :disabled="busy" required />
          <p v-if="registering" class="field-hint">10～64 位，包含字母和数字；中文等多字节字符总计不超过 72 字节</p>
          <template v-if="registering">
            <label for="confirm">确认密码</label>
            <el-input id="confirm" v-model="form.confirm" type="password" show-password autocomplete="new-password" maxlength="64" placeholder="再次输入密码" :disabled="busy" required />
          </template>
          <el-button class="submit-button" type="primary" native-type="submit" :loading="busy">{{ registering ? '创建账号' : '登 录' }}</el-button>
        </form>
        <p class="auth-switch">{{ registering ? '已经有账号？' : '还没有账号？' }} <RouterLink :to="registering ? '/login' : '/register'">{{ registering ? '去登录' : '立即注册' }}</RouterLink></p>
      </div>
    </section>
  </main>
</template>
