<script setup lang="ts">
import { computed } from 'vue'
import type { ProcessVariant } from '@/types/product'

const props = defineProps<{
  variant: ProcessVariant
  label: string
}>()

const step2Title = computed(() => {
  const map: Record<ProcessVariant, string> = {
    standard: 'AI 原创范文',
    journal: 'AI 原创范文',
    revise: '不限次数：降重 + 润色 + 扩写',
    review: 'AI 原创综述',
    aigc: 'AIGC 检测说明报告',
    proposal: 'AI 原创报告',
    course: 'AI 原创范文',
    task: 'AI 原创任务书',
    paraphrase: '降重后的原创论文',
    ppt: 'AI 生成 PPT',
    upload: '智能处理结果',
  }
  return map[props.variant]
})

const wordTag = computed(() => {
  const map: Partial<Record<ProcessVariant, string>> = {
    standard: '8000–20000 字',
    journal: '10000 字',
    course: '4000–9000 字',
  }
  return map[props.variant]
})

const outlineItems = computed(() => {
  const map: Partial<Record<ProcessVariant, string[]>> = {
    review: ['一、引言', '二、综述报告', '研究方法 / 实验分析', '结论与展望'],
    proposal: ['一、选题背景与意义', '二、研究内容与方案', '研究方法 / 实验分析', '结论与展望'],
    task: ['一、任务概述', '二、任务目标', '研究方法 / 实验分析', '结论与展望'],
    standard: ['绪论 / 研究背景', '研究方法 / 实验分析', '结论与展望'],
    journal: ['绪论 / 研究背景', '研究方法 / 实验分析', '结论与展望'],
    course: ['绪论 / 研究背景', '研究方法 / 实验分析', '结论与展望'],
  }
  return map[props.variant] ?? ['绪论 / 研究背景', '研究方法 / 实验分析', '结论与展望']
})

const extras = computed(() => {
  switch (props.variant) {
    case 'proposal':
    case 'task':
    case 'course':
      return ['期刊文献 ×20', '参考文献 ×10', '学术论文 ×10']
    case 'review':
      return ['期刊文献 ×30', '图片表格 ×40', '写作思路 ×3', '致谢模版 ×5']
    default:
      return ['期刊文献 ×30', '图片表格 ×40', '写作思路 ×3', '致谢模板 ×5']
  }
})

const showCheckBadge = computed(() => !['task', 'aigc', 'paraphrase', 'upload'].includes(props.variant))
</script>

<template>
  <div class="flow-art">
    <div class="art-glow" aria-hidden="true" />


    <!-- AIGC -->
    <template v-if="variant === 'aigc'">
      <div class="step-block">
        <div class="step-badge">1</div>
        <div class="step-content">
          <p class="step-heading">只需上传论文</p>
        </div>
      </div>
      <div class="arrow-down">
        <span>立得</span>
        <svg width="14" height="14" viewBox="0 0 16 16" fill="none"><path d="M4 6l4 4 4-4" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>
      </div>
      <div class="step-block">
        <div class="step-badge">2</div>
        <div class="step-content">
          <p class="step-heading">{{ step2Title }}</p>
          <p class="step-sub">AI 生成检测说明，非知网/维普等第三方接口</p>
        </div>
      </div>
    </template>

    <!-- 降重 -->
    <template v-else-if="variant === 'paraphrase'">
      <div class="step-block">
        <div class="step-badge">1</div>
        <div class="step-content">
          <p class="step-heading">上传论文或报告原文</p>
          <p class="step-sub">支持 docx / pdf / txt，不解析第三方查重 PDF 结构</p>
        </div>
      </div>
      <div class="arrow-down">
        <span>立得</span>
        <svg width="14" height="14" viewBox="0 0 16 16" fill="none"><path d="M4 6l4 4 4-4" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>
      </div>
      <div class="step-block">
        <div class="step-badge">2</div>
        <div class="step-content">
          <p class="step-heading">{{ step2Title }}</p>
          <p class="step-sub">AI 改写润色，请自行用学校指定系统复核</p>
          <p class="file-tag">📄 降重结果.docx</p>
        </div>
      </div>
    </template>

    <!-- 上传类 -->
    <template v-else-if="variant === 'upload'">
      <div class="step-block">
        <div class="step-badge">1</div>
        <div class="step-content">
          <p class="step-heading">上传文件</p>
        </div>
      </div>
      <div class="arrow-down">
        <span>立得</span>
        <svg width="14" height="14" viewBox="0 0 16 16" fill="none"><path d="M4 6l4 4 4-4" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>
      </div>
      <div class="step-block">
        <div class="step-badge">2</div>
        <div class="step-content">
          <p class="step-heading">{{ step2Title }}</p>
          <p class="step-sub">处理完成后可下载结果文件</p>
        </div>
      </div>
    </template>

    <!-- 标题输入类（标准流程） -->
    <template v-else>
      <div class="step-block">
        <div class="step-badge">1</div>
        <div class="step-content">
          <p class="step-heading">只需输入论文标题</p>
        </div>
      </div>

      <div class="arrow-down">
        <span>立得</span>
        <svg width="14" height="14" viewBox="0 0 16 16" fill="none"><path d="M4 6l4 4 4-4" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>
      </div>

      <div class="step-block">
        <div class="step-badge">2</div>
        <div class="step-content">
          <p class="step-heading">
            {{ step2Title }}
            <span v-if="wordTag" class="word-chip">{{ wordTag }}</span>
          </p>
          <div class="outline-mock">
            <ul>
              <li v-for="item in outlineItems" :key="item">{{ item }}</li>
            </ul>
            <div v-if="showCheckBadge" class="check-ring-wrap">
              <span class="check-label">可选学校标注导出</span>
            </div>
          </div>
        </div>
      </div>

      <div class="arrow-plus">+</div>

      <div class="step-block">
        <div class="step-badge">3</div>
        <div class="step-content">
          <p class="step-heading">思路补充</p>
          <div class="tag-cloud">
            <span v-for="e in extras" :key="e" class="cloud-tag">{{ e }}</span>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.flow-art {
  position: relative;
  width: 100%;
  max-width: var(--xw-process-width);
  padding: 4px 4px 16px;
  min-height: 400px;
}

