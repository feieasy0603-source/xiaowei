<script setup lang="ts">
export interface EduCardOption {
  value: string
  title: string
  desc: string
  extra?: string
}

const model = defineModel<string>({ required: true })

const props = defineProps<{
  label?: string
  options: EduCardOption[]
  columns?: number
}>()

const gridCols = () => props.columns ?? 2
</script>

<template>
  <div class="edu-cards">
    <label v-if="label" class="section-label">{{ label }}</label>
    <div
      class="cards"
      :style="{ '--edu-cols': gridCols() }"
    >
      <button
        v-for="opt in options"
        :key="opt.value"
        type="button"
        class="card"
        :class="{ active: model === opt.value }"
        @click="model = opt.value"
      >
        <div class="card-body">
          <strong>{{ opt.title }}</strong>
          <p>{{ opt.desc }}</p>
          <span v-if="opt.extra" class="extra">{{ opt.extra }}</span>
        </div>
        <span v-if="model === opt.value" class="check" aria-hidden="true">✓</span>
      </button>
    </div>
  </div>
</template>

<style scoped>
.edu-cards {
  margin-bottom: 22px;
}

.section-label {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: var(--xw-text-secondary);
  margin-bottom: 12px;
}

.cards {
  display: grid;
  grid-template-columns: repeat(var(--edu-cols), minmax(0, 1fr));
  gap: 10px;
  align-items: stretch;
}

.card {
  position: relative;
  display: flex;
  flex-direction: column;
  text-align: left;
  padding: 14px 40px 14px 14px;
  min-height: 88px;
  border: 2px solid #e2e8f0;
  border-radius: 12px;
  background: #fff;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s, box-shadow 0.15s;
}

.card:hover {
  border-color: #93c5fd;
}

.card.active {
  border-color: var(--xw-primary);
  background: #eff6ff;
  box-shadow: 0 0 0 1px rgb(59 130 246 / 18%);
}

.card-body {
  flex: 1;
  min-width: 0;
}

.card strong {
  display: block;
  font-size: 15px;
  font-weight: 600;
  color: var(--xw-text);
  margin-bottom: 6px;
  line-height: 1.3;
}

.card p {
  font-size: 12px;
  color: var(--xw-text-secondary);
  line-height: 1.55;
  margin: 0;
}

.extra {
  display: inline-block;
  margin-top: 8px;
  font-size: 12px;
  color: var(--xw-primary);
  font-weight: 500;
}

.check {
  position: absolute;
  right: 12px;
  bottom: 12px;
  width: 22px;
  height: 22px;
  background: var(--xw-primary);
  color: #fff;
  border-radius: 50%;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
  box-shadow: 0 2px 6px rgb(37 99 235 / 25%);
}

@media (max-width: 900px) {
  .cards {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .cards {
    grid-template-columns: 1fr;
  }

  .card {
    min-height: auto;
    padding-right: 44px;
  }
}
</style>
