import type { DocumentRecord, DownloadIntent, UploadIntent, UserProfile } from './types'

type CsrfResponse = { headerName: string; token: string }

class ApiError extends Error {
  constructor(readonly status: number, message: string) {
    super(message)
    this.name = 'ApiError'
  }
}

let csrf: CsrfResponse | undefined

async function readResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    let message = `Request failed (${response.status})`
    try {
      const body = await response.json() as { detail?: string; message?: string }
      message = body.detail ?? body.message ?? message
    } catch {
      // Keep the status-based message when the server did not return JSON.
    }
    throw new ApiError(response.status, message)
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

async function csrfToken(): Promise<CsrfResponse> {
  if (!csrf) {
    const response = await fetch('/api/csrf', { credentials: 'include' })
    if (!response.ok) throw new ApiError(response.status, 'Could not prepare a secure request.')
    const cookie = document.cookie.split('; ').find((entry) => entry.startsWith('XSRF-TOKEN='))
    const token = cookie?.slice('XSRF-TOKEN='.length)
    if (!token) throw new ApiError(500, 'The security token was not available in this browser.')
    csrf = { headerName: 'X-XSRF-TOKEN', token: decodeURIComponent(token) }
  }
  return csrf
}

async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method ?? 'GET').toUpperCase()
  const headers = new Headers(init.headers)
  if (method !== 'GET' && method !== 'HEAD' && method !== 'OPTIONS') {
    const token = await csrfToken()
    headers.set(token.headerName, token.token)
  }
  if (init.body && !(init.body instanceof FormData)) headers.set('Content-Type', 'application/json')
  return readResponse<T>(await fetch(path, { ...init, headers, credentials: 'include' }))
}

export const apiClient = {
  me: () => api<UserProfile>('/api/me'),
  documents: () => api<DocumentRecord[]>('/api/documents'),
  createUpload: (file: File) => api<UploadIntent>('/api/documents', {
    method: 'POST',
    body: JSON.stringify({ fileName: file.name, contentType: file.type, sizeBytes: file.size }),
  }),
  async upload(file: File, intent: UploadIntent) {
    const form = new FormData()
    for (const [name, value] of Object.entries(intent.fields)) form.append(name, value)
    form.append('file', file)
    const response = await fetch(intent.uploadUrl, {
      method: 'POST',
      body: form,
    })
    if (!response.ok) throw new ApiError(response.status, 'The file could not be sent to storage.')
    return api<DocumentRecord>(`/api/documents/${intent.documentId}/complete`, { method: 'POST' })
  },
  download: (id: string) => api<DownloadIntent>(`/api/documents/${id}/download`),
  remove: (id: string) => api<void>(`/api/documents/${id}`, { method: 'DELETE' }),
  logout: async () => {
    const result = await api<void>('/logout', { method: 'POST' })
    csrf = undefined
    return result
  },
  isUnauthenticated: (error: unknown) => error instanceof ApiError && error.status === 401,
}
