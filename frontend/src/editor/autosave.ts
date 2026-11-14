// No lost edits (domain spec 5.3): every change is written to IndexedDB at once, sent ~1 s after typing
// stops, flushed when the tab hides, and picked up on the next open if the server never confirmed it.
//
// Stale saves (D6): this copy and the server's are Yjs documents of one lineage, so a 409 is answered by
// MERGING the server state into the local document and saving again. The exception is a state that was
// replaced wholesale (version restore): the server then has a new contentEpoch, and merging the
// old lineage into it would bring back what the restore removed. Such a copy is kept as a private
// "recovered" version instead, and the page loads the server state.
import { useEffect, useState } from 'react'
import * as Y from 'yjs'
import { ApiError } from '../api/client'
import { getContent, saveContent, saveRecovered } from '../api/content'
import { fromBase64, toBase64 } from './base64'
import { pendingFor, type Pending, type PendingStore, type Slots } from './pendingStore'
import { EMPTY_PAGE_STATE } from './schema'
import { holdSlot, isHeld } from './slotLock'

/** failed = the server refused for a reason a retry cannot fix; recovered = kept privately, reload the page. */
export type SaveState = 'saved' | 'saving' | 'offline' | 'retrying' | 'failed' | 'recovered'

export interface SaveStatus {
  state: SaveState
  /** Why it failed, or the recovered notice. */
  message?: string
  /** false = the unsaved changes could NOT be stored on this device. */
  local: boolean
}

export const RECOVERED_NOTICE = 'Your unsaved changes were kept as a private version because this page was restored'

export interface Opened {
  ydoc: Y.Doc
  /** Revision the next save is based on. */
  revision: number
  /** Content epoch this copy is based on. */
  epoch: number
  /** The copy is from an older epoch than the server's: it must be kept privately, never saved to the page. */
  recover: boolean
  state: SaveState
  /** This editing session's own slot in the pending store, and the lock that marks it as alive. */
  slot: string
  store: PendingStore
  release: () => void
  /** Best known plain text of an unsaved copy taken over on open. */
  text?: string
}

const DEBOUNCE_MS = 1000
const BACKOFF_MS = [1000, 2000, 5000, 10_000, 30_000]
/** Statuses that will not get better by sending the same thing again. */
const PERMANENT = new Set([400, 403, 404, 413])

const encode = (doc: Y.Doc) => toBase64(Y.encodeStateAsUpdate(doc))
const lockName = (store: PendingStore, slot: string) => `${store.key}.${slot}`

function docFrom(...states: string[]): Y.Doc {
  const doc = new Y.Doc()
  for (const s of states) if (s) Y.applyUpdate(doc, fromBase64(s))
  return doc
}

/**
 * Loads a page. With edit access, unsaved copies that earlier sessions of this user left on the device
 * come first (spec 5.3 rule 4): same epoch = merged in and saved, older epoch = kept privately.
 */
export async function openContent(nodeId: string, canEdit: boolean, userId: string): Promise<Opened> {
  const store = pendingFor(userId, nodeId)
  const [server, slots] = await Promise.all([getContent(nodeId), canEdit ? store.all() : ({} as Slots)])
  const slot = Math.random().toString(36).slice(2)
  const release = canEdit ? await holdSlot(lockName(store, slot)) : () => {}
  const mine = { store, slot, release }

  const orphans: { id: string; pending: Pending }[] = []
  for (const [id, pending] of Object.entries(slots)) {
    if (await isHeld(lockName(store, id))) continue // belongs to a tab that is still open: not ours to touch
    // The server already has exactly this copy (the flush on tab close worked). That is the ONLY reason a copy
    // is removed without being sent: however old it is, it is merged or kept as a private version below.
    if (pending.state === server.state) {
      await store.clear(id)
      continue
    }
    try {
      docFrom(pending.state) // can it be read at all?
      orphans.push({ id, pending })
    } catch {
      await store.quarantine(id) // one broken copy must not make the page impossible to open
    }
  }

  // A copy from before a restore goes first and alone; the reload after it handles the rest.
  const old = orphans.find((o) => o.pending.epoch !== server.contentEpoch)
  if (old) {
    if (await store.put(slot, { ...old.pending })) await store.clear(old.id)
    return {
      ...mine, ydoc: docFrom(old.pending.state), revision: old.pending.baseRevision, epoch: old.pending.epoch,
      recover: true, state: 'saving', text: old.pending.text,
    }
  }

  const ydoc = docFrom(server.state || EMPTY_PAGE_STATE, ...orphans.map((o) => o.pending.state))
  const current = { ...mine, ydoc, revision: server.revision, epoch: server.contentEpoch, recover: false }
  if (!orphans.length) return { ...current, state: 'saved' }

  // Under this session's slot first, and only then remove the old ones: never a moment without a copy.
  const text = orphans.map((o) => o.pending.text).join('\n')
  const taken = await store.put(slot, { state: encode(ydoc), text, baseRevision: server.revision, epoch: server.contentEpoch, at: Date.now() })
  if (taken) for (const o of orphans) await store.clear(o.id)
  return { ...current, state: 'saving', text }
}

