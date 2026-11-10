// Runs the real idb-keyval code against an in-memory IndexedDB, so write ordering is the real thing.
import 'fake-indexeddb/auto'
import { get } from 'idb-keyval'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { saveContent } from '../api/content'
import { clearAllPending, pendingFor, type Pending } from './pendingStore'
import { holdSlot, isHeld } from './slotLock'

const apiMock = vi.hoisted(() => vi.fn())
vi.mock('../api/client', () => ({ api: apiMock }))

const copy = (text: string): Pending => ({ state: 'c3RhdGU=', text, baseRevision: 3, epoch: 0, at: 1 })

beforeEach(async () => {
  await clearAllPending()
  apiMock.mockReset()
  apiMock.mockResolvedValue({ revision: 1, savedAt: '' })
})

describe('pendingStore', () => {
  it('keepsOneCopyPerTabNodeAndUser', async () => {
    const mine = pendingFor('userA', 'n1')
    expect(await mine.put('tabA', copy('from A'))).toBe(true)
    await mine.put('tabB', copy('from B'))
    await pendingFor('userA', 'n2').put('tabA', copy('other page'))
    await pendingFor('userB', 'n1').put('tabA', copy('other user'))
    await mine.put('tabA', copy('from A, later'))

    expect(await mine.all()).toEqual({ tabA: copy('from A, later'), tabB: copy('from B') })

    await mine.clear('tabA') // A was confirmed by the server; B's copy must survive
    expect(await mine.all()).toEqual({ tabB: copy('from B') })
    expect(await pendingFor('userA', 'n2').all()).toEqual({ tabA: copy('other page') })
    expect(await pendingFor('userB', 'n1').all()).toEqual({ tabA: copy('other user') }) // never shown to user A
    expect(await pendingFor('userA', 'never-edited').all()).toEqual({})
  })

  it('writesLandInTheOrderTheyWereIssued', async () => {
    const store = pendingFor('userA', 'n1')
    // Not awaited one by one: typing fires these back to back, and a confirmed save clears right after.
    const done = [
      store.put('tab', copy('1')),
      store.put('tab', copy('2')),
      store.clear('tab'),
      store.put('tab', copy('3')),
      store.put('other', copy('x')),
      store.clear('other'),
    ]
    await Promise.all(done)
    expect(await store.all()).toEqual({ tab: copy('3') })
  })

  it('quarantineMovesABrokenCopyOutOfTheWay', async () => {
    const store = pendingFor('userA', 'n1')
    await store.put('bad', copy('broken'))
    await store.put('good', copy('fine'))
    await store.quarantine('bad')
    expect(await store.all()).toEqual({ good: copy('fine') })
    expect(await get('kf.pending-bad.userA.n1')).toEqual({ bad: copy('broken') }) // kept for a manual rescue
  })

  it('logoutRemovesEveryLocalCopy', async () => {
    await pendingFor('userA', 'n1').put('tab', copy('a'))
    await pendingFor('userB', 'n2').put('tab', copy('b'))
    await pendingFor('userA', 'n3').put('bad', copy('c'))
    await pendingFor('userA', 'n3').quarantine('bad')

    await clearAllPending()
    expect(await pendingFor('userA', 'n1').all()).toEqual({})
    expect(await pendingFor('userB', 'n2').all()).toEqual({})
    expect(await get('kf.pending-bad.userA.n3')).toBeUndefined()
  })

  it('reportsWhenTheCopyCouldNotBeStored', async () => {
    const blocked = () => {
      throw new Error('IndexedDB is blocked') // thrown, not rejected: that is what a refused open looks like
    }
    vi.resetModules()
    vi.doMock('idb-keyval', () => ({ get: blocked, update: blocked, keys: blocked, delMany: blocked }))
    try {
      const broken = await import('./pendingStore')
      const store = broken.pendingFor('userA', 'blocked')
      expect(await store.put('tab', copy('x'))).toBe(false) // the caller must not claim a local copy
      await expect(store.clear('tab')).resolves.toBeUndefined()
      await expect(store.quarantine('tab')).resolves.toBeUndefined()
      expect(await store.all()).toEqual({})
      await expect(broken.clearAllPending()).resolves.toBeUndefined()
    } finally {
      vi.doUnmock('idb-keyval')
      vi.resetModules()
    }
  })
})

describe('slot ownership', () => {
  afterEach(() => vi.unstubAllGlobals())

  /** Minimal Web Locks: enough for one page holding names and asking "is it free". */
  function fakeLocks() {
    const held = new Set<string>()
    return {
      held,
      request(name: string, a: unknown, b?: unknown) {
        const options = typeof a === 'function' ? {} : (a as { ifAvailable?: boolean })
        const run = (typeof a === 'function' ? a : b) as (lock: object | null) => Promise<unknown>
        if (held.has(name)) return options.ifAvailable ? Promise.resolve(run(null)) : new Promise(() => {})
        held.add(name)
        return Promise.resolve(run({ name })).finally(() => held.delete(name))
      },
    }
  }

  it('aHeldSlotIsSeenAsLiveUntilItIsReleased', async () => {
    vi.stubGlobal('navigator', { locks: fakeLocks() })
    expect(await isHeld('kf.pending.u.n.slot1')).toBe(false)
    const release = await holdSlot('kf.pending.u.n.slot1')
    expect(await isHeld('kf.pending.u.n.slot1')).toBe(true)
    expect(await isHeld('kf.pending.u.n.slot2')).toBe(false)
    release() // also what happens by itself when the tab dies
    await Promise.resolve()
    expect(await isHeld('kf.pending.u.n.slot1')).toBe(false)
  })

  it('withoutWebLocksEverySlotCountsAsOrphaned', async () => {
    vi.stubGlobal('navigator', {})
    const release = await holdSlot('x')
    expect(await isHeld('x')).toBe(false)
    release()
  })
})

describe('saveContent', () => {
  const body = (state: string) => ({ baseRevision: 3, state, text: 't' })
  const init = () => (apiMock.mock.calls.at(-1) as unknown as [string, RequestInit & { redirectOn401: boolean }])[1]

  it('flushUsesKeepaliveOnlyWhenTheBodyFitsTheBrowserLimit', async () => {
    await saveContent('n1', body('small'), true)
    expect(init()).toMatchObject({ method: 'PUT', keepalive: true, redirectOn401: false })

    await saveContent('n1', body('x'.repeat(70_000)), true) // over 64 KB: fetch would reject it outright
    expect(init().keepalive).toBe(false)

    await saveContent('n1', body('small'))
    expect(init()).toMatchObject({ keepalive: false, redirectOn401: true })
  })

  it('aSaveThatHangsIsAbortedAfter15Seconds', async () => {
    vi.useFakeTimers()
    try {
      // Like fetch: never answers, rejects when its signal aborts.
      apiMock.mockImplementation((_path: string, i: RequestInit) =>
        new Promise((_resolve, reject) => i.signal!.addEventListener('abort', () => reject(new Error('aborted')))))
      const outcome = saveContent('n1', body('small')).then(() => 'saved', () => 'gave up')
      await vi.advanceTimersByTimeAsync(14_999)
      expect(init().signal!.aborted).toBe(false)
      await vi.advanceTimersByTimeAsync(1)
      expect(await outcome).toBe('gave up')
    } finally {
      vi.useRealTimers()
    }
  })
})
