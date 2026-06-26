<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AdminPageHeader from '@/components/AdminPageHeader.vue'
import { adminFetch } from '@/api/http'
import { TASK_TYPES } from '@/constants/ai'

const router = useRouter()
const stats = ref<Record<string, unknown>>({})
const dashboardError = ref('')
const deployReady = ref<boolean | null>(null)
const deployWarn = ref('')

onMounted(async () => {
  try {
    stats.value = await adminFetch('/admin/dashboard')
  } catch (e) {
    dashboardError.value = (e as Error).message || '加载看板失败'
  }
  try {
    const r = await adminFetch<{ productionReady?: boolean; checks?: { status: string; label: string }[] }>(
      '/admin/deploy/readiness',
    )
    deployReady.value = r.productionReady === true
    if (!r.productionReady) {
      const fails = (r.checks ?? []).filter((c) => c.status === 'fail').map((c) => c.label)
      deployWarn.value = fails.length ? fails.slice(0, 3).join('、') : 'AI Mock 或密钥未就绪'
    }
  } catch {
    deployReady.value = null
  }
})

const jobByType = computed(() => {
  const raw = stats.value.jobsByTaskType as Record<string, number> | undefined
  if (!raw) return []
  return TASK_TYPES.map((t) => ({
    ...t,
    count: raw[t.value] ?? 0,
  })).filter((x) => x.count > 0)
})

const successRate = computed(() => {
  const ok = Number(stats.value.jobsSuccess ?? 0)
  const fail = Number(stats.value.jobsFailed ?? 0)
  const total = ok + fail
  if (!total) return '—'
  return `${((ok / total) * 100).toFixed(1)}%`
})

const aiTokenStats = computed(() => {
  return (stats.value.aiTokenStats as {
    totalTokens?: number
    promptTokens?: number
    completionTokens?: number
    requestCount?: number
    models?: {
      label: string
      provider: string
      modelName: string
      totalTokens: number
      promptTokens: number
      completionTokens: number
      requestCount: number
    }[]
  }) ?? { models: [] }
})

const tokenModels = computed(() => aiTokenStats.value.models ?? [])

const maxModelTokens = computed(() => {
  const list = tokenModels.value
  if (!list.length) return 1
  return Math.max(1, ...list.map((m) => Number(m.totalTokens ?? 0)))
})

