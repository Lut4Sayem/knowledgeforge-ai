import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as Y from 'yjs'
import { ApiError } from '../api/client'
import { Autosave, openContent, RECOVERED_NOTICE, type Opened, type SaveState, type SaveStatus } from './autosave'
import { fromBase64, toBase64 } from './base64'
import type { Pending } from './pendingStore'
import SaveIndicator from './SaveIndicator'

// Fake server with the real revision rule, and an in-memory stand-in for IndexedDB (shared by all "tabs").
interface Put { baseRevision: number; state: string; text: string; keepalive: boolean }
const server = {
  revision: 3,
  epoch: 0,
  state: '',
  puts: [] as Put[],
  recovered: [] as { state: string; text: string; baseEpoch: number }[],
  /** What version restore will do (Task 6.1): replace the state wholesale and start a new epoch. */
  restore(doc: Y.Doc) {
    server.state = stateOf(doc)
    server.revision++
    server.epoch++
  },
}
const contentApi = vi.hoisted(() => ({ getContent: vi.fn(), saveContent: vi.fn(), saveRecovered: vi.fn() }))
vi.mock('../api/content', () => contentApi)
const idb = vi.hoisted(() => ({ slots: new Map<string, Record<string, unknown>>(), bad: [] as string[], broken: false }))
vi.mock('./pendingStore', () => ({
  pendingFor: (userId: string, nodeId: string) => {
    const key = `kf.pending.${userId}.${nodeId}`
    const drop = (slot: string) => {
      const rest = { ...idb.slots.get(key) }
      delete rest[slot]
      idb.slots.set(key, rest)
    }
    return {
      key,
      put: async (slot: string, p: unknown) => {
        if (idb.broken) return false
        idb.slots.set(key, { ...idb.slots.get(key), [slot]: p })
        return true
      },
      all: async () => idb.slots.get(key) ?? {},
      clear: async (slot: string) => drop(slot),
      quarantine: async (slot: string) => {
        idb.bad.push(slot)
        drop(slot)
      },
    }
  },
}))

const NODE = 'n1'
const USER = 'u1'
const KEY = `kf.pending.${USER}.${NODE}`
const pendingOf = (slot: string) => idb.slots.get(KEY)?.[slot] as Pending | undefined
/** The one local copy on the device (fails when there are several). */
const pending = () => {
  const all = Object.values(idb.slots.get(KEY) ?? {}) as Pending[]
  expect(all.length).toBeLessThanOrEqual(1)
  return all[0] as Pending | undefined
}
/** A copy left behind by a tab that is gone. */
const leftover = (p: Partial<Pending> & { state: string; text: string }, slot = 'old') =>
  idb.slots.set(KEY, { ...idb.slots.get(KEY), [slot]: { baseRevision: 3, epoch: 0, at: Date.now(), ...p } })

const stateOf = (doc: Y.Doc) => toBase64(Y.encodeStateAsUpdate(doc))
const textOf = (doc: Y.Doc) => doc.getText('t').toString()
const type = (doc: Y.Doc, s: string) => doc.getText('t').insert(doc.getText('t').length, s)
const tick = (ms: number) => vi.advanceTimersByTimeAsync(ms)
/** A copy of the server's document, like another client would hold. */
const serverDoc = () => {
  const doc = new Y.Doc()
  Y.applyUpdate(doc, fromBase64(server.state))
  return doc
}

const live: Autosave[] = []
function start(opened: Opened) {
  const seen: SaveStatus[] = []
  const a = new Autosave(NODE, opened.ydoc, () => textOf(opened.ydoc), opened, (s) => seen.push(s))
  live.push(a)
  return { a, seen, states: () => seen.map((s) => s.state), doc: opened.ydoc, slot: opened.slot }
}
/** Opens the page like a tab would. */
const openTab = async () => start(await openContent(NODE, true, USER))

function setOnline(value: boolean) {
  Object.defineProperty(navigator, 'onLine', { configurable: true, get: () => value })
}

