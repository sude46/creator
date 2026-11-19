<script setup lang="ts">
import { useRoute } from 'vue-router'
import WorkspaceShell from '../components/WorkspaceShell.vue'
const route = useRoute()
</script>

<template>
  <WorkspaceShell>
    <section class="studio-panel not-found">
      <p class="eyebrow">404 / NOT FOUND</p>
      <h1>页面不存在</h1>
      <p class="muted">没有找到 <code>{{ route.fullPath }}</code>。链接可能已失效，或作品已被删除。</p>
      <div class="inline-actions"><RouterLink to="/">返回发现页</RouterLink><RouterLink to="/studio">回到创作中心</RouterLink></div>
    </section>
  </WorkspaceShell>
</template>

<style scoped>
.not-found { max-width: 720px; margin: 60px auto; padding: 40px; }
.not-found h1 { font-size: 32px; margin: 12px 0; }
.not-found code { overflow-wrap: anywhere; }
</style>
