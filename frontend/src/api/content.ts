import { api } from './client'

export interface Content {
  format: string
  state: string // base64 Yjs state, '' for a new page
  revision: number
}

export interface SaveBody {
  baseRevision: number
  state: string
  text: string
}

export const getContent = (nodeId: string) => api<Content>(`/api/nodes/${nodeId}/content`)

// Browsers refuse keepalive requests whose bodies total more than 64 KB, so a bigger page is sent as a
// normal request (it may die with the tab; the local pending copy covers that).
const KEEPALIVE_MAX_BYTES = 60_000

/** 409 = the page has a newer revision. keepalive lets the request outlive the tab (flush on close). */
export function saveContent(nodeId: string, body: SaveBody, keepalive = false) {
  const json = JSON.stringify(body)
  return api<{ revision: number; savedAt: string }>(`/api/nodes/${nodeId}/content`, {
    method: 'PUT',
    body: json,
    keepalive: keepalive && new Blob([json]).size <= KEEPALIVE_MAX_BYTES,
    redirectOn401: !keepalive, // never navigate from a page that is closing
  })
}

export interface EditLock {
  mine: boolean
  holder: { id: string; name: string; avatarUrl: string | null } | null
  expiresAt: string
}

/** Soft lock (1a only). Acquires or renews for 60 s; `force` takes it over from someone else. */
export const acquireEditLock = (nodeId: string, force: boolean) =>
  api<EditLock>(`/api/nodes/${nodeId}/edit-lock`, { method: 'POST', body: JSON.stringify({ force }) })

export const releaseEditLock = (nodeId: string, keepalive = false) =>
  api<null>(`/api/nodes/${nodeId}/edit-lock`, { method: 'DELETE', keepalive, redirectOn401: false })