export class Autosave {
  state: SaveState
  message: string | undefined
  private local = true
  private revision: number
  private recover: boolean
  private dirty: boolean
  private version = 0 // bumped on every change, to tell whether a finished save is still current
  private inFlight = false
  private again = false // save once more as soon as the current attempt has finished
  private attempt = 0
  private stopped = false
  private timer: ReturnType<typeof setTimeout> | undefined
  private text: string | undefined // as of the last change; the editor may be gone at save time

  constructor(
    private nodeId: string,
    private ydoc: Y.Doc,
    private getText: () => string,
    private opened: Omit<Opened, 'ydoc'>,
    private onStatus: (status: SaveStatus) => void,
  ) {
    this.state = opened.state
    this.revision = opened.revision
    this.recover = opened.recover
    this.dirty = opened.state !== 'saved'
    this.text = opened.text
    ydoc.on('update', this.onUpdate)
    window.addEventListener('online', this.onOnline)
    window.addEventListener('pagehide', this.flush)
    window.addEventListener('beforeunload', this.onBeforeUnload)
    document.addEventListener('visibilitychange', this.onVisibility)
    // A copy taken over on open: wait until the editor has rendered it, so its text can be read.
    if (this.dirty) {
      this.timer = setTimeout(() => {
        this.text = this.readText()
        void this.save()
      }, DEBOUNCE_MS)
    }
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
    const last = this.save()
    this.stopped = true
    void last.finally(this.opened.release) // from here on the slot counts as orphaned
  }

  private readText(): string {
    try {
      return this.getText()
    } catch {
      return this.text ?? '' // editor already unmounted
    }
  }

  private snapshot() {
    return { state: encode(this.ydoc), text: this.text ?? this.readText() }
  }

  // shortcut: writes the whole encoded page on every change; store incremental Yjs updates instead if
  // typing on very large pages becomes slow.
  private storePending() {
    const copy = { ...this.snapshot(), baseRevision: this.revision, epoch: this.opened.epoch, at: Date.now() }
    void this.opened.store.put(this.opened.slot, copy).then((ok) => {
      if (ok === this.local) return
      this.local = ok
      this.emit()
    })
  }

  private emit() {
    if (!this.stopped) this.onStatus({ state: this.state, message: this.message, local: this.local })
  }

  private setState(state: SaveState, message?: string) {
    if (state === this.state && message === this.message) return
    this.state = state
    this.message = message
    this.emit()
  }

  private schedule(ms: number) {
    clearTimeout(this.timer)
    this.timer = setTimeout(() => void this.save(), ms)
  }

  private onUpdate = (_update: Uint8Array, origin: unknown) => {
    if (origin === this) return // our own merge of the server state; stale() deals with it
    this.version++
    this.dirty = true
    this.text = this.readText()
    this.storePending()
    if (this.state === 'saved' || this.state === 'failed') this.setState('saving')
    this.schedule(DEBOUNCE_MS)
  }

  private onOnline = () => {
    if (this.dirty) this.schedule(0)
  }

  private onVisibility = () => {
    if (document.visibilityState === 'hidden') this.flush()
  }

  /** Tab is hiding or closing: send now with a request that survives the unload. */
  private flush = () => void this.save(true)