.art-glow {
  position: absolute;
  top: 40px;
  left: 50%;
  transform: translateX(-50%);
  width: 280px;
  height: 280px;
  background: radial-gradient(circle, rgb(59 130 246 / 8%) 0%, transparent 70%);
  pointer-events: none;
}

.step-block {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.step-badge {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: linear-gradient(145deg, #3b82f6, #2563eb);
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgb(37 99 235 / 30%);
}

.step-content {
  flex: 1;
  min-width: 0;
  padding-top: 4px;
}

.step-heading {
  font-size: 15px;
  font-weight: 600;
  color: #1e293b;
  line-height: 1.45;
  margin-bottom: 10px;
}

.step-sub {
  font-size: 13px;
  color: var(--xw-muted);
  margin: -6px 0 12px;
}

.word-chip {
  display: inline-block;
  margin-left: 6px;
  padding: 2px 8px;
  background: linear-gradient(90deg, #fef9c3, #fef08a);
  color: #a16207;
  font-size: 11px;
  font-weight: 600;
  border-radius: 4px;
  vertical-align: middle;
}

.arrow-down {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 6px 0 6px 8px;
  margin-left: 4px;
  color: var(--xw-muted);
  font-size: 12px;
  font-weight: 500;
}

.arrow-plus {
  text-align: center;
  width: 32px;
  margin: 6px 0;
  font-size: 20px;
  color: #cbd5e1;
  font-weight: 300;
}

.outline-mock {
  background: rgb(255 255 255 / 72%);
  backdrop-filter: blur(8px);
  border: 1px solid rgb(255 255 255 / 90%);
  border-radius: 14px;
  padding: 18px 16px;
  box-shadow: 0 8px 32px rgb(59 130 246 / 8%), 0 2px 8px rgb(15 23 42 / 4%);
  position: relative;
}

.outline-mock ul {
  list-style: none;
  font-size: 13px;
  color: #94a3b8;
  line-height: 2.1;
}

.outline-mock li {
  padding-left: 14px;
  position: relative;
}

.outline-mock li::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #cbd5e1;
}

.check-ring-wrap {
  position: absolute;
  right: 14px;
  bottom: 14px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.check-label {
  font-size: 11px;
  color: #64748b;
}

.check-ring {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  border: 2.5px solid #2563eb;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  color: #2563eb;
  background: #fff;
}

.tag-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.cloud-tag {
  padding: 7px 14px;
  background: rgb(255 255 255 / 65%);
  border: 1px solid rgb(226 232 240 / 80%);
  border-radius: 10px;
  font-size: 12px;
  color: #475569;
  box-shadow: var(--xw-shadow-sm);
}

.stat-pair {
  display: flex;
  gap: 10px;
}

.stat-box {
  flex: 1;
  background: rgb(255 255 255 / 72%);
  border: 1px solid rgb(255 255 255 / 90%);
  border-radius: 12px;
  padding: 16px;
  text-align: center;
  box-shadow: 0 4px 16px rgb(59 130 246 / 6%);
}

.stat-label {
  display: block;
  font-size: 12px;
  color: #64748b;
  margin-bottom: 8px;
}

.stat-val {
  font-size: 24px;
  font-weight: 800;
}

.rate-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: rgb(255 255 255 / 72%);
  border-radius: 10px;
  font-size: 13px;
  color: #64748b;
  box-shadow: var(--xw-shadow-sm);
}

.rate-bar.before {
  border-left: 3px solid #f87171;
}

.rate-bar.after {
  border-left: 3px solid #4ade80;
}

.file-tag {
  font-size: 12px;
  color: var(--xw-muted);
  margin-top: 10px;
}

.red { color: #ef4444; }
.green { color: #22c55e; }
.blue { color: #3b82f6; }
</style>
