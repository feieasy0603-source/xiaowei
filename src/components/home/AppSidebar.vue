<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { ArrowDown, Search } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import { illustrationKeyFor, preloadIllustration } from '@/mocks/processIllustrations'
import {
  PRODUCT_CATEGORY_LABELS,
  type ProductCategory,
  type ProductConfig,
} from '@/types/product'
import { useProductsStore } from '@/stores/products'
import { useAppStore } from '@/stores/app'

const STORAGE_KEY = 'xiaowei_sidebar_groups'

const appStore = useAppStore()
const productsStore = useProductsStore()
const router = useRouter()
const route = useRoute()

const search = ref('')
const isMobile = ref(false)

const categoryOrder: ProductCategory[] = [
  'writing',
  'check',
  'defense',
  'sci',
  'tools',
]

function loadExpanded(): Record<string, boolean> {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) return JSON.parse(raw) as Record<string, boolean>
  } catch {
    /* ignore */
  }
  return {
    writing: true,
    check: true,
    defense: false,
    sci: false,
    tools: false,
  }
}

const expanded = ref<Record<string, boolean>>(loadExpanded())

function saveExpanded() {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(expanded.value))
}

function toggleGroup(cat: ProductCategory) {
  expanded.value[cat] = !expanded.value[cat]
  saveExpanded()
}

const pinnedProducts = computed(() =>
  productsStore.list.filter((p) => p.pinned),
)

const filteredList = computed(() => {
  const q = search.value.trim().toLowerCase()
  if (!q) return productsStore.list
  return productsStore.list.filter((p) => p.label.toLowerCase().includes(q))
})

const groupedProducts = computed(() => {
  const groups: { category: ProductCategory; label: string; items: ProductConfig[] }[] =
    []
  for (const cat of categoryOrder) {
    const items = filteredList.value.filter((p) => (p.category ?? 'tools') === cat)
    if (items.length) {
      groups.push({
        category: cat,
        label: PRODUCT_CATEGORY_LABELS[cat],
        items,
      })
    }
  }
  return groups
})

const hasResults = computed(() => groupedProducts.value.length > 0)

function ensureActiveGroupOpen() {
  const active = productsStore.getProduct(appStore.activeMenuId)
  const cat = active.category ?? 'tools'
  if (!expanded.value[cat]) {
    expanded.value[cat] = true
    saveExpanded()
  }
}

watch(() => appStore.activeMenuId, ensureActiveGroupOpen, { immediate: true })

function checkMobile() {
  isMobile.value = window.matchMedia('(max-width: 900px)').matches
}

if (typeof window !== 'undefined') {
  checkMobile()
  window.addEventListener('resize', checkMobile)
}

onUnmounted(() => {
  if (typeof window !== 'undefined') {
    window.removeEventListener('resize', checkMobile)
  }
})

function goOrders() {
  router.push({ name: 'orders', query: { ...route.query } })
}

function goAccount() {
  if (!appStore.isLoggedIn) {
    appStore.openLogin()
    return
  }
  router.push({ name: 'account', query: { ...route.query } })
}

function selectMenu(id: string, variant: ProductConfig['processVariant']) {
  const key = illustrationKeyFor(variant, id)
  if (key) preloadIllustration(key)

  appStore.setActiveMenu(id)
  appStore.closeProEdition()
  appStore.closeWizard()
  const q = { ...route.query }
  delete q.pro
  delete q.wizard
  router.push({
    name: 'intelligentWriting',
    params: { step: '0' },
    query: q,
  })
}

</script>

<template>
  <aside class="sidebar" :class="{ mobile: isMobile }">
    <button
      type="button"
      class="query-btn"
      :class="{ active: route.name === 'orders' }"
      @click="goOrders"
    >
      <el-icon><Search /></el-icon>
      查询结果
    </button>

    <button
      type="button"
      class="query-btn account-btn"
      :class="{ active: route.name === 'account' }"
      @click="goAccount"
    >
      用户中心
    </button>

    <div class="search-wrap">
      <el-input
        v-model="search"
        placeholder="搜索工具…"
        clearable
        size="small"
        :prefix-icon="Search"
      />
    </div>

    <div v-if="!search.trim() && pinnedProducts.length" class="pinned-block">
      <p class="section-label">常用</p>
      <div class="pinned-row">
        <button
          v-for="item in pinnedProducts"
          :key="item.id"
          type="button"
          class="pinned-chip"
          :class="{ active: route.name !== 'orders' && appStore.activeMenuId === item.id }"
          @click="selectMenu(item.id, item.processVariant)"
        >
          <span>{{ item.icon }}</span>
          {{ item.label }}
        </button>
      </div>
    </div>

    <nav v-if="!isMobile" class="menu">
      <p v-if="!hasResults" class="empty-hint">未找到匹配的工具</p>
      <div v-for="group in groupedProducts" :key="group.category" class="menu-group">
        <button type="button" class="group-head" @click="toggleGroup(group.category)">
          <span>{{ group.label }}</span>
          <el-icon class="chevron" :class="{ open: expanded[group.category] }">
            <ArrowDown />
          </el-icon>
        </button>
        <div v-show="expanded[group.category]" class="group-items">
          <button
            v-for="item in group.items"
            :key="item.id"
            type="button"
            class="menu-item"
            :class="{
              active: route.name !== 'orders' && appStore.activeMenuId === item.id,
            }"
            @click="selectMenu(item.id, item.processVariant)"
          >
            <span class="menu-icon">{{ item.icon }}</span>
            <span class="menu-label">{{ item.label }}</span>
            <span v-if="item.badge" class="menu-badge">{{ item.badge }}</span>
          </button>
        </div>
      </div>
    </nav>

    <nav v-else class="menu mobile-menu">
      <p v-if="!hasResults" class="empty-hint">未找到匹配的工具</p>
      <template v-for="group in groupedProducts" :key="group.category">
        <p class="mobile-group-label">{{ group.label }}</p>
        <div class="mobile-scroll">
          <button
            v-for="item in group.items"
            :key="item.id"
            type="button"
            class="menu-item mobile-chip"
            :class="{
              active: route.name !== 'orders' && appStore.activeMenuId === item.id,
            }"
            @click="selectMenu(item.id, item.processVariant)"
          >
            <span class="menu-icon">{{ item.icon }}</span>
            <span class="menu-label">{{ item.label }}</span>
            <span v-if="item.badge" class="menu-badge">{{ item.badge }}</span>
          </button>
        </div>
      </template>
    </nav>
  </aside>
