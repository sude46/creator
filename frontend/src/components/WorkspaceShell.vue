<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { auth } from '../stores/auth'
import { useStudioError } from '../composables/useStudioError'

const router = useRouter()
const route = useRoute()
const user = auth.user
const adminArea = computed(() => user.value?.role === 'ADMIN' && (route.path === '/admin' || route.path.startsWith('/admin/')))
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
      <nav class="workspace-nav" aria-label="主导航"><RouterLink to="/">发现</RouterLink><RouterLink to="/favorites">我的收藏</RouterLink><RouterLink to="/studio">我的作品</RouterLink><RouterLink v-if="user?.role === 'ADMIN'" to="/admin">管理后台</RouterLink></nav>
      <div class="account-menu"><span>{{ user?.username }}</span><el-tag :type="user?.role === 'ADMIN' ? 'warning' : 'info'">{{ user?.role === 'ADMIN' ? '管理员' : '用户' }}</el-tag><el-button :loading="busy" @click="logout">退出登录</el-button></div>
    </header>
    <nav v-if="adminArea" class="admin-nav" aria-label="管理导航">
      <span class="admin-nav-group">概览</span><RouterLink to="/admin">总览</RouterLink>
      <span class="admin-nav-group">内容</span><RouterLink to="/admin/videos">作品管理</RouterLink><RouterLink to="/admin/reviews">审核工作台</RouterLink><RouterLink to="/admin/processing">异常处理</RouterLink>
      <span class="admin-nav-group">配置</span><RouterLink to="/admin/categories">分类管理</RouterLink>
      <span class="admin-nav-group">系统</span><RouterLink to="/admin/users">用户管理</RouterLink><RouterLink to="/admin/audit">审计记录</RouterLink>
    </nav>
    <main class="studio-main">
      <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" />
      <slot />
    </main>
  </div>
</template>

<style scoped>
.workspace-nav { flex-wrap: wrap; }
.admin-nav { display: flex; flex-wrap: wrap; align-items: center; gap: 6px 16px; padding: 11px 28px; border-top: 1px solid #d9ddd5; border-bottom: 1px solid #d9ddd5; background: #fff; font-size: 13px; }
.admin-nav a { color: #4f5f56; }
.admin-nav a.router-link-exact-active { color: #c65b3d; font-weight: 700; }
.admin-nav-group { color: #87918a; font-size: 11px; letter-spacing: .14em; margin-left: 8px; }
.admin-nav-group:first-child { margin-left: 0; }
@media (max-width: 760px) { .admin-nav { padding: 10px 16px; } }
</style>
