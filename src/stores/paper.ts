import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { ElMessageBox } from 'element-plus/es/components/message-box/index.mjs'
import { ApiError } from '@/api/http'
import { createPaper, fetchPaper, savePaper as apiSavePaper } from '@/api/modules/papers'
import { getToken, useApiEnabled } from '@/api/http'
import { useAppStore } from '@/stores/app'
import { outlineToText } from '@/composables/useOutlineNumbering'
import { parseOutlineText } from '@/composables/parseOutlineText'
import { applyPrefsToDraftMeta } from '@/composables/useUserPreferences'
import {
  createEmptyDraft,
  type LiteratureItem,
  type OutlineNode,
  type PaperDraft,
  type PaperPreview,
} from '@/types/paper'

const STORAGE_PREFIX = 'xiaowei_paper_'

function loadFromStorage(id: string): PaperDraft | null {
  try {
    const raw = localStorage.getItem(STORAGE_PREFIX + id)
    if (!raw) return null
    return JSON.parse(raw) as PaperDraft
  } catch {
    return null
  }
}

function saveToStorage(draft: PaperDraft) {
  localStorage.setItem(STORAGE_PREFIX + draft.id, JSON.stringify(draft))
}

function removeFromStorage(id: string) {
  localStorage.removeItem(STORAGE_PREFIX + id)
}

function hydrateLocal(id: string): PaperDraft {
  const stored = loadFromStorage(id)
  let next = stored ?? createEmptyDraft(id)
  if (!stored) {
    const applied = applyPrefsToDraftMeta(next.meta, next.model)
    next = { ...next, meta: applied.meta, model: applied.model ?? next.model }
    saveToStorage(next)
  }
  return next
}

function remoteTimestamp(draft: PaperDraft): number {
  if (!draft.updatedAt) return 0
  const t = new Date(draft.updatedAt).getTime()
  return Number.isFinite(t) ? t : 0
}