</template>

<style scoped>
.sidebar {
  width: var(--xw-sidebar-width);
  flex-shrink: 0;
  background: #fff;
  border-right: 1px solid var(--xw-border);
  padding: 12px 0 16px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  max-height: calc(100vh - var(--xw-topbar-height));
}

.query-btn {
  margin: 0 10px 8px;
  padding: 9px 12px;
  border: 1px solid #dbeafe;
  border-radius: 10px;
  background: #f8fafc;
  color: var(--xw-text-secondary);
  font-size: 13px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  transition: all 0.15s;
  flex-shrink: 0;
}

.query-btn:hover {
  background: #eff6ff;
  border-color: #bfdbfe;
}

.query-btn.active {
  background: #eef2ff;
  color: #4f46e5;
  border-color: #c7d2fe;
  font-weight: 600;
}

.account-btn {
  margin-top: -4px;
}

.search-wrap {
  padding: 0 10px 10px;
  flex-shrink: 0;
}

.section-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--xw-muted);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  padding: 0 12px 6px;
}

.pinned-block {
  flex-shrink: 0;
  margin-bottom: 8px;
}

.pinned-row {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 0 8px 8px;
}

.pinned-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border: 1px solid var(--xw-border);
  border-radius: 8px;
  background: #fafbfc;
  font-size: 12px;
  color: var(--xw-text-secondary);
  cursor: pointer;
  text-align: left;
  transition: all 0.12s;
}

.pinned-chip:hover {
  border-color: #bfdbfe;
  background: #f0f9ff;
}

.pinned-chip.active {
  border-color: #93c5fd;
  background: #eff6ff;
  color: #0369a1;
  font-weight: 600;
}

.menu {
  flex: 1;
  overflow-y: auto;
  padding: 0 6px;
  min-height: 0;
}

.menu-group {
  margin-bottom: 4px;
}

.group-head {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 10px;
  border: none;
  background: transparent;
  font-size: 12px;
  font-weight: 700;
  color: var(--xw-muted);
  cursor: pointer;
  border-radius: 6px;
}

.group-head:hover {
  background: #f8fafc;
  color: var(--xw-text-secondary);
}

.chevron {
  transition: transform 0.15s;
  font-size: 14px;
}

.chevron.open {
  transform: rotate(180deg);
}

.group-items {
  padding-bottom: 4px;
}

.menu-item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  margin-bottom: 1px;
  border: none;
  border-radius: 8px;
  background: transparent;
  cursor: pointer;
  font-size: 13px;
  color: var(--xw-text-secondary);
  text-align: left;
  transition: all 0.12s;
}

.menu-item:hover {
  background: #f8fafc;
  color: var(--xw-text);
}

.menu-item.active {
  background: linear-gradient(90deg, #e0f2fe, #eff6ff);
  color: #0369a1;
  font-weight: 600;
  box-shadow: inset 3px 0 0 #3b82f6;
}

.menu-icon {
  font-size: 15px;
  width: 22px;
  text-align: center;
  flex-shrink: 0;
}

.menu-label {
  flex: 1;
  line-height: 1.35;
}

.menu-badge {
  font-size: 10px;
  background: linear-gradient(135deg, #ef4444, #dc2626);
  color: #fff;
  padding: 1px 6px;
  border-radius: 4px;
  font-weight: 700;
  flex-shrink: 0;
}

.empty-hint {
  font-size: 12px;
  color: var(--xw-muted);
  text-align: center;
  padding: 16px 8px;
}

.mobile-group-label {
  font-size: 11px;
  font-weight: 700;
  color: var(--xw-muted);
  padding: 8px 10px 4px;
}

.mobile-scroll {
  display: flex;
  gap: 6px;
  overflow-x: auto;
  padding: 0 8px 10px;
  scrollbar-width: thin;
}

.mobile-chip {
  width: auto;
  flex-shrink: 0;
  white-space: nowrap;
  box-shadow: none;
}

.mobile-chip.active {
  box-shadow: none;
}

@media (max-width: 900px) {
  .sidebar {
    width: 100%;
    max-height: none;
    border-right: none;
    border-bottom: 1px solid var(--xw-border);
  }

  .pinned-row {
    flex-direction: row;
    flex-wrap: nowrap;
    overflow-x: auto;
  }

  .pinned-chip {
    flex-shrink: 0;
    white-space: nowrap;
  }

  .menu.mobile-menu {
    overflow: visible;
  }
}
</style>
