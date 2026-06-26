<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { CircleCheck, CircleClose, Warning } from '@element-plus/icons-vue'
import AdminPageHeader from '@/components/AdminPageHeader.vue'
import { adminFetch } from '@/api/http'

interface CheckItem {
  id: string
  label: string
  status: 'ok' | 'warn' | 'fail'
  message: string
  fix?: string
}

interface Readiness {
  checks: CheckItem[]
  passed: number
  warn: number
  total: number
  productionReady: boolean
  activeProfiles: string[]
  envTemplates: Record<string, string>
  nginxSnippet: string
}

const router = useRouter()
const loading = ref(false)
const applying = ref(false)
const activeStep = ref(0)
const data = ref<Readiness | null>(null)

const statusIcon = (s: string) => {
  if (s === 'ok') return CircleCheck
  if (s === 'fail') return CircleClose
  return Warning
}

const statusType = (s: string) => {
  if (s === 'ok') return 'success'
  if (s === 'fail') return 'danger'
  return 'warning'
}

const progressPercent = computed(() => {
  if (!data.value) return 0
  return Math.round((data.value.passed / data.value.total) * 100)
})

async function load() {
  loading.value = true
  try {
    data.value = await adminFetch<Readiness>('/admin/deploy/readiness')
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

async function disableAiMock() {
  applying.value = true
  try {
    const res = await adminFetch<{ message?: string }>('/admin/deploy/disable-ai-mock', {
      method: 'POST',
    })
    ElMessage.success(res.message ?? '已关闭 AI Mock')
    await load()
    activeStep.value = 0
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    applying.value = false
  }
}

async function disablePaymentMock() {
  applying.value = true
  try {
    const res = await adminFetch<{ message?: string; env?: string }>(
      '/admin/deploy/disable-payment-mock',
      { method: 'POST' },
    )
    if (res.env) {
      copyText(`export ${res.env}`, '支付 Mock 环境变量')
    }
    ElMessage.success(res.message ?? '已生成配置指引')
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    applying.value = false
  }
}

function copyText(text: string, label: string) {
  void navigator.clipboard.writeText(text.trim())
  ElMessage.success(`已复制：${label}`)
}

onMounted(() => void load())
</script>

<template>
  <div class="deploy-wizard">
    <AdminPageHeader
      title="生产部署向导"
      desc="按步骤关闭 Mock、核对环境变量，确保用户端走真实 API 与 LLM"
    >
      <template #actions>
        <el-button :loading="loading" @click="load">重新检测</el-button>
      </template>
    </AdminPageHeader>

    <el-alert
      v-if="data?.productionReady"
      type="success"
      :closable="false"
      show-icon
      title="生产就绪"
      description="核心项已通过。发布前请再次执行模型池「一键测试全部」。"
      class="top-alert"
    />
    <el-alert
      v-else-if="data"
      type="warning"
      :closable="false"
      show-icon
      title="尚未达到生产就绪"
      :description="`已通过 ${data.passed}/${data.total} 项，请按下方步骤处理 fail 项`"
      class="top-alert"
    />

    <div v-loading="loading" class="admin-card progress-card">
      <div class="progress-head">
        <span>检测进度</span>
        <span v-if="data" class="profiles">Profile: {{ data.activeProfiles.join(', ') || 'default' }}</span>
      </div>
      <el-progress
        v-if="data"
        :percentage="progressPercent"
        :status="data.productionReady ? 'success' : undefined"
      />
    </div>

    <el-steps :active="activeStep" finish-status="success" align-center class="steps">
      <el-step title="就绪检测" />
      <el-step title="后端环境" />
      <el-step title="前端构建" />
      <el-step title="关闭 AI Mock" />
      <el-step title="支付与安全" />
    </el-steps>

    <div v-if="data" class="step-body admin-card">
      <!-- Step 0 -->
      <div v-show="activeStep === 0">
        <h3 class="step-title">运行时就绪检测</h3>
        <el-table :data="data.checks" size="small" stripe>
          <el-table-column label="状态" width="72" align="center">
            <template #default="{ row }">
              <el-icon :class="'st-' + row.status">
                <component :is="statusIcon(row.status)" />
              </el-icon>
            </template>
          </el-table-column>
          <el-table-column prop="label" label="检查项" width="200" />
          <el-table-column prop="message" label="说明" min-width="220" />
          <el-table-column label="处理建议" min-width="200">
            <template #default="{ row }">
              <span v-if="row.fix" class="fix">{{ row.fix }}</span>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- Step 1 -->
      <div v-show="activeStep === 1">
        <h3 class="step-title">后端环境变量</h3>
        <p class="hint">
          使用 <code>SPRING_PROFILES_ACTIVE=prod,mysql</code> 启动后，
          <code>application-prod.yml</code> 会默认关闭 YAML 层 AI/支付 Mock。
          运行时 AI 仍以管理后台「AI 模型池」数据库配置为准。
        </p>
        <div class="copy-block">
          <div class="copy-head">
            <span>服务端 .env 模板</span>
            <el-button size="small" @click="copyText(data.envTemplates.server, '服务端环境')">
              复制
            </el-button>
          </div>
          <pre>{{ data.envTemplates.server }}</pre>
        </div>
        <div class="copy-block">
          <div class="copy-head">
            <span>Nginx 反代片段</span>
            <el-button size="small" @click="copyText(data.nginxSnippet, 'Nginx')">复制</el-button>
          </div>
          <pre>{{ data.nginxSnippet }}</pre>
        </div>
      </div>

      <!-- Step 2 -->
      <div v-show="activeStep === 2">
        <h3 class="step-title">前端构建（关闭离线演示）</h3>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="必须设置 VITE_USE_API=true"
          description="生产构建若未设置该变量，用户端会退化为离线演示（假提交、假订单）。根目录 .env.production 已包含推荐值。"
          style="margin-bottom: 12px"
        />
        <div class="copy-block">
          <div class="copy-head">
            <span>用户端 .env.production</span>
            <el-button size="small" @click="copyText(data.envTemplates.userFrontend, '用户端 env')">
              复制
            </el-button>
          </div>
          <pre>{{ data.envTemplates.userFrontend }}</pre>
        </div>
        <div class="copy-block">
          <div class="copy-head">
            <span>管理端 .env.production</span>
            <el-button size="small" @click="copyText(data.envTemplates.adminFrontend, '管理端 env')">
              复制
            </el-button>
          </div>
          <pre>{{ data.envTemplates.adminFrontend }}</pre>
        </div>
        <pre class="cmd">cd /path/xiaowei && npm run build
cd xiaowei-admin && npm run build</pre>
      </div>

      <!-- Step 3 -->
      <div v-show="activeStep === 3">
        <h3 class="step-title">关闭 AI Mock（运行时）</h3>
        <p class="hint">
          即使 YAML 已设 <code>mock: false</code>，若曾在后台保存过 Mock，数据库配置仍会覆盖。
          请确保模型池里至少有 1 个<strong>已启用且已填 API Key</strong> 的模型后再执行。
        </p>
        <el-space>
          <el-button type="primary" :loading="applying" @click="disableAiMock">
            一键关闭 AI Mock
          </el-button>
          <el-button @click="router.push('/ai-settings')">打开 AI 模型池</el-button>
        </el-space>
      </div>

      <!-- Step 4 -->
      <div v-show="activeStep === 4">
        <h3 class="step-title">支付与安全</h3>
        <ul class="checklist">
          <li>
            <el-tag :type="statusType(data.checks.find((c) => c.id === 'payment_mock')?.status ?? 'warn')" size="small">
              支付 Mock
            </el-tag>
            {{ data.checks.find((c) => c.id === 'payment_mock')?.message }}
          </li>
          <li>
            <el-tag :type="statusType(data.checks.find((c) => c.id === 'wechat_pay')?.status ?? 'warn')" size="small">
              微信支付
            </el-tag>
            {{ data.checks.find((c) => c.id === 'wechat_pay')?.message }}
          </li>
          <li>
            <el-tag :type="statusType(data.checks.find((c) => c.id === 'jwt_secret')?.status ?? 'warn')" size="small">
              JWT
            </el-tag>
            {{ data.checks.find((c) => c.id === 'jwt_secret')?.message }}
          </li>
        </ul>
        <p class="hint">
          微信回调 URL 需指向
          <code>https://你的域名/api/payments/callback/wechat</code>，请求头携带
          <code>X-Pay-Secret</code>（与 PAY_CALLBACK_SECRET 一致）。充值订单号以 WR 开头走余额充值逻辑。
        </p>
        <el-space style="margin-top: 12px">
          <el-button type="warning" :loading="applying" @click="disablePaymentMock">
            一键关闭支付 Mock（复制环境变量）
          </el-button>
        </el-space>
      </div>

      <div class="step-nav">
        <el-button :disabled="activeStep === 0" @click="activeStep--">上一步</el-button>
        <el-button v-if="activeStep < 4" type="primary" @click="activeStep++">下一步</el-button>
        <el-button v-else type="success" @click="load">完成并重新检测</el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.deploy-wizard {
  max-width: 960px;
}
.top-alert {
  margin-bottom: 16px;
}
.progress-card {
  margin-bottom: 20px;
  padding: 16px 20px;
}
.progress-head {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 13px;
}
.profiles {
  color: var(--admin-muted);
}
.steps {
  margin-bottom: 20px;
}
.step-body {
  padding: 20px 24px;
}
.step-title {
  margin: 0 0 12px;
  font-size: 16px;
  font-weight: 600;
}
.hint {
  font-size: 13px;
  color: var(--admin-muted);
  line-height: 1.6;
  margin-bottom: 12px;
}
.copy-block {
  margin-bottom: 16px;
  border: 1px solid var(--admin-border);
  border-radius: 8px;
  overflow: hidden;
}
.copy-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: #f8fafc;
  font-size: 13px;
  font-weight: 500;
}
pre {
  margin: 0;
  padding: 12px;
  font-size: 12px;
  line-height: 1.5;
  background: #1e293b;
  color: #e2e8f0;
  overflow-x: auto;
}
.cmd {
  background: #f1f5f9;
  color: #334155;
  padding: 12px;
  border-radius: 8px;
  font-size: 12px;
}
.step-nav {
  margin-top: 24px;
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}
.st-ok {
  color: #16a34a;
}
.st-warn {
  color: #d97706;
}
.st-fail {
  color: #dc2626;
}
.fix {
  font-size: 12px;
  color: #4f46e5;
}
.muted {
  color: var(--admin-muted);
}
.checklist {
  margin: 0 0 16px;
  padding-left: 0;
  list-style: none;
}
.checklist li {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
  font-size: 13px;
}
code {
  font-size: 12px;
  background: #f1f5f9;
  padding: 1px 4px;
  border-radius: 4px;
}
</style>
