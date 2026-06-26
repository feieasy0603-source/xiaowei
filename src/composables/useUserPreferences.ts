import { updateProfile } from '@/api/modules/auth'
import { getToken, useApiEnabled } from '@/api/http'
import type { PaperMeta, PaperModel } from '@/types/paper'

const PREFS_KEY = 'xiaowei_user_prefs'

export interface UserWritingPrefs {
  degree?: string
  wordCount?: number
  model?: PaperModel
  schoolId?: string
  category?: string
  language?: PaperMeta['language']
}

export function loadUserPreferences(): UserWritingPrefs {
  try {
    const raw = localStorage.getItem(PREFS_KEY)
    if (!raw) return {}
    return JSON.parse(raw) as UserWritingPrefs
  } catch {
    return {}
  }
}

export function saveUserPreferences(partial: UserWritingPrefs) {
  const next = { ...loadUserPreferences(), ...partial }
  localStorage.setItem(PREFS_KEY, JSON.stringify(next))
}

/** 将偏好合并进草稿 meta / model（不覆盖已有非空标题场景下的显式设置） */
export function applyPrefsToDraftMeta(meta: PaperMeta, model?: PaperModel): {
  meta: PaperMeta
  model?: PaperModel
} {
  const prefs = loadUserPreferences()
  return {
    meta: {
      ...meta,
      ...(prefs.degree ? { degree: prefs.degree } : {}),
      ...(prefs.wordCount ? { wordCount: prefs.wordCount } : {}),
      ...(prefs.schoolId ? { schoolId: prefs.schoolId } : {}),
      ...(prefs.category ? { category: prefs.category } : {}),
      ...(prefs.language ? { language: prefs.language } : {}),
    },
    model: model ?? prefs.model,
  }
}

export function capturePrefsFromDraft(meta: PaperMeta, model?: PaperModel) {
  const prefs: UserWritingPrefs = {
    degree: meta.degree,
    wordCount: meta.wordCount,
    schoolId: meta.schoolId || undefined,
    category: meta.category,
    language: meta.language,
    model,
  }
  saveUserPreferences(prefs)
  if (useApiEnabled() && getToken()) {
    void updateProfile({ preferences: prefs }).catch(() => {})
  }
}
