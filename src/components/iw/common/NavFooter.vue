<script setup lang="ts">
defineProps<{
  showPrev?: boolean
  showNext?: boolean
  nextLabel?: string
  nextLoading?: boolean
}>()

const emit = defineEmits<{
  prev: []
  next: []
}>()
</script>

<template>
  <footer class="nav-footer">
    <el-button v-if="showPrev" size="large" @click="emit('prev')">上一步</el-button>
    <div class="spacer" />
    <el-button
      v-if="showNext"
      type="primary"
      size="large"
      :loading="nextLoading"
      @click="emit('next')"
    >
      {{ nextLabel ?? '下一步' }}
    </el-button>
  </footer>
</template>

<style scoped>
.nav-footer {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid var(--xw-border);
}

.spacer {
  flex: 1;
}
</style>
