<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import AdminPageHeader from '@/components/AdminPageHeader.vue'
import { adminFetch } from '@/api/http'
import { LLM_ROUTE_TASKS, TASK_TYPES } from '@/constants/ai'
import { AI_PROVIDERS, providerLabel } from '@/constants/ai-providers'

type HealthStatus = 'ok' | 'fail' | 'skip' | 'unknown' | 'checking'

interface ModelPoolEntry {
  id: string
  label: string
  enabled: boolean
  provider: string
  baseUrl: string
  modelName: string
  apiKey: string
  apiKeySet?: boolean
  maxConcurrency: number
  availablePermits?: number
  healthStatus?: HealthStatus
  healthMessage?: string
  healthCheckedAt?: string
  promptTokens?: number
  completionTokens?: number
  totalTokens?: number
  requestCount?: number
}

interface TokenStatsSummary {
  modelCount?: number
  promptTokens?: number
  completionTokens?: number
  totalTokens?: number
  requestCount?: number
}

interface ProbeResultRow {
  id: string
  label: string
  ok?: boolean
  healthStatus?: HealthStatus
  healthMessage?: string
  latencyMs?: number
  reply?: string
  message?: string
  totalTokens?: number
}

interface TestAllResult {
  ok: boolean
  successCount: number
  total: number
  latencyMs: number
  results: ProbeResultRow[]
}

const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const testingAll = ref(false)
const probing = ref(false)
const testAllResult = ref<TestAllResult | null>(null)

const isMock = ref(false)
const poolStrategy = ref('round_robin')
const globalConcurrencyMode = ref<'auto' | 'manual'>('auto')
const effectiveGlobalMax = ref(4)
const temperature = ref(0.7)
const timeoutMs = ref(120000)
const maxConcurrency = ref(4)
const slotAcquireTimeoutMs = ref(90000)
const modelPools = ref<ModelPoolEntry[]>([])
const tokenStats = ref<TokenStatsSummary>({})
const routeRows = ref<{ taskType: string; model: string }[]>([])
const endpointRouteRows = ref<{ taskType: string; endpointId: string }[]>([])
const taskTypeModels = ref<Record<string, string>>({})
const taskTypeEndpoints = ref<Record<string, string>>({})

let poolPollTimer: ReturnType<typeof setTimeout> | null = null

const poolActiveCount = computed(() => modelPools.value.filter((p) => p.enabled).length)

