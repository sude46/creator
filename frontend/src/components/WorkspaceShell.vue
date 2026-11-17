<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { auth } from '../stores/auth'
import { useStudioError } from '../composables/useStudioError'

const router = useRouter()
const user = auth.user
const busy = ref(false)
const error = ref('')
const explain = useStudioError()
async function logout() {
  if (busy.value) return
  busy.value = true
  try { await auth.logout(); await router.push('/login') }
  catch (failure) { error.value = explain(failure) }
  finally { busy.value = false }
}
</script>

<template>
  <div class="workspace">
    <header class="workspace-header">
      <RouterLink class="brand" to="/">帧间<span>FRAME ROOM</span></RouterLink>
      <nav class="workspace-nav" aria-label="主导航"><RouterLink to="/">发现作品</RouterLink><RouterLink to="/favorites">我的收藏</RouterLink><RouterLink to="/studio">创作中心</RouterLink><RouterLink v-if="user?.role === 'ADMIN'" to="/admin/reviews">审核工作台</RouterLink></nav>
      <div class="account-menu"><span>{{ user?.username }}</span><el-tag :type="user?.role === 'ADMIN' ? 'warning' : 'info'">{{ user?.role === 'ADMIN' ? '管理员' : '创作者' }}</el-tag><el-button :loading="busy" @click="logout">退出登录</el-button></div>
    </header>
    <main class="studio-main">
      <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" />
      <slot />
    </main>
  </div>
</template>

<style scoped>
.workspace-nav { flex-wrap: wrap; }
</style>