  private onBeforeUnload = (e: BeforeUnloadEvent) => {
    if (this.state === 'saved' || this.state === 'recovered') return
    e.preventDefault()
    e.returnValue = '' // older browsers need this to show the "Leave page?" prompt
  }

  private async save(keepalive = false) {
    if (this.inFlight || !this.dirty) return
    clearTimeout(this.timer)
    this.inFlight = true
    const sentVersion = this.version
    const sent = this.snapshot()
    try {
      if (this.recover) {
        await this.keepPrivately(sent, sentVersion)
      } else {
        const saved = await saveContent(this.nodeId, { baseRevision: this.revision, ...sent }, keepalive)
        this.confirmed(saved.revision, sentVersion)
      }
    } catch (e) {
      if (!this.recover && e instanceof ApiError && e.status === 409) await this.stale(sent.state, sentVersion)
      else this.failed(e)
    } finally {
      this.inFlight = false
    }
    if (this.again) {
      this.again = false
      await this.save()
    }
  }

  private confirmed(revision: number, sentVersion: number) {
    this.revision = revision
    this.attempt = 0
    if (this.version === sentVersion) {
      this.dirty = false
      void this.opened.store.clear(this.opened.slot)
      this.setState('saved')
    } else {
      // Changed again while the save was in flight: the local copy now builds on the new revision.
      this.storePending()
      this.setState('saving')
      this.schedule(DEBOUNCE_MS)
    }
  }

  private failed(e: unknown) {
    if (this.stopped) return // page was left; the pending copy is picked up on the next open
    const reason = e instanceof Error ? e.message : 'unknown error'
    if (e instanceof ApiError && PERMANENT.has(e.status)) {
      // Too large, no longer allowed, page gone: trying again would never work. The next change tries once more.
      this.setState('failed', reason)
      return
    }
    this.setState(
      navigator.onLine === false ? 'offline' : 'retrying',
      this.recover ? `Your unsaved changes could not be kept as a private version yet (${reason}). They are still on this device; trying again.` : undefined,
    )
    this.schedule(BACKOFF_MS[Math.min(this.attempt++, BACKOFF_MS.length - 1)])
  }

  /** 409: the server has a newer revision than the one this save was based on. */
  private async stale(sentState: string, sentVersion: number) {
    try {
      const server = await getContent(this.nodeId)
      // It is our own save: it reached the server but its answer was lost (network drop, closing tab).
      if (server.state === sentState) return this.confirmed(server.revision, sentVersion)

      if (server.contentEpoch !== this.opened.epoch) {
        // Replaced wholesale since this copy was made: merging would resurrect what was removed.
        this.recover = true
        await this.keepPrivately(this.snapshot(), this.version)
        return
      }
      // Same lineage: take in what the server has and save the union on top of its revision.
      this.revision = server.revision
      if (server.state) Y.applyUpdate(this.ydoc, fromBase64(server.state), this)
      if (!this.stopped) this.text = this.readText()
      this.version++
      this.storePending()
      this.setState('saving')
      this.again = true
    } catch (e) {
      this.failed(e) // could not look or could not keep; the retry comes back here
    }
  }

  /** The local copy must not go into the page: store it as a version only this user sees. */
  private async keepPrivately(copy: { state: string; text: string }, sentVersion: number) {
    await saveRecovered(this.nodeId, { ...copy, baseEpoch: this.opened.epoch })
    if (this.version !== sentVersion) {
      this.again = true // typed meanwhile: keep that too
      return
    }
    this.dirty = false
    await this.opened.store.clear(this.opened.slot) // only now: the copy is safe on the server
    this.setState('recovered', RECOVERED_NOTICE)
  }
}

/** Runs autosave for an opened page while `enabled` (edit access). */
export function useAutosave(nodeId: string, opened: Opened, getText: () => string, enabled: boolean): SaveStatus {
  const [status, setStatus] = useState<SaveStatus>({ state: opened.state, local: true })
  const [text] = useState(() => ({ read: getText }))
  text.read = getText

  useEffect(() => {
    if (!enabled) return
    const autosave = new Autosave(nodeId, opened.ydoc, () => text.read(), opened, setStatus)
    setStatus({ state: autosave.state, local: true })
    return () => autosave.destroy()
  }, [nodeId, opened, enabled, text])

  return status
}
