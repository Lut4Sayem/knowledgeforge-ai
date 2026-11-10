import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as Y from 'yjs'
import { ApiError } from '../api/client'
import { Autosave, openContent, type Opened, type SaveState } from './autosave'
import { toBase64 } from './base64'
import type { Pending } from './pendingStore'
import SaveIndicator from './SaveIndicator'

// Fake server with the real revision rule, and an in-memory stand-in for IndexedDB (shared by all "tabs").
const server = { revision: 3, state: '', puts: [] as { baseRevision: number; state: string; text: string; keepalive: boolean }[] }
const contentApi = vi.hoisted(() => ({ getContent: vi.fn(), saveContent: vi.fn() }))
vi.mock('../api/content', () => contentApi)
const idb = vi.hoisted(() => new Map<string, Record<string, unknown>>())
vi.mock('./pendingStore', () => ({
  pendingStore: {
    put: async (id: string, slot: string, p: unknown) => void idb.set(id, { ...idb.get(id), [slot]: p }),
    all: async (id: string) => idb.get(id) ?? {},
    clear: async (id: string, slot: string) => {
      const rest = { ...idb.get(id) }
      delete rest[slot]
      idb.set(id, rest)
    },
  },
}))

const NODE = 'n1'
const pendingOf = (slot: string) => idb.get(NODE)?.[slot] as Pending | undefined
/** The one local copy on the device (fails when there are several). */
const pending = () => {
  const all = Object.values(idb.get(NODE) ?? {}) as Pending[]
  expect(all.length).toBeLessThanOrEqual(1)
  return all[0] as Pending | undefined
}
/** A copy left behind by an earlier tab. */
const leftover = (p: Omit<Pending, 'at'>, slot = 'old', at = 1) => idb.set(NODE, { ...idb.get(NODE), [slot]: { ...p, at } })
const stateOf = (doc: Y.Doc) => toBase64(Y.encodeStateAsUpdate(doc))
const textOf = (doc: Y.Doc) => doc.getText('t').toString()
const type = (doc: Y.Doc, s: string) => doc.getText('t').insert(doc.getText('t').length, s)
const tick = (ms: number) => vi.advanceTimersByTimeAsync(ms)

const live: Autosave[] = []
function start(opened: Opened) {
  const states: SaveState[] = []
  const a = new Autosave(NODE, opened.ydoc, () => textOf(opened.ydoc), opened, (s) => states.push(s))
  live.push(a)
  return { a, states, doc: opened.ydoc, slot: opened.slot }
}
/** Opens the page like a tab would. */
const openTab = async () => start(await openContent(NODE, true))

function setOnline(value: boolean) {
  Object.defineProperty(navigator, 'onLine', { configurable: true, get: () => value })
}

beforeEach(() => {
  vi.useFakeTimers()
  const base = new Y.Doc()
  type(base, 'base ')
  Object.assign(server, { revision: 3, state: stateOf(base), puts: [] })
  contentApi.getContent.mockImplementation(async () => ({ format: 'YJS_BLOCKNOTE_V1', state: server.state, revision: server.revision }))
  contentApi.saveContent.mockImplementation(async (_id: string, body: { baseRevision: number; state: string; text: string }, keepalive = false) => {
    server.puts.push({ ...body, keepalive })
    if (body.baseRevision !== server.revision) throw new ApiError(409, 'This page changed somewhere else')
    server.revision++
    server.state = body.state
    return { revision: server.revision, savedAt: '2026-11-10T00:00:00Z' }
  })
})

afterEach(() => {
  live.splice(0).forEach((a) => a.destroy())
  cleanup()
  idb.clear()
  vi.clearAllMocks()
  vi.useRealTimers()
  setOnline(true)
})

