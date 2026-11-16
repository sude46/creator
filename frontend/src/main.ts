import { createApp } from 'vue'
import { ElAlert, ElButton, ElInput, ElTag } from 'element-plus'
import 'element-plus/es/components/alert/style/css'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/input/style/css'
import 'element-plus/es/components/tag/style/css'
import App from './App.vue'
import { router } from './router'
import './style.css'

createApp(App).use(router).use(ElAlert).use(ElButton).use(ElInput).use(ElTag).mount('#app')