function formatTokens(n?: number) {
  if (n == null) return '0'
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(2)}M`
  if (n >= 1000) return `${(n / 1000).toFixed(1)}K`
  return String(n)
}
</script>

<template>
  <div>
    <AdminPageHeader
      title="AI 运营看板"
      desc="监控自动生成任务、草稿与商业数据，对应用户端智能写作全流程"
    >
      <template #actions>
        <el-button type="primary" plain @click="router.push('/deploy')">生产部署向导</el-button>
      </template>
    </AdminPageHeader>

    <el-alert v-if="dashboardError" type="error" :title="dashboardError" show-icon class="deploy-alert" />

    <el-alert
      v-if="deployReady === false"
      type="warning"
      show-icon
      :closable="false"
      title="尚未达到生产就绪"
      :description="`${deployWarn}。请打开生产部署向导完成配置。`"
      class="deploy-alert"
      @click="router.push('/deploy')"
    />

    <div class="admin-stat-grid">
      <div class="admin-stat-card accent">
        <div class="label">AI 任务总数</div>
        <div class="value">{{ stats.jobCount ?? 0 }}</div>
      </div>
      <div class="admin-stat-card">
        <div class="label">生成中</div>
        <div class="value" style="color: #d97706">{{ stats.jobsRunning ?? 0 }}</div>
      </div>
      <div class="admin-stat-card">
        <div class="label">已完成</div>
        <div class="value" style="color: #059669">{{ stats.jobsSuccess ?? 0 }}</div>
      </div>
      <div class="admin-stat-card">
        <div class="label">失败</div>
        <div class="value" style="color: #dc2626">{{ stats.jobsFailed ?? 0 }}</div>
      </div>
      <div class="admin-stat-card">
        <div class="label">任务成功率</div>
        <div class="value small">{{ successRate }}</div>
      </div>
      <div class="admin-stat-card">
        <div class="label">论文草稿</div>
        <div class="value">{{ stats.paperCount ?? 0 }}</div>
      </div>
    </div>

    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="14">
        <div class="admin-card">
          <h3 class="card-h3">按任务类型分布</h3>
          <p class="card-desc">对应用户端产品 taskType：论文生成、改稿、降重、PPT 等</p>
          <div v-if="jobByType.length" class="type-bars">
            <div v-for="item in jobByType" :key="item.value" class="type-row">
              <span class="type-name">{{ item.label }}</span>
              <el-progress
                :percentage="Math.min(100, (item.count / Number(stats.jobCount || 1)) * 100)"
                :color="item.color"
                :stroke-width="10"
                :show-text="false"
                style="flex: 1"
              />
              <span class="type-count">{{ item.count }}</span>
            </div>
          </div>
          <el-empty v-else description="暂无任务数据" :image-size="80" />
        </div>
      </el-col>
      <el-col :span="10">
        <div class="admin-card">
          <h3 class="card-h3">商业概览</h3>
          <el-descriptions :column="1" border size="small" style="margin-top: 12px">
            <el-descriptions-item label="注册用户">{{ stats.userCount ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="订单总数">{{ stats.orderCount ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="已支付订单">{{ stats.paidOrderCount ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="已支付金额">
              ¥{{ Number(stats.paidOrderAmount ?? 0).toFixed(2) }}
            </el-descriptions-item>
            <el-descriptions-item label="用户余额合计">
              ¥{{ Number(stats.totalBalance ?? 0).toFixed(2) }}
            </el-descriptions-item>
            <el-descriptions-item label="AI 产品数">{{ stats.productCount ?? 0 }}</el-descriptions-item>
          </el-descriptions>
        </div>
      </el-col>
    </el-row>

    <div class="admin-card" style="margin-top: 16px">
      <div class="token-head">
        <div>
          <h3 class="card-h3">各模型 Token 消耗</h3>
          <p class="card-desc">
            线程池已启用 {{ stats.aiPoolEnabledCount ?? 0 }} 个模型 · 策略 {{ stats.aiPoolStrategy ?? 'round_robin' }}
          </p>
        </div>
        <div class="token-totals">
          <span>累计 <strong>{{ formatTokens(aiTokenStats.totalTokens) }}</strong></span>
          <span>Prompt {{ formatTokens(aiTokenStats.promptTokens) }}</span>
          <span>Completion {{ formatTokens(aiTokenStats.completionTokens) }}</span>
          <span>{{ aiTokenStats.requestCount ?? 0 }} 次调用</span>
        </div>
      </div>
      <el-table v-if="tokenModels.length" :data="tokenModels" size="small" stripe>
        <el-table-column prop="label" label="接入点" width="120" />
        <el-table-column label="运营商" width="100">
          <template #default="{ row }">{{ row.provider }}</template>
        </el-table-column>
        <el-table-column prop="modelName" label="模型" min-width="140" />
        <el-table-column label="请求" width="72" align="right">
          <template #default="{ row }">{{ row.requestCount }}</template>
        </el-table-column>
        <el-table-column label="Prompt" width="96" align="right">
          <template #default="{ row }">{{ formatTokens(row.promptTokens) }}</template>
        </el-table-column>
        <el-table-column label="Completion" width="108" align="right">
          <template #default="{ row }">{{ formatTokens(row.completionTokens) }}</template>
        </el-table-column>
        <el-table-column label="总 Token" width="96" align="right">
          <template #default="{ row }">
            <strong>{{ formatTokens(row.totalTokens) }}</strong>
          </template>
        </el-table-column>
        <el-table-column label="占比" min-width="160">
          <template #default="{ row }">
            <el-progress
              :percentage="Math.min(100, (Number(row.totalTokens) / maxModelTokens) * 100)"
              :stroke-width="10"
              :show-text="false"
            />
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="暂无 Token 统计，发起真实模型生成后将自动累计" :image-size="72" />
      <el-button type="primary" link style="margin-top: 8px" @click="router.push('/ai-settings')">
        管理模型线程池
      </el-button>
    </div>

    <div class="admin-card" style="margin-top: 16px">
      <h3 class="card-h3">快捷操作</h3>
      <div class="quick-btns">
        <el-button type="primary" @click="router.push('/jobs')">查看生成任务</el-button>
        <el-button @click="router.push('/papers')">论文草稿</el-button>
        <el-button @click="router.push('/literature')">维护文献库</el-button>
        <el-button @click="router.push('/outlines')">维护提纲模板</el-button>
        <el-button @click="router.push('/products')">AI 产品配置</el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.card-h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}
.card-desc {
  margin: 4px 0 12px;
  font-size: 12px;
  color: var(--admin-muted);
}
.type-bars {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.type-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.type-name {
  width: 88px;
  font-size: 13px;
  flex-shrink: 0;
}
.type-count {
  width: 36px;
  text-align: right;
  font-weight: 600;
  font-size: 13px;
}
.quick-btns {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 12px;
}
.token-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.token-totals {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 13px;
  color: var(--admin-muted);
}
.token-totals strong {
  color: #4f46e5;
  font-size: 16px;
}
.deploy-alert {
  margin-bottom: 16px;
  cursor: pointer;
}
</style>