describe('autosave', () => {
  it('debouncesOneSecond', async () => {
    const { a, doc, states } = await openTab()
    expect(a.state).toBe('saved')

    type(doc, 'a')
    await tick(0)
    expect(pending()).toMatchObject({ state: stateOf(doc), text: 'base a', baseRevision: 3 }) // local copy is written at once
    expect(a.state).toBe('saving')
    await tick(600)
    type(doc, 'b')
    await tick(999) // 1 s after the LAST change has not passed yet
    expect(server.puts).toHaveLength(0)

    await tick(1)
    expect(server.puts).toEqual([{ baseRevision: 3, state: stateOf(doc), text: 'base ab', keepalive: false }])
    expect(a.state).toBe('saved')
    expect(pending()).toBeUndefined() // confirmed by the server, so the local copy is dropped
    expect(states).toEqual(['saving', 'saved'])

    await tick(60_000)
    expect(server.puts).toHaveLength(1) // nothing more to save
  })

  it('editsMadeDuringASaveAreSavedNext', async () => {
    const { a, doc } = await openTab()
    let release!: () => void
    contentApi.saveContent.mockImplementationOnce(async (_id: string, body: { state: string }) => {
      await new Promise<void>((r) => (release = r))
      server.revision++
      server.state = body.state
      return { revision: server.revision, savedAt: '' }
    })
    type(doc, 'one')
    await tick(1000) // first save is now in flight
    type(doc, ' two')
    release()
    await tick(0)

    expect(a.state).toBe('saving') // not "Saved": the newer text is not on the server yet
    expect(pending()).toMatchObject({ state: stateOf(doc), text: 'base one two', baseRevision: 4 })
    await tick(1000)
    expect(server.puts.at(-1)).toMatchObject({ baseRevision: 4, text: 'base one two' })
    expect(a.state).toBe('saved')
    expect(pending()).toBeUndefined()
  })

  // Review Focus 1: a big page cannot be flushed on tab close (keepalive bodies are capped at 64 KB).
  it('pendingStateSurvivesFailedFlush', async () => {
    const first = await openTab()
    type(first.doc, 'x'.repeat(100_000))
    await tick(0)
    contentApi.saveContent.mockRejectedValueOnce(new TypeError('Failed to fetch')) // the flush dies with the tab

    window.dispatchEvent(new Event('pagehide'))
    await tick(0)
    expect(contentApi.saveContent).toHaveBeenCalledWith(NODE, expect.objectContaining({ baseRevision: 3 }), true)
    vi.clearAllTimers() // the tab is gone: nothing of it runs any more
    expect(server.revision).toBe(3) // nothing reached the server
    expect(pending()).toMatchObject({ baseRevision: 3, state: stateOf(first.doc) })

    // Next open: the unconfirmed edit is replayed and saved.
    const second = await openTab()
    expect(textOf(second.doc)).toBe(textOf(first.doc))
    await tick(0)
    expect(server.state).toBe(stateOf(second.doc))
    expect(second.a.state).toBe('saved')
    expect(pending()).toBeUndefined()
  })

  it('flushesOnHideAndPagehideOnlyWhenThereAreChanges', async () => {
    const { doc } = await openTab()
    document.dispatchEvent(new Event('visibilitychange'))
    window.dispatchEvent(new Event('pagehide'))
    await tick(0)
    expect(server.puts).toHaveLength(0)

    type(doc, 'a')
    Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'hidden' })
    document.dispatchEvent(new Event('visibilitychange'))
    await tick(0)
    Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'visible' })
    expect(server.puts).toEqual([expect.objectContaining({ keepalive: true, text: 'base a' })])
    await tick(5000)
    expect(server.puts).toHaveLength(1) // the debounced save does not repeat it
  })

  it('replaysPendingOnOpen', async () => {
    const earlier = new Y.Doc()
    Y.applyUpdate(earlier, Y.encodeStateAsUpdate((await openContent(NODE, true)).ydoc))
    type(earlier, 'unsent edit')
    leftover({ state: stateOf(earlier), text: 'base unsent edit', baseRevision: 3 })

    const opened = await openContent(NODE, true)
    expect(textOf(opened.ydoc)).toBe('base unsent edit') // local changes first
    expect(opened.state).toBe('saving')
    const { a } = start(opened)
    await tick(0)
    expect(server.puts).toEqual([{ baseRevision: 3, state: stateOf(opened.ydoc), text: 'base unsent edit', keepalive: false }])
    expect(a.state).toBe('saved')
    expect(pending()).toBeUndefined()
  })

  it('openDropsAPendingCopyTheServerAlreadyHas', async () => {
    // The flush on tab close succeeded, but the tab died before it could clear its local copy.
    leftover({ state: server.state, text: 'base ', baseRevision: 2 })
    const { a } = await openTab()
    await tick(2000)
    expect(a.state).toBe('saved')
    expect(pending()).toBeUndefined()
    expect(server.puts).toHaveLength(0)
  })

  it('viewersNeverReplayAPendingCopy', async () => {
    const other = new Y.Doc()
    type(other, 'stale local')
    leftover({ state: stateOf(other), text: 'stale local', baseRevision: 3 })
    const opened = await openContent(NODE, false)
    expect(textOf(opened.ydoc)).toBe('base ')
    expect(opened.state).toBe('saved')
  })

  it('openWithANewerServerVersionIsAConflict', async () => {
    const mine = new Y.Doc()
    type(mine, 'my offline edit')
    leftover({ state: stateOf(mine), text: 'my offline edit', baseRevision: 2 }) // server is at 3 with other content

    const { a, doc } = await openTab()
    expect(a.state).toBe('conflict')
    expect(textOf(doc)).toBe('my offline edit') // my copy is what I see
    await tick(60_000)
    expect(server.puts).toHaveLength(0) // nothing is overwritten until I choose
    type(doc, '!')
    await tick(2000)
    expect(server.puts).toHaveLength(0)
    expect(pending()).toMatchObject({ baseRevision: 2, text: 'my offline edit!' }) // still a conflict after a reload

    await a.keepMine()
    expect(server.puts).toEqual([expect.objectContaining({ baseRevision: 3, text: 'my offline edit!' })])
    expect(a.state).toBe('saved')
    expect(pending()).toBeUndefined()
  })

  // Review Focus 2: two tabs, same user, same page. Both pass the soft lock and race on the revision.
  it('conflictKeepsLocalCopy', async () => {
    const tabA = await openTab()
    const tabB = await openTab()

    type(tabA.doc, 'from A')
    await tick(1000)
    expect(tabA.a.state).toBe('saved')
    expect(server.revision).toBe(4)

    type(tabB.doc, 'from B')
    await tick(1000)
    expect(server.puts.at(-1)).toMatchObject({ baseRevision: 3, text: 'base from B' }) // rejected with 409
    expect(tabB.a.state).toBe('conflict')
    expect(server.state).toBe(stateOf(tabA.doc)) // A's save is untouched
    expect(textOf(tabB.doc)).toBe('base from B') // B still shows its own text
    expect(pendingOf(tabB.slot)).toMatchObject({ state: stateOf(tabB.doc), text: 'base from B', baseRevision: 3 }) // and it is kept on the device

    // While the banner is up nothing is sent, but typing keeps updating the local copy.
    const putsBefore = server.puts.length
    type(tabB.doc, ' more')
    await tick(60_000)
    expect(server.puts).toHaveLength(putsBefore)
    expect(pendingOf(tabB.slot)).toMatchObject({ text: 'base from B more', baseRevision: 3 })

    // A saving again must not wipe B's local copy.
    type(tabA.doc, '!')
    await tick(1000)
    expect(server.revision).toBe(5)
    expect(pendingOf(tabA.slot)).toBeUndefined()
    expect(pendingOf(tabB.slot)).toMatchObject({ text: 'base from B more', baseRevision: 3 })

    // "Keep mine" saves on top of whatever the server has now.
    await tabB.a.keepMine()
    expect(server.puts.at(-1)).toMatchObject({ baseRevision: 5, text: 'base from B more' })
    expect(server.state).toBe(stateOf(tabB.doc))
    expect(tabB.a.state).toBe('saved')
    expect(pending()).toBeUndefined()
  })

  it('aSaveWhoseAnswerWasLostIsNotAConflict', async () => {
    const { a, doc } = await openTab()
    // The server stores the save, but the response never arrives.
    contentApi.saveContent.mockImplementationOnce(async (_id: string, body: { state: string }) => {
      server.revision++
      server.state = body.state
      throw new TypeError('Failed to fetch')
    })
    type(doc, 'once')
    await tick(1000)
    expect(a.state).toBe('retrying')

    await tick(1000) // retry gets 409, sees its own content on the server
    expect(a.state).toBe('saved')
    expect(pending()).toBeUndefined()

    type(doc, ' twice')
    await tick(1000)
    expect(server.puts.at(-1)).toMatchObject({ baseRevision: 4, text: 'base once twice' })
    expect(a.state).toBe('saved')
  })

  it('offlineShowsOfflineText', async () => {
    const { a, doc } = await openTab()
    setOnline(false)
    contentApi.saveContent.mockRejectedValue(new TypeError('Failed to fetch'))
    type(doc, 'typed offline')
    await tick(1000)

    expect(a.state).toBe('offline')
    render(<SaveIndicator state={a.state} />)
    expect(screen.getByRole('status').textContent).toBe('Offline: changes kept on this device')
    expect(pending()).toMatchObject({ text: 'base typed offline', baseRevision: 3 })

    // Back online: saved without waiting for the next backoff step.
    contentApi.saveContent.mockReset()
    contentApi.saveContent.mockImplementation(async (_id: string, body: { state: string }) => {
      server.state = body.state
      return { revision: ++server.revision, savedAt: '' }
    })
    setOnline(true)
    window.dispatchEvent(new Event('online'))
    await tick(0)
    expect(a.state).toBe('saved')
    expect(server.state).toBe(stateOf(doc))
    expect(pending()).toBeUndefined()
  })

  it('retriesWithBackoff', async () => {
    const { a, doc } = await openTab()
    contentApi.saveContent.mockRejectedValue(new ApiError(500, 'boom'))
    type(doc, 'a')
    await tick(1000)
    expect(a.state).toBe('retrying')
    expect(contentApi.saveContent).toHaveBeenCalledTimes(1)

    let calls = 1
    for (const wait of [1000, 2000, 5000, 10_000, 30_000, 30_000]) {
      await tick(wait - 1)
      expect(contentApi.saveContent).toHaveBeenCalledTimes(calls)
      await tick(1)
      expect(contentApi.saveContent).toHaveBeenCalledTimes(++calls)
    }
    expect(a.state).toBe('retrying')
    expect(pending()).toMatchObject({ text: 'base a' })
  })

  it('leavePromptOnlyWhileChangesAreUnsaved', async () => {
    const { doc } = await openTab()
    const leave = () => {
      const e = new Event('beforeunload', { cancelable: true })
      window.dispatchEvent(e)
      return e.defaultPrevented
    }
    expect(leave()).toBe(false) // saved: no prompt
    type(doc, 'a')
    expect(leave()).toBe(true) // saving
    await tick(1000)
    expect(leave()).toBe(false) // saved again

    contentApi.saveContent.mockRejectedValue(new ApiError(500, 'boom'))
    type(doc, 'b')
    await tick(1000)
    expect(leave()).toBe(true) // retrying
  })

  it('leavePromptAlsoWhileAConflictIsOpen', async () => {
    const mine = new Y.Doc()
    type(mine, 'mine')
    leftover({ state: stateOf(mine), text: 'mine', baseRevision: 2 })
    const { a } = await openTab()
    expect(a.state).toBe('conflict')
    const e = new Event('beforeunload', { cancelable: true })
    window.dispatchEvent(e)
    expect(e.defaultPrevented).toBe(true)
  })

  it('leftoverCopiesFromTwoTabsComeUpNewestFirstAndNoneIsDropped', async () => {
    const older = new Y.Doc()
    type(older, 'older copy')
    const newer = new Y.Doc()
    type(newer, 'newer copy')
    leftover({ state: stateOf(older), text: 'older copy', baseRevision: 2 }, 'tab-1', 100)
    leftover({ state: stateOf(newer), text: 'newer copy', baseRevision: 2 }, 'tab-2', 200)

    const { a, doc, slot } = await openTab()
    expect(slot).toBe('tab-2')
    expect(textOf(doc)).toBe('newer copy')
    await a.keepMine()
    expect(pendingOf('tab-2')).toBeUndefined()
    expect(pendingOf('tab-1')).toMatchObject({ text: 'older copy' }) // still on the device for the next open
  })

  it('leavingThePageSavesAtOnceAndStopsListening', async () => {
    const { a, doc } = await openTab()
    type(doc, 'last words')
    a.destroy()
    await tick(0)
    expect(server.puts).toEqual([expect.objectContaining({ baseRevision: 3, text: 'base last words' })])
    expect(pending()).toBeUndefined()

    type(doc, ' after')
    await tick(5000)
    expect(server.puts).toHaveLength(1)
  })

  it('saveIndicatorTextsAreExact', () => {
    const text = (state: SaveState) => {
      const view = render(<SaveIndicator state={state} />)
      const t = view.getByRole('status').textContent
      view.unmount()
      return t
    }
    expect(text('saved')).toBe('Saved')
    expect(text('saving')).toBe('Saving…')
    expect(text('offline')).toBe('Offline: changes kept on this device')
    expect(text('retrying')).toBe("Couldn't save, retrying")
  })
})
