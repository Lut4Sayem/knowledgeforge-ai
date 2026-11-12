import { api } from './client'

export type VersionKind = 'AUTO' | 'NAMED' | 'PUBLISHED' | 'RESTORED' | 'RECOVERED'

export interface VersionAuthor {
  userId: string
  name: string
  avatarUrl: string | null
}

export interface VersionSummary {
  id: string
  kind: VersionKind
  name: string | null
  authors: VersionAuthor[]
  createdAt: string
  updatedAt: string
  /** Only the person who owns it can see it (recovered unsaved changes, their own drafts). */
  private: boolean
}

export interface VersionDetail extends VersionSummary {
  state: string
  text: string
}

export const listVersions = (nodeId: string) => api<VersionSummary[]>(`/api/nodes/${nodeId}/versions`)
export const getVersion = (id: string) => api<VersionDetail>(`/api/versions/${id}`)
export const nameVersion = (nodeId: string, name: string) =>
  api<VersionSummary>(`/api/nodes/${nodeId}/versions`, { method: 'POST', body: JSON.stringify({ name }) })
export const restoreVersion = (id: string) => api<VersionSummary>(`/api/versions/${id}/restore`, { method: 'POST' })

/** Leaving the page: turns the open edit session into a version now. keepalive lets it outlive the tab. */
export function endSession(nodeId: string) {
  api<null>(`/api/nodes/${nodeId}/session/end`, { method: 'POST', keepalive: true, redirectOn401: false }).catch(() => {}) // the 60 s job closes it anyway
}
