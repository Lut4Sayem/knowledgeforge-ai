import { api } from './client'

export interface NodeSummary {
  id: string
  parentId: string | null
  rank: string
  title: string
  icon: string | null
  type: 'PAGE' | 'BOARD'
  publishState: 'DRAFT' | 'PUBLISHED'
  locked: boolean
  titleOnly: boolean
}

export interface NodeDetail {
  id: string
  spaceId: string
  parentId: string | null
  title: string
  icon: string | null
  tags: string[]
  publishState: 'DRAFT' | 'PUBLISHED'
  locked: boolean
  lockedBy: string | null
  status: 'ACTIVE' | 'ARCHIVED' | 'TRASHED'
  access: 'NONE' | 'TITLE_ONLY' | 'VIEW' | 'EDIT'
  breadcrumbs: { id: string; title: string }[]
  contentUpdatedAt: string
}

export interface MoveBody {
  parentId: string | null
  beforeId?: string
  afterId?: string
}

export const getTree = (spaceId: string) => api<NodeSummary[]>(`/api/spaces/${spaceId}/tree`)

export const createNode = (spaceId: string, body: { parentId?: string | null; title?: string; afterId?: string }) =>
  api<NodeDetail>(`/api/spaces/${spaceId}/nodes`, { method: 'POST', body: JSON.stringify(body) })

export const getNode = (id: string) => api<NodeDetail>(`/api/nodes/${id}`)

export const updateNode = (id: string, body: { title?: string; icon?: string; tags?: string[] }) =>
  api<NodeDetail>(`/api/nodes/${id}`, { method: 'PATCH', body: JSON.stringify(body) })

export const moveNode = (id: string, body: MoveBody) =>
  api<NodeDetail>(`/api/nodes/${id}/move`, { method: 'POST', body: JSON.stringify(body) })