beforeEach(() => {
  vi.useFakeTimers()
  const base = new Y.Doc()
  type(base, 'base ')
  Object.assign(server, { revision: 3, epoch: 0, state: stateOf(base), puts: [], recovered: [] })
  contentApi.getContent.mockImplementation(async () => ({ format: 'YJS_BLOCKNOTE_V1', state: server.state, revision: server.revision, contentEpoch: server.epoch }))
  contentApi.saveContent.mockImplementation(async (_id: string, body: Omit<Put, 'keepalive'>, keepalive = false) => {
    server.puts.push({ ...body, keepalive })
    if (body.baseRevision !== server.revision) throw new ApiError(409, 'This page changed somewhere else')
    server.revision++
    server.state = body.state
    return { revision: server.revision, savedAt: '2026-11-10T00:00:00Z' }
  })
  contentApi.saveRecovered.mockImplementation(async (_id: string, body: { state: string; text: string; baseEpoch: number }) => {
    server.recovered.push(body)
    return { id: 'v' + server.recovered.length, createdAt: '' }
  })
})

afterEach(() => {
  live.splice(0).forEach((a) => a.destroy())
  cleanup()
  idb.slots.clear()
  idb.bad.length = 0
  idb.broken = false
  vi.clearAllMocks()
  vi.unstubAllGlobals()
  vi.useRealTimers()
  setOnline(true)
})

