import { apiFetch } from '@/api/http'

export interface SchoolOption {
  id: string
  name: string
}

export async function fetchSchools(): Promise<SchoolOption[]> {
  return apiFetch<SchoolOption[]>('/schools')
}
