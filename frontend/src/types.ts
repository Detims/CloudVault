export type UserProfile = {
  id: string
  name: string | null
  email: string | null
  picture: string | null
}

export type DocumentRecord = {
  id: string
  fileName: string
  contentType: string
  sizeBytes: number
  status: 'PENDING_UPLOAD' | 'AVAILABLE'
  createdAt: string
}

export type UploadIntent = {
  documentId: string
  uploadUrl: string
  fields: Record<string, string>
  expiresAt: string
  contentType: string
  maxFileBytes: number
}

export type DownloadIntent = {
  downloadUrl: string
  expiresAt: string
}

