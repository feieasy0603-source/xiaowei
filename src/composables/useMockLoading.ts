import { ref } from 'vue'

export function useMockLoading(delayMs = 2000) {
  const loading = ref(false)

  async function run<T>(fn: () => T | Promise<T>): Promise<T> {
    loading.value = true
    try {
      await new Promise((r) => setTimeout(r, delayMs))
      return await fn()
    } finally {
      loading.value = false
    }
  }

  return { loading, run }
}
