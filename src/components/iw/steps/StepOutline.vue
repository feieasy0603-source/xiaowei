<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import OutlineEditor from '@/components/iw/outline/OutlineEditor.vue'
import NavFooter from '@/components/iw/common/NavFooter.vue'
import { outlineToText } from '@/composables/useOutlineNumbering'
import { generateOutline } from '@/api/modules/ai'
import { useApiEnabled } from '@/api/http'
import { requireLogin } from '@/composables/useRequireLogin'
import {
  outlineTemplate2Level,
  outlineTemplate3Level,
} from '@/mocks/outlineTemplates'
import { usePaperStore } from '@/stores/paper'

const emit = defineEmits<{ prev: []; next: [] }>()

const paperStore = usePaperStore()
const regenLoading = ref(false)

const outline = computed({
  get: () => paperStore.draft?.outline ?? [],
  set: (v) => paperStore.setOutline(v),
})

const rules = [
  '规则1：大纲标题加上「国内研究现状/文献综述」或 (国内研究现状/文献综述) 表示中文文献综述法撰写',
  '规则2：大纲标题加上「国外研究现状」或 (国外研究现状) 表示外文文献综述法撰写',
  '规则3：一般章节要求，如 (举例说明相关的5个例子)、(需要用python代码说明算法) 等',
  '规则4：上级标题被标记成文献综述法撰写时，下级标题自动使用相应语言文献综述法撰写',
  '规则5：大纲标题带上 (章节要求) 在最后输出文档中不在标题上显示',
]

async function regen(depth: 2 | 3) {
  const title = paperStore.draft?.title?.trim()
  if (!title) {
    ElMessage.warning('请先在标题步骤填写论文标题')
    return
  }

  regenLoading.value = true
  try {
    if (useApiEnabled()) {
      if (!(await requireLogin())) return
      const text = await generateOutline(title, depth)
      if (text.trim()) {
        paperStore.setOutlineText(text.trim())
        ElMessage.success(`已重新生成 ${depth} 级大纲`)
        return
      }
      ElMessage.warning('未生成有效大纲，请重试')
      return
    }

    await new Promise((r) => setTimeout(r, 1200))
    paperStore.setOutline(
      depth === 2 ? [...outlineTemplate2Level] : [...outlineTemplate3Level],
    )
    ElMessage.success(`已重新生成 ${depth} 级大纲`)
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    regenLoading.value = false
  }
}

function copyOutline() {
  const text = outlineToText(outline.value)
  navigator.clipboard.writeText(text).then(() => {
    ElMessage.success('大纲已复制')
  })
}

async function validateAndNext() {
  const hasL1 = outline.value.some((n) => n.level === 1)
  if (!hasL1) {
    ElMessage.warning('请至少添加一个一级提纲')
    return
  }
  const litCount = paperStore.literature.length
  if (litCount < 4) {
    ElMessage.warning(`建议至少添加 4 篇文献（当前 ${litCount} 篇），继续可能影响生成质量`)
  }
  if (useApiEnabled()) {
    if (!(await requireLogin())) return
    if (!(await paperStore.persistDraft())) return
  }
  emit('next')
}
</script>

<template>
  <div class="step-outline">
    <section class="xw-card">
      <h2 class="xw-section-title">创建文章的大纲（输入大纲备注论文质量更好哦）</h2>

      <el-collapse>
        <el-collapse-item title="章节要求规则说明（点击展开）" name="rules">
          <ul class="rules">
            <li v-for="(r, i) in rules" :key="i">{{ r }}</li>
          </ul>
        </el-collapse-item>
      </el-collapse>

      <div class="toolbar">
        <el-button
          :loading="regenLoading"
          @click="regen(2)"
        >
          重新生成(2级大纲)
        </el-button>
        <el-button
          :loading="regenLoading"
          type="primary"
          plain
          @click="regen(3)"
        >
          重新生成(3级大纲)
        </el-button>
        <el-button @click="copyOutline">复制大纲</el-button>
        <el-dropdown>
          <el-button>推荐提纲 ▾</el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="regen(2)">推荐提纲(2级)</el-dropdown-item>
              <el-dropdown-item @click="regen(3)">推荐提纲(3级)</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>

      <p class="xw-hint ref-hint">1个✔ 最少 4 个文献 · 字数范围 20~5000 字</p>

      <OutlineEditor v-model="outline" />

      <p v-if="regenLoading" class="processing">
        学术之星提纲处理中…
      </p>
    </section>

    <NavFooter
      show-prev
      show-next
      @prev="emit('prev')"
      @next="validateAndNext"
    />
  </div>
</template>

<style scoped>
.rules {
  padding-left: 20px;
  font-size: 13px;
  color: var(--xw-muted);
  line-height: 1.8;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 16px 0;
}

.ref-hint {
  margin-bottom: 12px;
}

.processing {
  margin-top: 12px;
  color: var(--xw-primary);
  font-size: 14px;
}
</style>
