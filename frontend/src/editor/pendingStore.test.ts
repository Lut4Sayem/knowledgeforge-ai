import { beforeEach, describe, expect, it, vi } from 'vitest'
import { saveContent } from '../api/content'
import { pendingStore, type Pending } from './pendingStore'

const db = vi.hoisted(() => ({ map: new Map<string, unknown>(), broken: false }))
vi.mock('idb-keyval', () => ({
  get: async (k: string) => {
    if (db.broken) throw new Error('IndexedDB is blocked')
    return db.map.get(k)
  },
  update: async (k: string, fn: (old: unknown) => unknown) => {
    if (db.broken) throw new Error('IndexedDB is blocked')
    db.map.set(k, fn(db.map.get(k)))
  },
}))
const apiMock = vi.hoisted(() => vi.fn(async () => ({ revision: 1, savedAt: '' })))
vi.mock('../api/client', () => ({ api: apiMock }))

const copy = (text: string): Pending => ({ state: 'c3RhdGU=', text, baseRevision: 3, at: 1 })

beforeEach(() => {
  db.map.clear()
  db.broken = false
  apiMock.mockClear()
})

describe('pendingStore', () => {
  it('keepsOneCopyPerTabAndNode', async () => {
    await pendingStore.put('n1', 'tabA', copy('from A'))
    await pendingStore.put('n1', 'tabB', copy('from B'))
    await pendingStore.put('n2', 'tabA', copy('other page'))
    await pendingStore.put('n1', 'tabA', copy('from A, later'))

    expect(await pendingStore.all('n1')).toEqual({ tabA: copy('from A, later'), tabB: copy('from B') })

    await pendingStore.clear('n1', 'tabA') // A was confirmed by the server; B's copy must survive
    expect(await pendingStore.all('n1')).toEqual({ tabB: copy('from B') })
    expect(await pendingStore.all('n2')).toEqual({ tabA: copy('other page') })
    expect(await pendingStore.all('never-edited')).toEqual({})
  })

  it('editingStillWorksWithoutIndexedDb', async () => {
    db.broken = true
    await expect(pendingStore.put('n1', 'tabA', copy('x'))).resolves.toBeUndefined()
    await expect(pendingStore.clear('n1', 'tabA')).resolves.toBeUndefined()
    expect(await pendingStore.all('n1')).toEqual({})
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
})
