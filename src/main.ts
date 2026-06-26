import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { provideGlobalConfig } from 'element-plus/es/components/config-provider/index.mjs'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { ElMessageBox } from 'element-plus/es/components/message-box/index.mjs'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import App from './App.vue'
import router from './router'
import '@/styles/global.css'
import { useProductsStore } from '@/stores/products'
import { getToken, useApiEnabled } from '@/api/http'
import { useAppStore } from '@/stores/app'
import { initChannelFromUrl } from '@/composables/useChannelCode'
import { useSiteBrandingStore } from '@/stores/siteBranding'

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)
app.use(router)
app.use(ElMessage)
app.use(ElMessageBox)
provideGlobalConfig({ locale: zhCn }, app, true)

app.config.errorHandler = (err, _instance, info) => {
  console.error('[xiaowei]', info, err)
  const root = document.getElementById('app')
  if (root && !root.querySelector('.boot-error')) {
    const el = document.createElement('div')
    el.className = 'boot-error'
    el.style.cssText =
      'padding:24px;font-family:system-ui;color:#b91c1c;max-width:640px;margin:40px auto;line-height:1.6'
    el.innerHTML = `<strong>页面渲染出错</strong><p style="margin-top:8px;font-size:14px;color:#64748b">${String(err)}</p><p style="margin-top:12px;font-size:13px">请打开浏览器控制台查看详情，或尝试清除站点数据后刷新。</p>`
    root.appendChild(el)
  }
}

try {
  app.mount('#app')
} catch (e) {
  const root = document.getElementById('app')
  if (root) {
    root.innerHTML = `<div style="padding:24px;color:#b91c1c">启动失败: ${String(e)}</div>`
  }
  throw e
}

async function loadRemoteData() {
  const brandingStore = useSiteBrandingStore()
  void brandingStore.load()
  if (!useApiEnabled()) return
  const productsStore = useProductsStore()
  const appStore = useAppStore()
  await Promise.all([productsStore.loadFromApi(), initChannelFromUrl()])
  if (!getToken()) return
  await appStore.refreshProfile()
}

void loadRemoteData()