function newPoolId() {
  return `m-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

function syncRouteRows() {
  routeRows.value = LLM_ROUTE_TASKS.map((t) => ({
    taskType: t.value,
    model: taskTypeModels.value[t.value] ?? '',
  }))
  endpointRouteRows.value = LLM_ROUTE_TASKS.map((t) => ({
    taskType: t.value,
    endpointId: taskTypeEndpoints.value[t.value] ?? '',
  }))
}

function buildTaskTypeEndpoints(): Record<string, string> {
  const m: Record<string, string> = {}
  for (const row of endpointRouteRows.value) {
    if (row.endpointId.trim()) m[row.taskType] = row.endpointId.trim()
  }
  return m
}

function buildTaskTypeModels(): Record<string, string> {
  const m: Record<string, string> = {}
  for (const row of routeRows.value) {
    if (row.model.trim()) m[row.taskType] = row.model.trim()
  }
  return m
}

function buildModelPoolsPayload() {
  return modelPools.value.map((p) => ({
    id: p.id,
    label: p.label,
    enabled: p.enabled,
    provider: p.provider,
    baseUrl: p.baseUrl,
    modelName: p.modelName,
    apiKey: p.apiKey,
    maxConcurrency: p.maxConcurrency,
  }))
}

function addEmptyModel() {
  const preset = AI_PROVIDERS.find((p) => p.id === 'deepseek') ?? AI_PROVIDERS[1]
  modelPools.value.push({
    id: newPoolId(),
    label: `模型 ${modelPools.value.length + 1}`,
    enabled: true,
    provider: preset.id,
    baseUrl: preset.defaultBaseUrl,
    modelName: preset.defaultModel,
    apiKey: '',
    maxConcurrency: 2,
    healthStatus: 'unknown',
    healthMessage: '',
  })
}

function addPresetModel(providerId: string) {
  const preset = AI_PROVIDERS.find((p) => p.id === providerId)
  if (!preset) return
  modelPools.value.push({
    id: newPoolId(),
    label: preset.label,
    enabled: true,
    provider: preset.id,
    baseUrl: preset.defaultBaseUrl,
    modelName: preset.defaultModel,
    apiKey: '',
    maxConcurrency: 2,
    healthStatus: 'unknown',
    healthMessage: '',
  })
}

function duplicateModel(row: ModelPoolEntry) {
  modelPools.value.push({
    ...row,
    id: newPoolId(),
    label: `${row.label} 副本`,
    apiKey: '',
    healthStatus: 'unknown',
    healthMessage: '',
  })
}

async function refreshPoolStatus() {
  if (isMock.value) return
  try {
    const st = await adminFetch<{
      endpoints?: { id: string; availablePermits?: number; maxConcurrency?: number }[]
      effectiveGlobalMax?: number
    }>('/admin/ai-config/pool-status')
    if (st.effectiveGlobalMax != null) effectiveGlobalMax.value = st.effectiveGlobalMax
    for (const ep of st.endpoints ?? []) {
      const row = modelPools.value.find((p) => p.id === ep.id)
      if (row && ep.availablePermits != null) row.availablePermits = ep.availablePermits
    }
  } catch {
    /* ignore poll errors */
  }
}

function startPoolPoll() {
  stopPoolPoll()
  if (isMock.value) return
  const tick = async () => {
    await refreshPoolStatus()
    if (!isMock.value) {
      poolPollTimer = setTimeout(() => void tick(), 8000)
    } else {
      poolPollTimer = null
    }
  }
  poolPollTimer = setTimeout(() => void tick(), 8000)
}

function stopPoolPoll() {
  if (poolPollTimer) {
    clearTimeout(poolPollTimer)
    poolPollTimer = null
  }
}

function healthLabel(status?: HealthStatus) {
  switch (status) {
    case 'ok':
      return '可用'
    case 'fail':
      return '不可用'
    case 'skip':
      return '跳过'
    case 'checking':
      return '检测中'
    default:
      return '未检测'
  }
}

function healthTagType(status?: HealthStatus): 'success' | 'danger' | 'info' | 'warning' {
  switch (status) {
    case 'ok':
      return 'success'
    case 'fail':
      return 'danger'
    case 'checking':
      return 'warning'
    case 'skip':
      return 'info'
    default:
      return 'info'
  }
}

function applyProbeResults(results: ProbeResultRow[]) {
  for (const r of results) {
    const row = modelPools.value.find((p) => p.id === r.id)
    if (!row) continue
    row.healthStatus = (r.healthStatus ?? (r.ok ? 'ok' : 'fail')) as HealthStatus
    row.healthMessage = r.healthMessage ?? r.message ?? r.reply ?? ''
  }
}

async function probeModels(silent = false) {
  if (isMock.value || modelPools.value.length === 0) return
  probing.value = true
  for (const row of modelPools.value) {
    if (row.enabled) row.healthStatus = 'checking'
  }
  try {
    const res = await adminFetch<TestAllResult>('/admin/ai-config/probe-all', { method: 'POST' })
    applyProbeResults(res.results ?? [])
    if (!silent && (res.total ?? 0) > 0) {
      if (res.ok) ElMessage.success(`全部可用 (${res.successCount}/${res.total})`)
      else ElMessage.warning(`部分不可用：${res.successCount}/${res.total} 可用`)
    }
  } catch (e) {
    if (!silent) ElMessage.error((e as Error).message)
  } finally {
    probing.value = false
  }
}

function onPoolProviderChange(row: ModelPoolEntry, id: string) {
  const preset = AI_PROVIDERS.find((p) => p.id === id)
  if (!preset) return
  row.provider = id
  if (id !== 'custom' && preset.defaultBaseUrl) {
    row.baseUrl = preset.defaultBaseUrl
  }
  if (preset.defaultModel) {
    row.modelName = preset.defaultModel
  }
}

function firstEnabledModel() {
  return modelPools.value.find((p) => p.enabled) ?? modelPools.value[0]
}

async function load() {
  loading.value = true
  try {
    const data = await adminFetch<{
      mock?: boolean
      provider?: string
      poolStrategy?: string
      temperature?: number
      timeoutMs?: number
      maxConcurrency?: number
      slotAcquireTimeoutMs?: number
      globalConcurrencyMode?: string
      effectiveGlobalMax?: number
      taskTypeModels?: Record<string, string>
      taskTypeEndpoints?: Record<string, string>
      modelPools?: ModelPoolEntry[]
      tokenStats?: TokenStatsSummary
    }>('/admin/ai-config')

    isMock.value = false
    poolStrategy.value = data.poolStrategy ?? 'round_robin'
    globalConcurrencyMode.value =
      data.globalConcurrencyMode === 'manual' ? 'manual' : 'auto'
    effectiveGlobalMax.value = data.effectiveGlobalMax ?? data.maxConcurrency ?? 4
    temperature.value = data.temperature ?? 0.7
    timeoutMs.value = data.timeoutMs ?? 120000
    maxConcurrency.value = data.maxConcurrency ?? 4
    slotAcquireTimeoutMs.value = data.slotAcquireTimeoutMs ?? 90000
    taskTypeModels.value = data.taskTypeModels ?? {}
    taskTypeEndpoints.value = data.taskTypeEndpoints ?? {}
    tokenStats.value = data.tokenStats ?? {}

    modelPools.value = (data.modelPools ?? []).map((p) => ({
      id: p.id || newPoolId(),
      label: p.label || '未命名',
      enabled: p.enabled !== false,
      provider: p.provider || 'deepseek',
      baseUrl: p.baseUrl ?? '',
      modelName: p.modelName ?? '',
      apiKey: '',
      apiKeySet: p.apiKeySet,
      maxConcurrency: p.maxConcurrency ?? 2,
      availablePermits: p.availablePermits,
      healthStatus: (p.healthStatus as HealthStatus) ?? 'unknown',
      healthMessage: p.healthMessage ?? '',
      healthCheckedAt: p.healthCheckedAt,
      promptTokens: p.promptTokens ?? 0,
      completionTokens: p.completionTokens ?? 0,
      totalTokens: p.totalTokens ?? 0,
      requestCount: p.requestCount ?? 0,
    }))

    syncRouteRows()
    testAllResult.value = null
    if (!isMock.value && modelPools.value.length > 0) {
      void probeModels(true)
      startPoolPoll()
    } else {
      stopPoolPoll()
    }
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  try {
    const first = firstEnabledModel()
    await adminFetch('/admin/ai-config', {
      method: 'PUT',
      body: JSON.stringify({
        mock: false,
        provider: first?.provider ?? 'openai',
        baseUrl: first?.baseUrl ?? '',
        modelName: first?.modelName ?? '',
        apiKey: first?.apiKey ?? '',
        timeoutMs: timeoutMs.value,
        maxConcurrency: maxConcurrency.value,
        globalConcurrencyMode: globalConcurrencyMode.value,
        slotAcquireTimeoutMs: slotAcquireTimeoutMs.value,
        temperature: temperature.value,
        poolStrategy: poolStrategy.value,
        taskTypeModels: buildTaskTypeModels(),
        taskTypeEndpoints: buildTaskTypeEndpoints(),
        modelPools: buildModelPoolsPayload(),
      }),
    })
    ElMessage.success('已保存')
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    saving.value = false
  }
}

async function resetAiConfig() {
  try {
    await adminFetch('/admin/ai-config/reset', { method: 'POST' })
    ElMessage.success('已重置为真实模型默认配置')
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

async function testAllModels() {
  if (isMock.value) {
    ElMessage.info('Mock 模式无需测试')
    return
  }
  if (!modelPools.value.some((p) => p.enabled)) {
    ElMessage.warning('请至少启用一个模型')
    return
  }
  testingAll.value = true
  testAllResult.value = null
  try {
    await save()
    testAllResult.value = await adminFetch<TestAllResult>('/admin/ai-config/test-all', { method: 'POST' })
    applyProbeResults(testAllResult.value.results ?? [])
    if (testAllResult.value.ok) {
      ElMessage.success(`全部通过 (${testAllResult.value.successCount}/${testAllResult.value.total})`)
    } else {
      ElMessage.warning(`部分失败：${testAllResult.value.successCount}/${testAllResult.value.total} 成功`)
    }
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    testingAll.value = false
  }
}

function taskLabel(v: string) {
  return LLM_ROUTE_TASKS.find((t) => t.value === v)?.label
    ?? TASK_TYPES.find((t) => t.value === v)?.label
    ?? v
}

function formatTokens(n?: number) {
  if (n == null) return '0'
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(2)}M`
  if (n >= 1000) return `${(n / 1000).toFixed(1)}K`
  return String(n)
}

