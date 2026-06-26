<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { UploadFilled } from '@element-plus/icons-vue'
import NavFooter from '@/components/iw/common/NavFooter.vue'
import CompactModelRow from '@/components/home/shared/compact/CompactModelRow.vue'
import { polishTitle, recommendTitles, parseProposal } from '@/api/modules/ai'
import { fetchFormOptions } from '@/api/modules/meta'
import { fetchSchools, type SchoolOption } from '@/api/modules/schools'
import { useApiEnabled } from '@/api/http'
import { requireLogin } from '@/composables/useRequireLogin'
import { categories as mockCategories, degrees as mockDegrees, paperTypes as mockPaperTypes } from '@/mocks/outlineTemplates'
import { schools as mockSchools } from '@/mocks/schools'
import { usePaperStore } from '@/stores/paper'
import { usePaperRoute } from '@/composables/usePaperRoute'
import { buildTitlesFromKeyword } from '@/utils/titleRecommend'
import type { UploadFile } from 'element-plus/es/components/upload/index.mjs'
import type { PaperDraft } from '@/types/paper'

const emit = defineEmits<{ next: [] }>()

const paperStore = usePaperStore()
const { goToStep, ensureLunwen } = usePaperRoute()
const polishLoading = ref(false)
const topicLoading = ref(false)
const proposalLoading = ref(false)
const schoolOptions = ref<SchoolOption[]>([...mockSchools])
const categoryOptions = ref<string[]>([...mockCategories])
const degreeOptions = ref<string[]>([...mockDegrees])
const paperTypeOptions = ref<string[]>([...mockPaperTypes])

onMounted(async () => {
  ensureLunwen()
  if (!useApiEnabled()) return
  try {
    const list = await fetchSchools()
    if (list.length) schoolOptions.value = list
  } catch {
    /* 保留 mock */
  }
  try {
    const opts = await fetchFormOptions()
    if (opts.categories?.length) categoryOptions.value = opts.categories
    if (opts.degrees?.length) degreeOptions.value = opts.degrees
    if (opts.paperTypes?.length) paperTypeOptions.value = opts.paperTypes
  } catch {
    /* 保留 mock */
  }
})

const draft = computed(() => paperStore.draft)
const researchLen = computed(() => draft.value?.researchNotes?.length ?? 0)

function syncMeta<K extends keyof PaperDraft['meta']>(
  key: K,
  val: PaperDraft['meta'][K],
) {
  const d = paperStore.draft
  if (!d) return
  paperStore.updateDraft({
    meta: { ...d.meta, [key]: val },
  })
}

async function onPolish() {
  if (!draft.value?.title.trim()) {
    ElMessage.warning('请先输入论文标题')
    return
  }
  polishLoading.value = true
  try {
    if (useApiEnabled()) {
      if (!(await requireLogin())) return
      const t = await polishTitle(draft.value!.title)
      paperStore.setTitle(t)
      ElMessage.success('标题润色完成')
      return
    }
    await new Promise((r) => setTimeout(r, 1500))
    paperStore.setTitle(`【润色】${draft.value!.title}`)
    ElMessage.success('标题润色完成')
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    polishLoading.value = false
  }
}

function validateTitle(): boolean {
  const title = draft.value?.title.trim() ?? ''
  if (title.length < 5) {
    ElMessage.warning('请输入 5-50 字的论文标题')
    return false
  }
  if (title.length > 50) {
    ElMessage.warning('标题不超过 50 字')
    return false
  }
  return true
}

async function onSmartTopic() {
  const keyword =
    draft.value?.title.trim() ||
    draft.value?.researchNotes.trim().slice(0, 80) ||
    ''
  if (keyword.length < 2) {
    ElMessage.warning('请先输入选题关键词或论文标题')
    return
  }

  topicLoading.value = true
  try {
    if (useApiEnabled()) {
      if (!(await requireLogin())) return
      const titles = await recommendTitles(keyword)
      if (titles.length > 0) {
        paperStore.setTitle(titles[0]!)
        ElMessage.success('已为您推荐选题')
        return
      }
    }
    await new Promise((r) => setTimeout(r, 900))
    const titles = buildTitlesFromKeyword(keyword)
    if (titles[0]) paperStore.setTitle(titles[0])
    ElMessage.success('已为您推荐选题')
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    topicLoading.value = false
  }
}

