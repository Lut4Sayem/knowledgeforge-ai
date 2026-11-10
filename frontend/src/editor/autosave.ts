// No lost edits (domain spec 5.3): every change is written to IndexedDB at once, sent ~1 s after typing
// stops, flushed when the tab hides, and replayed on the next open if the server never confirmed it.
import { useEffect, useRef, useState } from 'react'
import * as Y from 'yjs'
import { ApiError } from '../api/client'
import { getContent, saveContent } from '../api/content'
import { fromBase64, toBase64 } from './base64'
import { pendingStore, type Slots } from './pendingStore'

export type SaveState = 'saved' | 'saving' | 'offline' | 'retrying' | 'conflict'

export interface Opened {
  ydoc: Y.Doc
  /** Revision the next save is based on. */
  revision: number
  /** What the server has now. Differs from `revision` only in a conflict; "Keep mine" saves on top of it. */
  serverRevision: number
  state: SaveState
  /** This editing session's slot in the pending store. */
  slot: string
  /** Plain text of a replayed pending copy (the editor has not rendered it yet when it is saved). */
  text?: string
}

const DEBOUNCE_MS = 1000
const BACKOFF_MS = [1000, 2000, 5000, 10_000, 30_000]

function docFrom(...states: string[]): Y.Doc {
  const doc = new Y.Doc()
  for (const s of states) if (s) Y.applyUpdate(doc, fromBase64(s))
  return doc
}

/** Loads a page. With edit access, unconfirmed local changes come first (spec 5.3 rule 4). */
export async function openContent(nodeId: string, canEdit: boolean): Promise<Opened> {
  const [server, slots] = await Promise.all([getContent(nodeId), canEdit ? pendingStore.all(nodeId) : ({} as Slots)])
  const unsaved = []
  for (const [slot, pending] of Object.entries(slots)) {
    // The server already has exactly this copy (e.g. the flush on tab close worked): nothing to replay.
    if (pending.state === server.state) await pendingStore.clear(nodeId, slot)
    else unsaved.push({ slot, pending })
  }
  // Older leftovers (another tab's copy) stay stored and come up on a later open.
  const newest = unsaved.sort((a, b) => b.pending.at - a.pending.at)[0]
  const base = { revision: server.revision, serverRevision: server.revision }

  if (!newest) {
    return { ...base, ydoc: docFrom(server.state), state: 'saved', slot: Math.random().toString(36).slice(2) }
  }
  const { slot, pending } = newest
  if (pending.baseRevision === server.revision) {
    return { ...base, ydoc: docFrom(server.state, pending.state), state: 'saving', slot, text: pending.text }
  }
  // The page was saved somewhere else since this copy was made: show mine, save nothing until the user chooses.
  return { ...base, ydoc: docFrom(pending.state), revision: pending.baseRevision, state: 'conflict', slot, text: pending.text }
}

export class Autosave {
  state: SaveState
  private revision: number
  private serverRevision: number
  private dirty: boolean
  private version = 0 // bumped on every change, to tell whether a finished save is still current
  private inFlight = false
  private attempt = 0
  private stopped = false
  private timer: ReturnType<typeof setTimeout> | undefined
  private text: string | undefined // as of the last change; read then because the editor may be gone at save time

  constructor(
    private nodeId: string,
    private ydoc: Y.Doc,
    private getText: () => string,
    private opened: Omit<Opened, 'ydoc'>,
    private onState: (state: SaveState) => void,
  ) {
    this.state = opened.state
    this.revision = opened.revision
    this.serverRevision = opened.serverRevision
    this.dirty = opened.state !== 'saved'
    this.text = opened.text
    ydoc.on('update', this.onUpdate)
    window.addEventListener('online', this.onOnline)
    window.addEventListener('pagehide', this.flush)
    window.addEventListener('beforeunload', this.onBeforeUnload)
    document.addEventListener('visibilitychange', this.onVisibility)
    if (opened.state === 'saving') this.schedule(0) // replay of a pending copy
  }

  /** Stops listening. A last change is still sent once; if that fails it stays in the pending store. */
  destroy() {
    if (this.stopped) return
    this.ydoc.off('update', this.onUpdate)
    window.removeEventListener('online', this.onOnline)
    window.removeEventListener('pagehide', this.flush)
    window.removeEventListener('beforeunload', this.onBeforeUnload)
    document.removeEventListener('visibilitychange', this.onVisibility)
    clearTimeout(this.timer)
    void this.save()
    this.stopped = true
  }