export const usePaperStore = defineStore('paper', () => {
  const currentId = ref<string | null>(null)
  const draft = ref<PaperDraft | null>(null)
  const syncing = ref(false)
  const serverSynced = ref(false)
  let syncingId: string | null = null
  let persistDebounceTimer: ReturnType<typeof setTimeout> | null = null
  const PERSIST_DEBOUNCE_MS = 800

  function touchLocalRevision() {
    if (!draft.value) return
    draft.value._localUpdatedAt = Date.now()
  }

  function buildApiPayload(d: PaperDraft): PaperDraft {
    const appStore = useAppStore()
    return {
      ...d,
      productId: appStore.activeMenuId,
      _localUpdatedAt: d._localUpdatedAt ?? Date.now(),
    }
  }

  function schedulePersistDraft() {
    if (!useApiEnabled() || !getToken()) return
    if (persistDebounceTimer) clearTimeout(persistDebounceTimer)
    persistDebounceTimer = setTimeout(() => {
      persistDebounceTimer = null
      void persistDraft()
    }, PERSIST_DEBOUNCE_MS)
  }

  const title = computed(() => draft.value?.title ?? '')
  const literature = computed(() => draft.value?.literature ?? [])
  const outline = computed(() => draft.value?.outline ?? [])

  function initPaper(id: string) {
    if (currentId.value === id && draft.value) {
      if (useApiEnabled() && getToken() && !serverSynced.value) {
        void pullFromApi(id)
      }
      return
    }

    currentId.value = id
    draft.value = hydrateLocal(id)
    serverSynced.value = false

    if (!useApiEnabled() || !getToken()) return
    void pullFromApi(id)
  }

  async function pullFromApi(id: string) {
    if (syncingId === id) return
    syncingId = id
    syncing.value = true
    const appStore = useAppStore()
    try {
      const remote = await fetchPaper(id)
      if (currentId.value !== id) return

      const local = draft.value
      const localAt = local?._localUpdatedAt ?? 0
      const remoteAt = remoteTimestamp(remote)

      if (!local || remoteAt > localAt) {
        draft.value = { ...remote, _localUpdatedAt: remoteAt || Date.now() }
        if (remote.productId) appStore.setActiveMenu(remote.productId)
      } else if (localAt > remoteAt) {
        touchLocalRevision()
        void persistDraft()
      } else {
        draft.value = { ...remote, _localUpdatedAt: Date.now() }
        if (remote.productId) appStore.setActiveMenu(remote.productId)
      }
      saveToStorage(draft.value!)
      serverSynced.value = true
    } catch (e) {
      if (!(e instanceof ApiError && e.code === 409)) {
        ElMessage.warning('草稿同步失败，当前显示本地版本')
      }
    } finally {
      syncing.value = false
      if (syncingId === id) syncingId = null
    }
  }

  async function resolveDraftConflict(): Promise<boolean> {
    if (!draft.value?.id) return false
    try {
      await ElMessageBox.confirm(
        '云端草稿已在其他窗口或设备更新。选择「使用云端」将覆盖本页未同步内容；选择「保留本地」将用当前编辑覆盖云端。',
        '草稿冲突',
        {
          confirmButtonText: '使用云端版本',
          cancelButtonText: '保留本地并覆盖',
          distinguishCancelAndClose: true,
          type: 'warning',
        },
      )
      const remote = await fetchPaper(draft.value.id)
      draft.value = { ...remote, _localUpdatedAt: Date.now() }
      saveToStorage(draft.value)
      serverSynced.value = true
      ElMessage.success('已加载云端草稿')
      return true
    } catch (action) {
      if (action !== 'cancel') return false
      try {
        const payload: PaperDraft = {
          ...buildApiPayload(draft.value!),
          _forceOverwrite: true,
        }
        const saved = await apiSavePaper(draft.value!.id, payload)
        draft.value = {
          ...saved,
          _localUpdatedAt: remoteTimestamp(saved) || Date.now(),
        }
        saveToStorage(draft.value)
        serverSynced.value = true
        ElMessage.success('已用本地版本覆盖云端草稿')
        return true
      } catch (e) {
        ElMessage.error((e as Error).message || '覆盖保存失败')
        return false
      }
    }
  }

  async function persistDraft(): Promise<boolean> {
    if (persistDebounceTimer) {
      clearTimeout(persistDebounceTimer)
      persistDebounceTimer = null
    }
    if (!draft.value) return false
    touchLocalRevision()
    saveToStorage(draft.value)
    if (!useApiEnabled()) return true
    if (!getToken()) {
      ElMessage.warning('请先登录后再保存草稿')
      return false
    }
    try {
      await ensureServerPaper()
      if (!draft.value) return false
      const saved = await apiSavePaper(draft.value.id, buildApiPayload(draft.value))
      draft.value = {
        ...saved,
        _localUpdatedAt: remoteTimestamp(saved) || Date.now(),
      }
      saveToStorage(draft.value)
      serverSynced.value = true
      return true
    } catch (e) {
      if (e instanceof ApiError && e.code === 409) {
        return resolveDraftConflict()
      }
      ElMessage.warning((e as Error).message || '草稿同步失败')
      return false
    }
  }

  /** 登录后将本地 nanoid 草稿迁移到服务端 paper；若 ID 变更则返回新 ID */
  async function ensureServerPaper(): Promise<string | null> {
    if (!useApiEnabled() || !getToken() || !draft.value || serverSynced.value) return null
    const local = draft.value
    const appStore = useAppStore()
    try {
      await fetchPaper(local.id)
      serverSynced.value = true
      return null
    } catch (e) {
      const msg = (e as Error).message || ''
      if (!msg.includes('不存在') && !msg.includes('404')) {
        throw e
      }
    }
    const created = await createPaper(appStore.activeMenuId)
    const oldId = local.id
    const migrated: PaperDraft = {
      ...local,
      id: created.id,
      productId: appStore.activeMenuId,
    }
    removeFromStorage(oldId)
    touchLocalRevision()
    migrated._localUpdatedAt = Date.now()
    draft.value = migrated
    currentId.value = created.id
    const saved = await apiSavePaper(migrated.id, buildApiPayload(migrated))
    draft.value = {
      ...saved,
      _localUpdatedAt: remoteTimestamp(saved) || Date.now(),
    }
    saveToStorage(draft.value)
    serverSynced.value = true
    return oldId !== created.id ? created.id : null
  }

  function updateDraft(partial: Partial<PaperDraft>) {
    if (!draft.value) return
    draft.value = { ...draft.value, ...partial }
    touchLocalRevision()
    saveToStorage(draft.value)
    if (useApiEnabled() && getToken()) {
      schedulePersistDraft()
    }
  }

  function setTitle(t: string) {
    updateDraft({ title: t })
  }

  function setLiterature(items: LiteratureItem[]) {
    updateDraft({ literature: items })
  }

  function setOutline(nodes: OutlineNode[]) {
    updateDraft({
      outline: nodes,
      outlineText: nodes.length ? outlineToText(nodes) : '',
    })
  }

  function setOutlineText(text: string) {
    const trimmed = text.trim()
    const parsed = trimmed ? parseOutlineText(trimmed) : []
    updateDraft({
      outlineText: trimmed,
      outline: parsed,
    })
  }

  function clearOutline() {
    updateDraft({ outline: [], outlineText: '' })
  }

  function effectiveOutlineText(): string {
    const d = draft.value
    if (!d) return ''
    if (d.outlineText?.trim()) return d.outlineText.trim()
    if (d.outline.length > 0) return outlineToText(d.outline)
    return ''
  }

  function setPreview(preview: PaperPreview) {
    updateDraft({ preview })
  }

  function markVisitedStep(step: number) {
    if (!draft.value) return
    if (step > draft.value.maxVisitedStep) {
      updateDraft({ maxVisitedStep: step })
    }
  }

  watch(
    draft,
    (val) => {
      if (val) saveToStorage(val)
    },
    { deep: true },
  )

  return {
    currentId,
    draft,
    syncing,
    serverSynced,
    title,
    literature,
    outline,
    initPaper,
    pullFromApi,
    persistDraft,
    ensureServerPaper,
    updateDraft,
    setTitle,
    setLiterature,
    setOutline,
    setOutlineText,
    clearOutline,
    effectiveOutlineText,
    setPreview,
    markVisitedStep,
  }
})
