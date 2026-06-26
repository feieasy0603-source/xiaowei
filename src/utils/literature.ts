import type { LiteratureItem } from '@/types/paper'

export function mapLiteratureItem(raw: Record<string, unknown>): LiteratureItem {
  return {
    id: String(raw.id ?? ''),
    title: String(raw.title ?? ''),
    authors: String(raw.authors ?? ''),
    source: String(raw.source ?? ''),
    year: Number(raw.year ?? 0),
    lang: raw.lang === 'en' ? 'en' : 'zh',
    gbtCitation: String(raw.gbtCitation ?? ''),
  }
}
