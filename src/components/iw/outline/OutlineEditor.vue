<script setup lang="ts">
import { computed, ref } from 'vue'
import { nanoid } from 'nanoid'
import type { OutlineNode } from '@/types/paper'
import {
  adjustLevel,
  formatOutlineLabel,
} from '@/composables/useOutlineNumbering'

const props = defineProps<{
  modelValue: OutlineNode[]
}>()

const emit = defineEmits<{
  'update:modelValue': [v: OutlineNode[]]
}>()

const activeIndex = ref(0)

const numbered = computed(() =>
  props.modelValue.map((n, i) => ({
    node: n,
    label: formatOutlineLabel(props.modelValue, i),
  })),
)

function updateNode(index: number, patch: Partial<OutlineNode>) {
  const next = props.modelValue.map((n, i) =>
    i === index ? { ...n, ...patch } : n,
  )
  emit('update:modelValue', next)
}

function onKeydown(e: KeyboardEvent, index: number) {
  if (e.key === 'Tab') {
    e.preventDefault()
    const node = props.modelValue[index]
    if (!node) return
    updateNode(index, adjustLevel(node, e.shiftKey ? -1 : 1))
  }
}

function addNode() {
  emit('update:modelValue', [
    ...props.modelValue,
    {
      id: nanoid(8),
      level: 1,
      title: '新章节',
      wordCount: 400,
      zhRefs: 4,
      enRefs: 2,
    },
  ])
}

function removeNode(index: number) {
  emit(
    'update:modelValue',
    props.modelValue.filter((_, i) => i !== index),
  )
}
</script>

<template>
  <div class="outline-editor">
    <p class="xw-hint editor-hint">
      提纲编辑区：Tab 升级别，Shift+Tab 降级别。一级：第一章；二级：1.1；三级：1.1.1
    </p>

    <div
      v-for="(row, index) in numbered"
      :key="row.node.id"
      class="outline-row"
      :class="{ active: activeIndex === index }"
      @click="activeIndex = index"
    >
      <span class="level-tag">L{{ row.node.level }}</span>
      <span class="label-preview">{{ row.label }}</span>
      <el-input
        :model-value="row.node.title"
        class="title-input"
        @update:model-value="updateNode(index, { title: $event })"
        @keydown="onKeydown($event, index)"
      />
      <el-input-number
        :model-value="row.node.wordCount"
        :min="20"
        :max="5000"
        size="small"
        controls-position="right"
        @update:model-value="updateNode(index, { wordCount: $event ?? 400 })"
      />
      <el-input-number
        :model-value="row.node.zhRefs"
        :min="0"
        :max="20"
        size="small"
        controls-position="right"
        @update:model-value="updateNode(index, { zhRefs: $event ?? 4 })"
      />
      <el-input-number
        :model-value="row.node.enRefs"
        :min="0"
        :max="20"
        size="small"
        controls-position="right"
        @update:model-value="updateNode(index, { enRefs: $event ?? 2 })"
      />
      <el-button type="danger" link size="small" @click.stop="removeNode(index)">
        删除
      </el-button>
    </div>

    <el-button type="primary" plain @click="addNode">新增大纲</el-button>
  </div>
</template>

<style scoped>
.editor-hint {
  margin-bottom: 12px;
}

.outline-row {
  display: grid;
  grid-template-columns: 36px 1fr 1.2fr 90px 70px 70px auto;
  gap: 8px;
  align-items: center;
  padding: 8px;
  border-radius: 8px;
  margin-bottom: 6px;
  border: 1px solid transparent;
}

.outline-row.active {
  border-color: var(--xw-primary);
  background: #eef2ff;
}

.level-tag {
  font-size: 11px;
  background: #e5e7eb;
  padding: 2px 6px;
  border-radius: 4px;
  text-align: center;
}

.label-preview {
  font-size: 12px;
  color: var(--xw-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 900px) {
  .outline-row {
    grid-template-columns: 1fr;
  }

  .label-preview {
    display: none;
  }
}
</style>