function appendProposalToNotes(summary: string) {
  const notes = paperStore.draft?.researchNotes ?? ''
  const marker = '【开题报告摘要】'
  if (notes.includes(marker)) return
  const addition = `\n\n${marker}\n${summary}`
  paperStore.updateDraft({
    researchNotes: (notes + addition).slice(0, 8000),
  })
}

async function onUploadChange(file: UploadFile) {
  if (!file.raw) return
  const ext = file.name.split('.').pop()?.toLowerCase()
  if (!['doc', 'docx', 'pdf', 'txt'].includes(ext ?? '')) {
    ElMessage.error('仅支持 docx/doc/pdf/txt')
    return
  }

  proposalLoading.value = true
  try {
    if (useApiEnabled()) {
      if (!(await requireLogin())) return
      paperStore.updateDraft({
        proposalFile: { name: file.name, size: file.size ?? 0 },
        proposalParsed: undefined,
      })
      const res = await parseProposal(file.raw)
      const summary = String(res.summary ?? '').trim()
      if (summary) {
        paperStore.updateDraft({ proposalParsed: summary })
        appendProposalToNotes(summary)
        ElMessage.success('开题报告解析完成')
        return
      }
    }

    paperStore.updateDraft({
      proposalFile: { name: file.name, size: file.size ?? 0 },
      proposalParsed: undefined,
    })
    await new Promise((r) => setTimeout(r, 800))
    const offlineSummary =
      '【离线】已记录开题报告，连接后端并登录后可自动解析研究背景与方法。'
    paperStore.updateDraft({ proposalParsed: offlineSummary })
    ElMessage.success('开题报告已上传')
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    proposalLoading.value = false
  }
}

function clearProposal() {
  paperStore.updateDraft({
    proposalFile: undefined,
    proposalParsed: undefined,
  })
}

async function goLiterature() {
  if (!validateTitle()) return
  if (useApiEnabled()) {
    if (!(await requireLogin())) return
    if (!(await paperStore.persistDraft())) return
  }
  goToStep(1)
}

async function validateAndNext() {
  if (!validateTitle()) return
  if (useApiEnabled()) {
    if (!(await requireLogin())) return
    if (!(await paperStore.persistDraft())) return
  }
  emit('next')
}
</script>

