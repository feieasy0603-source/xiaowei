<script setup lang="ts">
import { computed, nextTick } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { generateOutline } from '@/api/modules/ai'
import { useApiEnabled } from '@/api/http'
import { requireLogin } from '@/composables/useRequireLogin'
import { useMockLoading } from '@/composables/useMockLoading'
import { usePaperRoute } from '@/composables/usePaperRoute'
import { outlineToText } from '@/composables/useOutlineNumbering'
import { usePaperStore } from '@/stores/paper'
import { buildOutlineTextFromTitle } from '@/utils/outlineFromTitle'

const paperStore = usePaperStore()
const { goToStep } = usePaperRoute()
const { run } = useMockLoading(1500)

const outlineText = computed({
  get: () => {
    const d = paperStore.draft
    if (!d) return ''
    if (d.outlineText?.trim()) return d.outlineText
    if (d.outline.length > 0) return outlineToText(d.outline)
    return ''
  },
  set: (v: string) => paperStore.setOutlineText(v),
})

async function pasteTemplate() {
  const title = paperStore.draft?.title?.trim() ?? ''
  if (title.length < 5) {
    ElMessage.warning('请先填写 5 字以上的论文标题')
    return
  }
  paperStore.clearOutline()

  if (useApiEnabled()) {
      if (!(await requireLogin())) return
    try {
      const text = await generateOutline(title, 2)
      if (text.trim()) {
        paperStore.setOutlineText(text.trim())
        ElMessage.success('已生成推荐提纲')
        return
      }
    } catch {
      /* 回退本地模板 */
    }
  }

  const text = buildOutlineTextFromTitle(title, 2, Date.now())
  paperStore.setOutlineText(text)
  ElMessage.success('已粘贴推荐提纲')
}

async function generateBody() {
  const text = outlineText.value.trim()
  if (text.length < 20) {
    ElMessage.warning('提纲至少 20 字，请完善后再生成正文')
    return
  }
  const title = paperStore.draft?.title?.trim() ?? ''
  if (title.length < 5) {
    ElMessage.warning('请先填写 5 字以上的论文标题')
    return
  }
  if (useApiEnabled() && !(await requireLogin())) return

  paperStore.setOutlineText(text)
  if (useApiEnabled() && !(await paperStore.persistDraft())) return

  if (!useApiEnabled()) {
    goToStep(3)
    await run(async () => {})
    ElMessage.success('正文生成中（离线预览）')
    return
  }

  goToStep(3, false, { autogen: true })
  ElMessage.success('已进入预览页，正在开始生成…')
}

function onOutlineKeydown(e: KeyboardEvent) {
  if (e.key !== 'Tab') return
  e.preventDefault()
  const ta = e.target as HTMLTextAreaElement
  const val = outlineText.value
  const pos = ta.selectionStart
  const lineStart = val.lastIndexOf('\n', pos - 1) + 1
  const lineEndRaw = val.indexOf('\n', pos)
  const lineEnd = lineEndRaw === -1 ? val.length : lineEndRaw
  const line = val.slice(lineStart, lineEnd)
  let newLine = line
  let cursorDelta = 0
  if (e.shiftKey) {
    const m = line.match(/^ {1,2}/)
    if (m) {
      newLine = line.slice(m[0].length)
      cursorDelta = -m[0].length
    }
  } else {
    newLine = `  ${line}`
    cursorDelta = 2
  }
  if (newLine === line) return
  const nextVal = val.slice(0, lineStart) + newLine + val.slice(lineEnd)
  outlineText.value = nextVal
  void nextTick(() => {
    const nextPos = Math.max(lineStart, pos + cursorDelta)
    ta.selectionStart = ta.selectionEnd = nextPos
  })
}
</script>

<template>
  <div class="outline-pro-panel xw-card">
    <div class="panel-head">
      <span class="bar">|</span>
      <span>提纲编辑区：</span>
    </div>
    <p class="hint">
      Tab 增加行首缩进，Shift + Tab 减少缩进（便于区分章节层级）
    </p>
    <el-input
      v-model="outlineText"
      type="textarea"
      :rows="14"
      placeholder="直接粘贴贴心仪提纲，或在右侧选择提纲"
      class="outline-textarea"
      @keydown="onOutlineKeydown"
    />
    <p class="word-hint">提纲字数范围：20-1000字（保存草稿后用于生成与支付）</p>
    <el-button
      type="primary"
      size="large"
      class="gen-btn"
      :disabled="!outlineText.trim()"
      @click="generateBody"
    >
      ai生成正文
    </el-button>
    <el-button text type="primary" class="paste-btn" @click="pasteTemplate">
      粘贴推荐提纲
    </el-button>
  </div>
</template>

<style scoped>
.outline-pro-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 520px;
  padding: 20px 24px;
}

.panel-head {
  font-size: 15px;
  font-weight: 600;
  color: #334155;
  margin-bottom: 8px;
}

.bar {
  color: #6366f1;
  margin-right: 4px;
  font-weight: 700;
}

.hint {
  font-size: 13px;
  color: #64748b;
  margin-bottom: 12px;
}

.outline-textarea :deep(textarea) {
  font-size: 14px;
  line-height: 1.7;
}

.word-hint {
  font-size: 12px;
  color: #94a3b8;
  margin: 10px 0 16px;
}

.gen-btn {
  width: 100%;
  height: 44px;
  border-radius: 8px;
  background: linear-gradient(90deg, #818cf8, #6366f1);
  border: none;
  font-size: 16px;
}

.paste-btn {
  margin-top: 8px;
  align-self: center;
}
</style>
