import { adminApiUrl, getAdminToken } from '@/api/http'

export async function downloadAdminPaperFile(fileId: number, fileName: string) {
  const headers: Record<string, string> = {}
  const token = getAdminToken()
  if (token) headers.Authorization = `Bearer ${token}`

  const res = await fetch(adminApiUrl(`/admin/paper-files/${fileId}/download`), { headers })
  if (!res.ok) {
    let msg = `下载失败 (${res.status})`
    try {
      const json = await res.json()
      if (json.message) msg = json.message
    } catch {
      /* ignore */
    }
    throw new Error(msg)
  }
  const blob = await res.blob()
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = fileName
  a.click()
  URL.revokeObjectURL(a.href)
}
