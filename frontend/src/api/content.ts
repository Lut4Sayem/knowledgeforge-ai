import { api } from './client'

export interface Content {
  format: string
  state: string // base64 Yjs state, '' for a new page
  revision: number
  /** Goes up when the state was replaced wholesale (restore, migration). Copies from another epoch must not be merged in. */
  contentEpoch: number
}

export interface SaveBody {
  baseRevision: number
  state: string
  text: string
}

// A request that never answers would block every later save, so content calls give up: after 15 s, plus
// 10 s per MB sent (a 4 MB page on a slow uplink needs longer than a small one).
const TIMEOUT_MS = 15_000
const TIMEOUT_MS_PER_MB = 10_000
const bytes = (json: string) => new Blob([json]).size

function timed<T>(call: (signal: AbortSignal) => Promise<T>, bodyBytes = 0): Promise<T> {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS + (TIMEOUT_MS_PER_MB * bodyBytes) / (1024 * 1024))
  return call(controller.signal).finally(() => clearTimeout(timer))
}

export const getContent = (nodeId: string) => timed((signal) => api<Content>(`/api/nodes/${nodeId}/content`, { signal }))

// Browsers refuse keepalive requests whose bodies total more than 64 KB, so a bigger page is sent as a
// normal request (it may die with the tab; the local pending copy covers that).
const KEEPALIVE_MAX_BYTES = 60_000

/** 409 = the page has a newer revision. keepalive lets the request outlive the tab (flush on close). */
export function saveContent(nodeId: string, body: SaveBody, keepalive = false) {
  const json = JSON.stringify(body)
  const size = bytes(json)
  return timed(
    (signal) =>
      api<{ revision: number; savedAt: string }>(`/api/nodes/${nodeId}/content`, {
        method: 'PUT',
        body: json,
        signal,
        keepalive: keepalive && size <= KEEPALIVE_MAX_BYTES,
        redirectOn401: !keepalive, // never navigate from a page that is closing
      }),
    size,
  )
}

/** Keeps a copy that must not be merged into the page as a version only this user can see. Safe to repeat. */
export function saveRecovered(nodeId: string, body: { state: string; text: string; baseEpoch: number }) {
  const json = JSON.stringify(body)
  return timed(
    (signal) => api<{ id: string; createdAt: string }>(`/api/nodes/${nodeId}/versions/recovered`, { method: 'POST', body: json, signal }),
    bytes(json),
  )
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