  /** Conflict banner, "Keep mine": save my copy on top of whatever revision the server has now. */
  async keepMine() {
    if (this.state !== 'conflict') return
    this.setState('saving')
    try {
      this.serverRevision = (await getContent(this.nodeId)).revision // it may have moved since the banner appeared
    } catch {
      // offline: try the last revision we saw; a wrong guess just brings the banner back
    }
    this.revision = this.serverRevision
    this.storePending()
    await this.save()
  }

  private snapshot() {
    return { state: toBase64(Y.encodeStateAsUpdate(this.ydoc)), text: this.text ?? this.getText() }
  }

  // shortcut: writes the whole encoded page on every change; store incremental Yjs updates instead if
  // typing on very large pages becomes slow.
  private storePending() {
    void pendingStore.put(this.nodeId, this.opened.slot, { ...this.snapshot(), baseRevision: this.revision, at: Date.now() })
  }

  private setState(state: SaveState) {
    if (this.stopped || state === this.state) return
    this.state = state
    this.onState(state)
  }

  private schedule(ms: number) {
    clearTimeout(this.timer)
    this.timer = setTimeout(() => void this.save(), ms)
  }

  private onUpdate = () => {
    this.version++
    this.dirty = true
    this.text = this.getText()
    this.storePending()
    if (this.state === 'conflict') return // local copy only, until the user picks a side
    if (this.state === 'saved') this.setState('saving')
    this.schedule(DEBOUNCE_MS)
  }

  private onOnline = () => {
    if (this.dirty && this.state !== 'conflict') this.schedule(0)
  }

  private onVisibility = () => {
    if (document.visibilityState === 'hidden') this.flush()
  }

  /** Tab is hiding or closing: send now with a request that survives the unload. */
  private flush = () => void this.save(true)

  private onBeforeUnload = (e: BeforeUnloadEvent) => {
    if (this.state === 'saved') return
    e.preventDefault()
    e.returnValue = '' // older browsers need this to show the "Leave page?" prompt
  }

  private async save(keepalive = false) {
    if (this.inFlight || !this.dirty || this.state === 'conflict') return
    clearTimeout(this.timer)
    this.inFlight = true
    const sentVersion = this.version
    const sent = this.snapshot()
    try {
      const saved = await saveContent(this.nodeId, { baseRevision: this.revision, ...sent }, keepalive)
      this.confirmed(saved.revision, sentVersion)
    } catch (e) {
      if (e instanceof ApiError && e.status === 409) await this.stale(sent.state, sentVersion)
      else this.failed()
    } finally {
      this.inFlight = false
    }
  }

  private confirmed(revision: number, sentVersion: number) {
    this.revision = this.serverRevision = revision
    this.attempt = 0
    if (this.version === sentVersion) {
      this.dirty = false
      void pendingStore.clear(this.nodeId, this.opened.slot)
      this.setState('saved')
    } else {
      // Changed again while the save was in flight: the local copy now builds on the new revision.
      this.storePending()
      this.setState('saving')
      this.schedule(DEBOUNCE_MS)
    }
  }

  private failed() {
    if (this.stopped) return // page was left; the pending copy is replayed on the next open
    this.setState(navigator.onLine === false ? 'offline' : 'retrying')
    this.schedule(BACKOFF_MS[Math.min(this.attempt++, BACKOFF_MS.length - 1)])
  }

  /** 409: the server has a newer revision than the one this save was based on. */
  private async stale(sentState: string, sentVersion: number) {
    try {
      const server = await getContent(this.nodeId)
      // It is our own save: it reached the server but its answer was lost (network drop, closing tab).
      if (server.state === sentState) return this.confirmed(server.revision, sentVersion)
      this.serverRevision = server.revision
      this.setState('conflict') // the pending copy keeps its old base, so a reload shows the conflict again
    } catch {
      this.failed() // could not look; the retry gets 409 again and re-checks
    }
  }
}

/** Runs autosave for an opened page while `enabled` (edit access). */
export function useAutosave(nodeId: string, opened: Opened, getText: () => string, enabled: boolean) {
  const [state, setState] = useState<SaveState>(opened.state)
  const autosave = useRef<Autosave>()
  const text = useRef(getText)
  text.current = getText

  useEffect(() => {
    if (!enabled) return
    const a = new Autosave(nodeId, opened.ydoc, () => text.current(), opened, setState)
    autosave.current = a
    setState(a.state)
    return () => a.destroy()
  }, [nodeId, opened, enabled])

  return { state, keepMine: () => void autosave.current?.keepMine() }
}
