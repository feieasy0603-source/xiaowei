import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

function manualChunks(id: string) {
  const normalized = id.replaceAll('\\', '/')
  if (!normalized.includes('/node_modules/')) return undefined

  if (normalized.includes('/node_modules/@vue/') || normalized.includes('/node_modules/vue/')) {
    return 'vue-vendor'
  }
  if (normalized.includes('/node_modules/vue-router/')) return 'vue-vendor'

  if (normalized.includes('/node_modules/@element-plus/icons-vue/')) return 'ep-icons'
  if (normalized.includes('/node_modules/element-plus/')) {
    if (/\/element-plus\/es\/components\/(?:table|pagination|tag|empty|progress)\//.test(normalized)) {
      return 'ep-data'
    }
    if (
      /\/element-plus\/es\/components\/(?:form|input|input-number|select|option|radio|checkbox|switch|upload)\//.test(
        normalized,
      )
    ) {
      return 'ep-form'
    }
    if (
      /\/element-plus\/es\/components\/(?:dialog|drawer|message|message-box|tooltip|dropdown|popover|popper|overlay|focus-trap)\//.test(
        normalized,
      )
    ) {
      return 'ep-feedback'
    }
    return 'ep-core'
  }

  return 'vendor'
}

export default defineConfig({
  plugins: [
    vue(),
    AutoImport({
      resolvers: [ElementPlusResolver()],
    }),
    Components({
      resolvers: [ElementPlusResolver()],
    }),
  ],
  base: '/admin/',
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  server: {
    host: true,
    port: 5174,
    strictPort: true,
    open: '/admin/login',
    proxy: { '/api': { target: 'http://localhost:8080', changeOrigin: true } },
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks,
      },
    },
  },
})
