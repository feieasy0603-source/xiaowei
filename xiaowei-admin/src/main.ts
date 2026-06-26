import { createApp } from 'vue'
import { provideGlobalConfig } from 'element-plus/es/components/config-provider/index.mjs'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { ElMessageBox } from 'element-plus/es/components/message-box/index.mjs'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import '@/styles/admin.css'
import App from './App.vue'
import router from './router'
import { bindAdminRouter } from '@/api/http'
import { loadAdminBranding, updateAdminDocumentBranding } from '@/stores/siteBranding'

bindAdminRouter(router)

const app = createApp(App)
app.use(router)
app.use(ElMessage)
app.use(ElMessageBox)
provideGlobalConfig({ locale: zhCn }, app, true)
app.mount('#app')

router.afterEach((to) => {
  const title = [...to.matched]
    .reverse()
    .map((record) => record.meta?.title)
    .find((title): title is string => typeof title === 'string')
  updateAdminDocumentBranding(title)
})

void loadAdminBranding()
