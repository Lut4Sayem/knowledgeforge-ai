import { api } from './client'

/** Uploads a file to a page; the server's message (too large, type not allowed, ...) comes back as the error text. */
export async function uploadAttachment(nodeId: string, file: File): Promise<string> {
  const body = new FormData()
  body.append('file', file)
  const a = await api<{ id: string }>(`/api/nodes/${nodeId}/attachments/upload`, { method: 'POST', body })
  return `/api/attachments/${a.id}/download`
}

export interface LinkPreview {
  url: string
  title: string | null
  description: string | null
  imageUrl: string | null
}

export const getLinkPreview = (url: string) => api<LinkPreview>(`/api/link-preview?url=${encodeURIComponent(url)}`)