describe('autosave', () => {
  it('debouncesOneSecond', async () => {
    const { a, doc, states } = await openTab()
    expect(a.state).toBe('saved')

    type(doc, 'a')
    await tick(0)
    expect(pending()).toMatchObject({ state: stateOf(doc), text: 'base a', baseRevision: 3, epoch: 0 }) // local copy is written at once
    expect(a.state).toBe('saving')
    await tick(600)
    type(doc, 'b')
    await tick(999) // 1 s after the LAST change has not passed yet
    expect(server.puts).toHaveLength(0)

    await tick(1)
    expect(server.puts).toEqual([{ baseRevision: 3, state: stateOf(doc), text: 'base ab', keepalive: false }])
    expect(a.state).toBe('saved')
    expect(pending()).toBeUndefined() // confirmed by the server, so the local copy is dropped
    expect(states()).toEqual(['saving', 'saved'])

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
    expect(second.a.state).toBe('saving')
    await tick(1000)
    expect(textOf(serverDoc())).toBe(textOf(first.doc))
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
    const earlier = serverDoc()
    type(earlier, 'unsent edit')
    leftover({ state: stateOf(earlier), text: 'base unsent edit' })

    const opened = await openContent(NODE, true, USER)
    expect(textOf(opened.ydoc)).toBe('base unsent edit') // local changes first
    expect(opened.state).toBe('saving')
    expect(pendingOf('old')).toBeUndefined() // taken over under this session's own slot
    expect(pendingOf(opened.slot)).toMatchObject({ text: 'base unsent edit', baseRevision: 3, epoch: 0 })

    const { a } = start(opened)
    await tick(1000)
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
    leftover({ state: stateOf(other), text: 'stale local' })
    const opened = await openContent(NODE, false, USER)
    expect(textOf(opened.ydoc)).toBe('base ')
    expect(opened.state).toBe('saved')
  })

  it('anotherUsersCopyOnThisBrowserIsNeverOpened', async () => {
    const theirs = serverDoc()
    type(theirs, 'typed by the previous user')
    idb.slots.set(`kf.pending.someoneElse.${NODE}`, { old: { state: stateOf(theirs), text: 'x', baseRevision: 3, epoch: 0, at: Date.now() } })

    const { a, doc } = await openTab()
    await tick(2000)
    expect(textOf(doc)).toBe('base ')
    expect(a.state).toBe('saved')
    expect(server.puts).toHaveLength(0)
  })

  // D6: a stale save is merged, not a choice between "mine" and "theirs". (Review Focus 2: two tabs.)
  it('sameEpochMerges', async () => {
    const tabA = await openTab()
    const tabB = await openTab()

    type(tabA.doc, 'from A. ')
    await tick(1000)
    expect(tabA.a.state).toBe('saved')
    expect(server.revision).toBe(4)

    type(tabB.doc, 'from B. ')
    await tick(1000) // B's save is based on revision 3: 409, merge, save again
    await tick(0)
    expect(tabB.a.state).toBe('saved')
    expect(tabB.states()).not.toContain('retrying')
    expect(textOf(tabB.doc)).toContain('from A. ') // A's text arrived in B's document
    expect(textOf(tabB.doc)).toContain('from B. ')
    expect(server.state).toBe(stateOf(tabB.doc))
    expect(server.revision).toBe(5)
    expect(server.puts.map((p) => p.baseRevision)).toEqual([3, 3, 4])
    expect(pendingOf(tabB.slot)).toBeUndefined()

    // And the other way round on A's next save: nothing of either tab is lost.
    type(tabA.doc, 'A again. ')
    await tick(1000)
    await tick(0)
    expect(tabA.a.state).toBe('saved')
    for (const part of ['base ', 'from A. ', 'from B. ', 'A again. ']) expect(textOf(serverDoc())).toContain(part)
    expect(textOf(tabA.doc)).toBe(textOf(serverDoc()))
    expect(server.recovered).toHaveLength(0)
  })

  it('openMergesAnOldCopyOfTheSameEpoch', async () => {
    const mine = serverDoc()
    type(mine, 'my offline edit. ')
    leftover({ state: stateOf(mine), text: 'base my offline edit. ', baseRevision: 3 })
    const other = serverDoc()
    type(other, 'saved by a teammate. ')
    Object.assign(server, { state: stateOf(other), revision: 4 }) // the page moved on while my copy sat on the device

    const { a, doc } = await openTab()
    expect(textOf(doc)).toContain('my offline edit. ')
    expect(textOf(doc)).toContain('saved by a teammate. ')
    await tick(1000)
    expect(a.state).toBe('saved')
    expect(server.puts).toEqual([expect.objectContaining({ baseRevision: 4, text: textOf(doc) })])
    expect(pending()).toBeUndefined()
  })

  // D6 epoch rule: after a restore the old lineage must not come back through a merge.
  it('restoreThenStaleClientDoesNotResurrectContent', async () => {
    const { a, doc, slot } = await openTab()
    type(doc, 'DELETED BY THE RESTORE ')
    await tick(1000)
    expect(a.state).toBe('saved')

    const restored = new Y.Doc()
    type(restored, 'the restored version')
    server.restore(restored) // epoch 0 -> 1 while this client still sits on epoch 0

    type(doc, 'and my unsaved words')
    const mine = stateOf(doc)
    const putsBefore = server.puts.length
    await tick(1000)

    expect(server.puts).toHaveLength(putsBefore + 1) // the one refused save; nothing else is written to the page
    expect(server.state).toBe(stateOf(restored))
    expect(textOf(serverDoc())).toBe('the restored version')
    expect(server.recovered).toEqual([{ state: mine, text: 'base DELETED BY THE RESTORE and my unsaved words', baseEpoch: 0 }])
    expect(a.state).toBe('recovered')
    expect(a.message).toBe('Your unsaved changes were kept as a private version because this page was restored')
    expect(RECOVERED_NOTICE).toBe('Your unsaved changes were kept as a private version because this page was restored')
    expect(pendingOf(slot)).toBeUndefined() // cleared, but only now that the copy is safe on the server

    // The page then loads the server state: none of the old lineage is in it.
    a.destroy()
    const again = await openTab()
    expect(textOf(again.doc)).toBe('the restored version')
    await tick(5000)
    expect(server.state).toBe(stateOf(restored))
    expect(server.recovered).toHaveLength(1)
  })

  it('aLeftoverCopyFromBeforeARestoreIsKeptPrivatelyOnOpen', async () => {
    const mine = serverDoc()
    type(mine, 'typed before the restore')
    leftover({ state: stateOf(mine), text: 'base typed before the restore', baseRevision: 3, epoch: 0 })
    const restored = new Y.Doc()
    type(restored, 'restored')
    server.restore(restored)

    const { a, doc } = await openTab()
    expect(textOf(doc)).toBe('base typed before the restore') // my copy stays on screen until it is safe
    await tick(1000)
    expect(server.recovered).toEqual([{ state: stateOf(mine), text: 'base typed before the restore', baseEpoch: 0 }])
    expect(server.puts).toHaveLength(0)
    expect(server.state).toBe(stateOf(restored))
    expect(a.state).toBe('recovered')
    expect(pending()).toBeUndefined()
  })

  it('recoveryFailureKeepsLocalCopy', async () => {
    const { a, doc, slot } = await openTab()
    const restored = new Y.Doc()
    type(restored, 'restored')
    server.restore(restored)
    contentApi.saveRecovered.mockRejectedValueOnce(new ApiError(500, 'Database is down'))

    type(doc, 'my unsaved words')
    await tick(1000)

    expect(a.state).toBe('retrying')
    expect(a.message).toContain('Database is down') // the error is shown
    expect(pendingOf(slot)).toMatchObject({ state: stateOf(doc), text: 'base my unsaved words', epoch: 0 }) // still on the device
    expect(textOf(doc)).toBe('base my unsaved words') // and still on screen
    expect(server.state).toBe(stateOf(restored))

    await tick(1000) // first backoff step: retried, this time it works
    expect(server.recovered).toEqual([{ state: stateOf(doc), text: 'base my unsaved words', baseEpoch: 0 }])
    expect(a.state).toBe('recovered')
    expect(pendingOf(slot)).toBeUndefined()
    expect(server.puts.filter((p) => p.baseRevision !== 3)).toHaveLength(0) // never saved over the restore
    expect(server.state).toBe(stateOf(restored))
  })

  it('aSaveWhoseAnswerWasLostIsJustConfirmed', async () => {
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
    const { a, doc, seen } = await openTab()
    setOnline(false)
    contentApi.saveContent.mockRejectedValue(new TypeError('Failed to fetch'))
    type(doc, 'typed offline')
    await tick(1000)

    expect(a.state).toBe('offline')
    render(<SaveIndicator {...seen.at(-1)!} />)
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

  it('offlineWithoutALocalCopyDoesNotClaimOne', async () => {
    const { a, doc, seen } = await openTab()
    idb.broken = true // IndexedDB blocked or full
    setOnline(false)
    contentApi.saveContent.mockRejectedValue(new TypeError('Failed to fetch'))
    type(doc, 'typed offline')
    await tick(1000)

    expect(a.state).toBe('offline')
    expect(seen.at(-1)).toMatchObject({ state: 'offline', local: false })
    render(<SaveIndicator {...seen.at(-1)!} />)
    expect(screen.getByRole('status').textContent).toBe('Offline: changes are only in this open tab')
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

  it('errorsThatCannotSucceedStopRetryingAndSayWhy', async () => {
    const { a, doc } = await openTab()
    contentApi.saveContent.mockRejectedValue(new ApiError(413, 'This page is too large to save'))
    type(doc, 'far too much')
    await tick(1000)

    expect(a.state).toBe('failed')
    expect(a.message).toBe('This page is too large to save')
    expect(pending()).toMatchObject({ text: 'base far too much' }) // the copy stays on the device
    await tick(120_000)
    expect(contentApi.saveContent).toHaveBeenCalledTimes(1) // no blind retry loop

    for (const status of [403, 404]) {
      contentApi.saveContent.mockRejectedValue(new ApiError(status, 'No longer yours to edit'))
      type(doc, '.') // a new change is a reason to try again
      await tick(1000)
      expect(a.state).toBe('failed')
      expect(a.message).toBe('No longer yours to edit')
    }

    contentApi.saveContent.mockImplementation(async (_id: string, body: { state: string }) => {
      server.state = body.state
      return { revision: ++server.revision, savedAt: '' }
    })
    type(doc, ' shorter now')
    expect(a.state).toBe('saving')
    await tick(1000)
    expect(a.state).toBe('saved')
    expect(a.message).toBeUndefined()
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

    contentApi.saveContent.mockRejectedValue(new ApiError(403, 'no'))
    type(doc, 'c')
    await tick(1000)
    expect(leave()).toBe(true) // failed for good: still unsaved
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

  it('copiesUntouchedForAWeekAreDropped', async () => {
    const old = serverDoc()
    type(old, 'eight days old')
    leftover({ state: stateOf(old), text: 'x', at: Date.now() - 8 * 24 * 3600 * 1000 }, 'ancient')
    const recent = serverDoc()
    type(recent, 'six days old')
    leftover({ state: stateOf(recent), text: 'base six days old', at: Date.now() - 6 * 24 * 3600 * 1000 }, 'recent')

    const { doc } = await openTab()
    expect(textOf(doc)).toBe('base six days old')
    expect(pendingOf('ancient')).toBeUndefined()
  })

  it('aCopyThatCannotBeReadIsQuarantinedAndThePageStillOpens', async () => {
    leftover({ state: '!!! not base64 !!!', text: 'x' }, 'garbage')
    leftover({ state: toBase64(new Uint8Array([1, 2, 3, 250, 251, 252, 253])), text: 'x' }, 'truncated')
    const good = serverDoc()
    type(good, 'good copy')
    leftover({ state: stateOf(good), text: 'base good copy' }, 'good')

    const { doc } = await openTab()
    expect(textOf(doc)).toBe('base good copy')
    expect(idb.bad.sort()).toEqual(['garbage', 'truncated'])
    expect(pendingOf('garbage')).toBeUndefined()
  })

  // Review findings 1 and 9: a second tab must not take over (and later clear) a live tab's copy.
  it('aLiveTabsCopyIsLeftAlone', async () => {
    const held = new Set<string>()
    vi.stubGlobal('navigator', {
      onLine: true,
      locks: {
        request(name: string, a: unknown, b?: unknown) {
          const options = typeof a === 'function' ? {} : (a as { ifAvailable?: boolean })
          const run = (typeof a === 'function' ? a : b) as (lock: object | null) => Promise<unknown>
          if (held.has(name)) return options.ifAvailable ? Promise.resolve(run(null)) : new Promise(() => {})
          held.add(name)
          return Promise.resolve(run({ name })).finally(() => held.delete(name))
        },
      },
    })

    const tabA = await openTab()
    contentApi.saveContent.mockRejectedValue(new ApiError(500, 'boom')) // A cannot save right now
    type(tabA.doc, 'unsaved in A')
    await tick(1000)
    expect(pendingOf(tabA.slot)).toMatchObject({ text: 'base unsaved in A' })

    const tabC = await openTab() // the user opens the page in another tab
    expect(tabC.slot).not.toBe(tabA.slot)
    expect(textOf(tabC.doc)).toBe('base ') // C starts from the server, it does not replay A's copy
    expect(pendingOf(tabA.slot)).toMatchObject({ text: 'base unsaved in A' }) // and A's copy is untouched

    tabC.a.destroy() // C closing must not clear A's copy either
    await tick(0)
    expect(pendingOf(tabA.slot)).toMatchObject({ text: 'base unsaved in A' })

    // Once A is really gone its copy is an orphan, and the next open picks it up.
    vi.clearAllTimers()
    held.clear()
    contentApi.saveContent.mockImplementation(async (_id: string, body: { state: string }) => {
      server.state = body.state
      return { revision: ++server.revision, savedAt: '' }
    })
    const tabD = await openTab()
    expect(textOf(tabD.doc)).toBe('base unsaved in A')
    expect(pendingOf(tabA.slot)).toBeUndefined()
    await tick(1000)
    expect(tabD.a.state).toBe('saved')
  })

  it('saveIndicatorTextsAreExact', () => {
    const text = (state: SaveState) => {
      const view = render(<SaveIndicator state={state} local />)
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