onMounted(() => void load())
onUnmounted(stopPoolPoll)
</script>

<template>
  <div>
    <AdminPageHeader
      title="AI 模型线程池"
      desc="多模型并发调度：轮询/最少繁忙、任务绑定接入点、故障自动切换、健康探测跳过不可用节点"
    >
      <template #actions>
        <el-button @click="router.push('/deploy')">生产部署向导</el-button>
        <el-button type="warning" @click="resetAiConfig">重置配置</el-button>
      </template>
    </AdminPageHeader>

    <div v-loading="loading" class="admin-card panel global-panel">
      <div class="global-row">
        <el-form-item label="运行模式" label-width="88px" style="margin: 0">
          <el-tag type="success">真实模型池</el-tag>
        </el-form-item>
        <template v-if="!isMock">
          <el-form-item label="调度策略" label-width="88px" style="margin: 0">
            <el-select v-model="poolStrategy" style="width: 140px">
              <el-option label="轮询" value="round_robin" />
              <el-option label="最少繁忙" value="least_busy" />
            </el-select>
          </el-form-item>
          <el-form-item label="Temperature" label-width="100px" style="margin: 0">
            <el-input-number v-model="temperature" :min="0" :max="1" :step="0.1" size="small" />
          </el-form-item>
          <el-form-item label="超时(ms)" label-width="88px" style="margin: 0">
            <el-input-number v-model="timeoutMs" :min="5000" :step="1000" size="small" />
          </el-form-item>
          <el-form-item label="全局并发" label-width="88px" style="margin: 0">
            <el-select v-model="globalConcurrencyMode" size="small" style="width: 88px; margin-right: 8px">
              <el-option label="自动" value="auto" />
              <el-option label="手动" value="manual" />
            </el-select>
            <el-input-number
              v-model="maxConcurrency"
              :min="1"
              :max="64"
              size="small"
              :disabled="globalConcurrencyMode === 'auto'"
            />
            <span v-if="globalConcurrencyMode === 'auto'" class="effective-hint">
              有效 {{ effectiveGlobalMax }}
            </span>
          </el-form-item>
          <el-form-item label="槽位等待(ms)" label-width="108px" style="margin: 0">
            <el-input-number v-model="slotAcquireTimeoutMs" :min="3000" :step="5000" size="small" />
          </el-form-item>
        </template>
      </div>
      <div class="token-summary">
        <span>累计 Token：<strong>{{ formatTokens(tokenStats.totalTokens) }}</strong></span>
        <span>Prompt：{{ formatTokens(tokenStats.promptTokens) }}</span>
        <span>Completion：{{ formatTokens(tokenStats.completionTokens) }}</span>
        <span>请求：{{ tokenStats.requestCount ?? 0 }} 次</span>
        <span>已启用模型：{{ poolActiveCount }}</span>
      </div>
    </div>

    <div v-loading="loading" class="admin-card panel pool-panel">
      <div class="pool-toolbar">
        <h3 class="panel-title">模型线程池（不限数量）</h3>
        <div class="pool-btns">
          <el-dropdown split-button type="primary" plain @click="addEmptyModel">
            + 添加模型
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="addPresetModel('deepseek')">DeepSeek</el-dropdown-item>
                <el-dropdown-item @click="addPresetModel('qwen')">通义千问</el-dropdown-item>
                <el-dropdown-item @click="addPresetModel('moonshot')">Moonshot</el-dropdown-item>
                <el-dropdown-item @click="addPresetModel('openai')">OpenAI</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button type="primary" :loading="saving" @click="save">保存配置</el-button>
          <el-button type="success" plain :loading="probing" @click="probeModels()">检测可用性</el-button>
          <el-button type="success" :loading="testingAll" @click="testAllModels">一键测试全部</el-button>
          <el-button plain @click="refreshPoolStatus">刷新空闲槽</el-button>
        </div>
      </div>

      <el-table :data="modelPools" size="small" stripe border class="pool-table">
        <el-table-column label="状态" width="96" fixed="left" align="center">
          <template #default="{ row }">
            <el-tooltip
              v-if="row.healthMessage"
              :content="row.healthMessage"
              placement="top"
              :show-after="300"
            >
              <el-tag :type="healthTagType(row.healthStatus)" size="small">
                {{ healthLabel(row.healthStatus) }}
              </el-tag>
            </el-tooltip>
            <el-tag v-else :type="healthTagType(row.healthStatus)" size="small">
              {{ healthLabel(row.healthStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="启用" width="64" align="center" fixed="left">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="名称" width="110" fixed="left">
          <template #default="{ row }">
            <el-input v-model="row.label" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="运营商" width="128">
          <template #default="{ row }">
            <el-select
              v-model="row.provider"
              size="small"
              style="width: 100%"
              @change="(v: string) => onPoolProviderChange(row, v)"
            >
              <el-option
                v-for="p in AI_PROVIDERS"
                :key="p.id"
                :label="p.label"
                :value="p.id"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="Base URL" min-width="200">
          <template #default="{ row }">
            <el-input v-model="row.baseUrl" size="small" placeholder="https://api.deepseek.com" />
          </template>
        </el-table-column>
        <el-table-column label="模型" width="160">
          <template #default="{ row }">
            <el-input v-model="row.modelName" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="API Key" width="140">
          <template #default="{ row }">
            <el-input
              v-model="row.apiKey"
              size="small"
              type="password"
              show-password
              :placeholder="row.apiKeySet ? '已配置' : 'sk-...'"
            />
          </template>
        </el-table-column>
        <el-table-column label="并发" width="88">
          <template #default="{ row }">
            <el-input-number v-model="row.maxConcurrency" :min="1" :max="32" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="Token 消耗" width="120">
          <template #default="{ row }">
            <div class="token-cell">{{ formatTokens(row.totalTokens) }}</div>
            <div class="token-sub">{{ row.requestCount ?? 0 }} 次</div>
          </template>
        </el-table-column>
        <el-table-column label="空闲槽" width="72">
          <template #default="{ row }">
            <span v-if="row.availablePermits != null">{{ row.availablePermits }}/{{ row.maxConcurrency }}</span>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="duplicateModel(row)">复制</el-button>
            <el-button link type="danger" @click="modelPools = modelPools.filter((p) => p.id !== row.id)">
              删
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty
        v-if="!modelPools.length"
        description="点击「添加模型」配置第一个接入点，可继续添加任意多个"
        :image-size="80"
      />

      <el-table
        v-if="testAllResult?.results?.length"
        :data="testAllResult.results"
        size="small"
        stripe
        class="test-table"
      >
        <el-table-column prop="label" label="模型" width="120" />
        <el-table-column label="结果" width="80">
          <template #default="{ row }">
            <el-tag :type="row.ok ? 'success' : 'danger'" size="small">{{ row.ok ? '通过' : '失败' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="latencyMs" label="耗时(ms)" width="90" />
        <el-table-column label="Token" width="80">
          <template #default="{ row }">{{ row.totalTokens ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="详情" min-width="200">
          <template #default="{ row }">
            {{ row.reply || row.message || '—' }}
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div v-if="!isMock" class="admin-card panel route-panel">
      <h3 class="panel-title">任务路由（可选）</h3>
      <p class="route-hint">
        「绑定接入点」优先于调度策略；「覆盖模型名」仅改请求体中的 model 字段。探测为 fail 的节点会自动跳过。
      </p>
      <el-table :data="endpointRouteRows" size="small" stripe class="route-table">
        <el-table-column label="任务" width="120">
          <template #default="{ row }">{{ taskLabel(row.taskType) }}</template>
        </el-table-column>
        <el-table-column prop="taskType" width="140" />
        <el-table-column label="绑定接入点" min-width="180">
          <template #default="{ row }">
            <el-select v-model="row.endpointId" size="small" clearable placeholder="自动调度">
              <el-option
                v-for="p in modelPools.filter((x) => x.enabled)"
                :key="p.id"
                :label="`${p.label} (${p.id})`"
                :value="p.id"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="覆盖模型名" min-width="160">
          <template #default="{ row }">
            <el-input
              :model-value="routeRows.find((r) => r.taskType === row.taskType)?.model ?? ''"
              size="small"
              placeholder="留空用接入点默认"
              @update:model-value="(v: string) => {
                const r = routeRows.find((x) => x.taskType === row.taskType)
                if (r) r.model = v
              }"
            />
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<style scoped>
.global-panel {
  margin-bottom: 16px;
}
.global-row {
  display: flex;
  flex-wrap: wrap;
  gap: 16px 24px;
  align-items: center;
}
.token-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-top: 12px;
  font-size: 13px;
  color: var(--admin-muted);
}
.token-summary strong {
  color: #4f46e5;
  font-size: 15px;
}
.panel-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}
.pool-panel {
  margin-bottom: 16px;
}
.pool-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  gap: 12px;
}
.pool-btns {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.pool-table {
  width: 100%;
}
.token-cell {
  font-weight: 600;
  color: #4f46e5;
}
.token-sub {
  font-size: 11px;
  color: var(--admin-muted);
}
.test-table {
  margin-top: 16px;
}
.route-panel {
  margin-top: 0;
}
.route-hint {
  font-size: 12px;
  color: var(--admin-muted);
  margin: 0 0 12px;
}
.route-table {
  width: 100%;
}
.effective-hint {
  margin-left: 8px;
  font-size: 12px;
  color: var(--admin-muted);
}
</style>
