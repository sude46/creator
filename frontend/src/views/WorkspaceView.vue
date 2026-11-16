<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { auth } from '../stores/auth'
import { request } from '../api/http'

const router = useRouter()
const user = auth.user
const busy = ref(false)
const message = ref('')
const error = ref('')

async function logout() {
  busy.value = true
  error.value = ''
  try {
    await auth.logout()
    await router.push('/login')
  } catch (failure) {
    error.value = failure instanceof Error ? failure.message : '退出失败，请重试'
  } finally {
    busy.value = false
  }
}

async function verifyAdmin() {
  error.value = ''
  try {
    const result = await request<{ message: string }>('/admin/overview')
    message.value = result.message
  } catch (failure) {
    error.value = failure instanceof Error ? failure.message : '请求失败'
  }
}
</script>

<template>
  <div class="workspace">
    <header class="workspace-header">
      <RouterLink class="brand" to="/">映作<span>CREATOR STUDIO</span></RouterLink>
      <div class="account-menu"><span>{{ user?.username }}</span><el-tag :type="user?.role === 'ADMIN' ? 'warning' : 'info'">{{ user?.role === 'ADMIN' ? '管理员' : '创作者' }}</el-tag><el-button :loading="busy" @click="logout">退出登录</el-button></div>
    </header>
    <main class="workspace-main">
      <p class="eyebrow">你的创作空间</p>
      <h1>你好，{{ user?.username }}。</h1>
      <p class="muted">账号已就绪，期待你的第一部作品。</p>
      <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
      <section class="module-status">
        <span class="status-dot"></span><strong>账号与权限已接入</strong>
        <p>视频上传、作品管理和播放功能正在开发，完成后将在这里开放。</p>
        <el-button v-if="user?.role === 'ADMIN'" type="primary" @click="verifyAdmin">验证管理权限</el-button>
        <p v-if="message" role="status">{{ message }}</p>
      </section>
    </main>
  </div>
</template>