<template>
  <div v-if="!draft" class="step-title step-loading">
    <el-skeleton :rows="8" animated />
  </div>
  <div v-else class="step-title">
    <div class="grid">
      <section class="xw-card main-form">
        <h2 class="xw-section-title">输入完整的论文标题或选题关键词，获得更好的生成效果</h2>

        <div class="title-row">
          <el-input
            :model-value="draft.title"
            placeholder="请输入论文标题"
            size="large"
            @update:model-value="paperStore.setTitle($event)"
          />
          <el-button
            :loading="topicLoading"
            @click="onSmartTopic"
          >
            智能选题
          </el-button>
          <el-button
            :loading="polishLoading"
            type="primary"
            plain
            @click="onPolish"
          >
            {{ polishLoading ? '学术之星标题润色中' : '标题润色' }}
          </el-button>
        </div>

        <el-form label-width="88px" class="meta-form">
          <el-row :gutter="16">
            <el-col :xs="24" :sm="12">
              <el-form-item label="单位">
                <el-input
                  :model-value="draft.meta.unit"
                  @update:model-value="syncMeta('unit', $event)"
                />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12">
              <el-form-item label="职位">
                <el-input
                  :model-value="draft.meta.position"
                  @update:model-value="syncMeta('position', $event)"
                />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12">
              <el-form-item label="类型">
                <el-select
                  :model-value="draft.meta.paperType"
                  style="width: 100%"
                  @update:model-value="syncMeta('paperType', $event)"
                >
                  <el-option
                    v-for="t in paperTypeOptions"
                    :key="t"
                    :label="t"
                    :value="t"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12">
              <el-form-item label="字数">
                <el-input-number
                  :model-value="draft.meta.wordCount"
                  :min="3000"
                  :max="50000"
                  :step="1000"
                  style="width: 100%"
                  @update:model-value="syncMeta('wordCount', $event ?? 8000)"
                />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12">
              <el-form-item label="语言">
                <el-radio-group
                  :model-value="draft.meta.language"
                  @update:model-value="syncMeta('language', $event)"
                >
                  <el-radio value="zh">中文</el-radio>
                  <el-radio value="en">英文</el-radio>
                </el-radio-group>
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <CompactModelRow />
            </el-col>
            <el-col :xs="24" :sm="12">
              <el-form-item label="学历">
                <el-select
                  :model-value="draft.meta.degree"
                  style="width: 100%"
                  @update:model-value="syncMeta('degree', $event)"
                >
                  <el-option
                    v-for="d in degreeOptions"
                    :key="d"
                    :label="d"
                    :value="d"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="分类">
                <el-select
                  :model-value="draft.meta.category"
                  style="width: 100%"
                  @update:model-value="syncMeta('category', $event)"
                >
                  <el-option
                    v-for="c in categoryOptions"
                    :key="c"
                    :label="c"
                    :value="c"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="24">
              <el-form-item label="选择学校">
                <el-select
                  :model-value="draft.meta.schoolId"
                  placeholder="选择学校（用于导出标注）"
                  filterable
                  style="width: 100%"
                  @update:model-value="syncMeta('schoolId', $event)"
                >
                  <el-option
                    v-for="s in schoolOptions"
                    :key="s.id"
                    :label="s.name"
                    :value="s.id"
                  />
                </el-select>
              </el-form-item>
              <p class="xw-tip-orange">
                👈 找不到学校可联系客服添加；选定学校后导出 DOCX 会附带校名标注（非完整排版模板）
              </p>
            </el-col>
          </el-row>
        </el-form>

        <h3 class="sub-title">开题报告上传</h3>
        <el-upload
          drag
          :auto-upload="false"
          accept=".doc,.docx,.pdf,.txt"
          :show-file-list="false"
          :disabled="proposalLoading"
          @change="onUploadChange"
        >
          <el-icon class="upload-icon"><UploadFilled /></el-icon>
          <div v-if="proposalLoading">正在解析开题报告，请稍候…</div>
          <div v-else>选择开题报告文件（docx/doc/pdf/txt）</div>
        </el-upload>
        <div v-if="draft.proposalFile" class="proposal-info">
          <span>{{ draft.proposalFile.name }}</span>
          <el-button link type="danger" @click="clearProposal">清空</el-button>
        </div>
        <p v-if="draft.proposalParsed" class="xw-hint">{{ draft.proposalParsed }}</p>
        <p class="xw-hint">
          👈 上传后将自动提取研究背景、方法与文献综述要点，并写入研究思路供正文生成使用
        </p>

        <h3 class="sub-title">研究思路 / 研究内容 / 资料</h3>
        <el-input
          :model-value="draft.researchNotes"
          type="textarea"
          :rows="6"
          maxlength="8000"
          show-word-limit
          placeholder="自定义输入中英文文献时请使用 GB/T 7714-2015 格式引文"
          @update:model-value="paperStore.updateDraft({ researchNotes: $event })"
        />
        <p class="word-count">字数: {{ researchLen }}/8000</p>

        <div class="lit-actions">
          <el-button type="primary" plain @click="goLiterature">下一步：添加文献</el-button>
          <span class="lit-hint">文献检索与导出请在「文献」步骤完成</span>
        </div>
      </section>

      <aside class="xw-card side-hints">
        <h3>智能选题</h3>
        <p class="xw-hint">选题没思路？用智能选题免费选题，节省时间。</p>
        <h3>人机共创</h3>
        <p class="xw-hint">输入完整标题与研究方向，可大幅提高范文生成质量。</p>
      </aside>
    </div>

    <NavFooter :show-prev="false" show-next @next="validateAndNext" />
  </div>
</template>

<style scoped>
.grid {
  display: grid;
  grid-template-columns: 1fr 280px;
  gap: 20px;
}

@media (max-width: 1024px) {
  .grid {
    grid-template-columns: 1fr;
  }
}

.title-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 20px;
}

.title-row .el-input {
  flex: 1;
  min-width: 200px;
}

.meta-form {
  margin-top: 8px;
}

.sub-title {
  font-size: 15px;
  font-weight: 600;
  margin: 20px 0 12px;
}

.upload-icon {
  font-size: 40px;
  color: var(--xw-primary);
}

.proposal-info {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 8px;
  font-size: 14px;
}

.word-count {
  font-size: 13px;
  color: var(--xw-muted);
  margin-top: 4px;
}

.lit-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
}

.lit-hint {
  font-size: 13px;
  color: var(--xw-muted);
}

.side-hints h3 {
  font-size: 14px;
  margin-bottom: 8px;
  margin-top: 16px;
}

.side-hints h3:first-child {
  margin-top: 0;
}

.step-loading {
  padding: 24px;
  max-width: 900px;
}
</style>
