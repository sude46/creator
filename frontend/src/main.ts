import { createApp } from 'vue'
import { ElAlert, ElButton, ElInput, ElTag } from 'element-plus'
import 'element-plus/es/components/alert/style/css'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/input/style/css'
import 'element-plus/es/components/tag/style/css'
import App from './App.vue'
import { router } from './router'
import { auth } from './stores/auth'
import { ensureAbortSignalSupport } from './api/signals'
import './tokens.css'
import './style.css'
import './studio.css'
import './public.css'

ensureAbortSignalSupport()

window.addEventListener('creator:unauthorized', () => {
  if (!auth.user.value) return
  auth.expire()
  const current = router.currentRoute.value
  if (current.meta.guest) return
  void router.push({ path: '/login', query: { redirect: current.fullPath, expired: '1' } })
})

createApp(App).use(router).use(ElAlert).use(ElButton).use(ElInput).use(ElTag).mount('#app')
