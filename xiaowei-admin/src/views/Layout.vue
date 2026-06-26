<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { adminFetch } from '@/api/http'
import {
  DataAnalysis,
  Document,
  Files,
  Grid,
  Link,
  List,
  Money,
  Reading,
  Setting,
  Guide,
  Picture,
  User,
  Medal,
  Present,
  Share,
  ChatDotRound,
} from '@element-plus/icons-vue'
import { setAdminToken } from '@/api/http'
import { providerLabel } from '@/constants/ai-providers'
import { useAdminBranding } from '@/stores/siteBranding'

const router = useRouter()
const route = useRoute()
const brandingStore = useAdminBranding()

const active = computed(() => route.path)

const pageTitle = computed(() => {
  for (let i = route.matched.length - 1; i >= 0; i--) {
    const t = route.matched[i].meta?.title
    if (typeof t === 'string') return t
  }
  return '管理端'
})

const menuGroups = [
  {
    title: '概览',
    items: [
      { path: '/', label: 'AI 运营看板', icon: DataAnalysis },
      { path: '/deploy', label: '生产部署向导', icon: Guide },
      { path: '/branding-settings', label: '站点品牌', icon: Picture },
    ],
  },
  {
    title: 'AI 生成业务',
    items: [
      { path: '/jobs', label: '生成任务', icon: List },
      { path: '/papers', label: '论文草稿', icon: Document },
      { path: '/literature', label: '文献库', icon: Reading },
      { path: '/outlines', label: '提纲模板', icon: Files },
      { path: '/products', label: 'AI 产品', icon: Grid },
      { path: '/ai-settings', label: 'AI 模型池', icon: Setting },
    ],
  },
  {
    title: '交易运营',
    items: [
      { path: '/users', label: '用户管理', icon: User },
      { path: '/vip-quotas', label: 'VIP 配额', icon: Medal },
      { path: '/orders', label: '订单管理', icon: Money },
      { path: '/wallet-recharges', label: '充值订单', icon: Money },
      { path: '/payment-records', label: '支付流水', icon: Money },
      { path: '/channels', label: '渠道管理', icon: Link },
      { path: '/gift-codes', label: '礼包码', icon: Present },
      { path: '/referral-settings', label: '分享奖励', icon: Share },
      { path: '/support-settings', label: '在线客服', icon: ChatDotRound },
      { path: '/schools', label: '学校模板', icon: Reading },
    ],
  },
]

const aiProvider = ref('openai')
const aiMock = ref(false)
const poolEnabled = ref(0)

async function refreshAiBadge() {
  try {
    const cfg = await adminFetch<{
      provider?: string
      mock?: boolean
      modelPools?: { enabled?: boolean }[]
    }>('/admin/ai-config')
    aiMock.value = false
    aiProvider.value = cfg.provider === 'mock' ? 'openai' : (cfg.provider ?? 'openai')
    poolEnabled.value = (cfg.modelPools ?? []).filter((p) => p.enabled !== false).length
  } catch {
    aiProvider.value = 'openai'
    aiMock.value = false
  }
}

onMounted(() => void refreshAiBadge())

watch(
  () => route.path,
  () => {
    if (route.path === '/ai-settings' || route.path === '/deploy') {
      void refreshAiBadge()
    }
  },
)

function logout() {
  setAdminToken(null)
  router.push('/login')
}
</script>

<template>
  <el-container class="admin-layout">
    <el-aside width="232px" class="sidebar">
      <div class="brand">
        <span class="brand-icon" :class="{ 'brand-icon--image': brandingStore.logoImageUrl.value }">
          <img
            v-if="brandingStore.logoImageUrl.value"
            :src="brandingStore.logoImageUrl.value"
            :alt="brandingStore.siteTitle.value"
          />
          <span v-else>{{ brandingStore.logoText.value }}</span>
        </span>
        <div>
          <div class="brand-title">{{ brandingStore.siteTitle.value }}</div>
          <div class="brand-sub">{{ brandingStore.slogan.value }} · 管理端</div>
        </div>
      </div>

      <nav class="nav">
        <div v-for="group in menuGroups" :key="group.title" class="nav-group">
          <div class="nav-group-title">{{ group.title }}</div>
          <router-link
            v-for="item in group.items"
            :key="item.path"
            :to="item.path"
            class="nav-item"
            :class="{ active: active === item.path || (item.path !== '/' && active.startsWith(item.path)) }"
          >
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.label }}</span>
          </router-link>
        </div>
      </nav>

      <div class="sidebar-foot">
        <el-tag :type="aiMock ? 'warning' : 'success'" size="small" effect="plain">
          {{ aiMock ? 'Mock' : providerLabel(aiProvider) }}
          <span v-if="!aiMock && poolEnabled"> · {{ poolEnabled }} 模型</span>
        </el-tag>
      </div>
    </el-aside>

    <el-container class="main-wrap">
      <el-header class="topbar" height="56px">
        <div class="topbar-title">{{ pageTitle }}</div>
        <div class="topbar-actions">
          <el-button :icon="Setting" circle text title="AI 配置" @click="router.push('/ai-settings')" />
          <el-button type="danger" link @click="logout">退出登录</el-button>
        </div>
      </el-header>
      <el-main class="admin-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.admin-layout {
  min-height: 100vh;
}
.sidebar {
  background: var(--admin-sidebar);
  color: #e2e8f0;
  display: flex;
  flex-direction: column;
  border-right: 1px solid #1e293b;
}
.brand {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 20px 18px 16px;
}
.brand-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: linear-gradient(135deg, #3b82f6, #6366f1);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  font-weight: 800;
  color: #fff;
  overflow: hidden;
  flex-shrink: 0;
}
.brand-icon--image {
  padding: 3px;
  background: #fff;
}
.brand-icon img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  display: block;
}
.brand-title {
  font-weight: 700;
  font-size: 15px;
  color: #f8fafc;
  max-width: 148px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.brand-sub {
  font-size: 11px;
  color: #94a3b8;
  margin-top: 2px;
  max-width: 148px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.nav {
  flex: 1;
  padding: 8px 10px;
  overflow-y: auto;
}
.nav-group {
  margin-bottom: 16px;
}
.nav-group-title {
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: #64748b;
  padding: 0 12px 8px;
}
.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  color: #cbd5e1;
  text-decoration: none;
  font-size: 14px;
  margin-bottom: 2px;
  transition: background 0.15s, color 0.15s;
}
.nav-item:hover {
  background: var(--admin-sidebar-hover);
  color: #f1f5f9;
}
.nav-item.active {
  background: linear-gradient(90deg, rgb(37 99 235 / 35%), transparent);
  color: #fff;
  font-weight: 600;
}
.sidebar-foot {
  padding: 14px 18px 18px;
  border-top: 1px solid #1e293b;
}
.main-wrap {
  background: var(--admin-bg);
}
.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid var(--admin-border);
  padding: 0 24px;
}
.topbar-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--admin-muted);
}
</style>
