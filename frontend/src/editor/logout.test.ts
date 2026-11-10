import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as Y from 'yjs'
import { ApiError } from '../api/client'
import { fromBase64, toBase64 } from './base64'
import { logOut, sendAllPending } from './logout'
import type { Pending } from './pendingStore'

// Fake server per page, and an in-memory stand-in for IndexedDB.
interface Page { revision: number; epoch: number; state: string; puts: number; down?: boolean }
const pages = new Map<string, Page>()
const recovered: { nodeId: string; state: string; text: string; baseEpoch: number }[] = []
const contentApi = vi.hoisted(() => ({ getContent: vi.fn(), saveContent: vi.fn(), saveRecovered: vi.fn() }))
vi.mock('../api/content', () => contentApi)
const apiMock = vi.hoisted(() => vi.fn())
vi.mock('../api/client', async (original) => ({ ...(await original<typeof import('../api/client')>()), api: apiMock }))
vi.mock('./docText', () => ({ docText: (doc: Y.Doc) => doc.getText('t').toString() }))

const idb = vi.hoisted(() => new Map<string, Record<string, unknown>>())
vi.mock('./pendingStore', () => {
  const pendingFor = (userId: string, nodeId: string) => {
    const key = `kf.pending.${userId}.${nodeId}`
    return {
      key,
      put: async (slot: string, p: unknown) => (idb.set(key, { ...idb.get(key), [slot]: p }), true),
      all: async () => idb.get(key) ?? {},
      clear: async (slot: string) => {
        const rest = { ...idb.get(key) }
        delete rest[slot]
        idb.set(key, rest)
      },
      quarantine: async () => {},
    }
  }
  const nodesWith = (prefix: string) => [...idb.keys()].filter((k) => k.startsWith(prefix)).map((k) => k.slice(prefix.length))
  return {
    pendingFor,
    pendingPages: async (userId: string) => ({ nodes: nodesWith(`kf.pending.${userId}.`), unreadable: nodesWith(`kf.pending-bad.${userId}.`) }),
    clearPendingOf: async (userId: string) => {
      for (const k of [...idb.keys()]) if (k.startsWith(`kf.pending.${userId}.`) || k.startsWith(`kf.pending-bad.${userId}.`)) idb.delete(k)
    },
  }
})

const ME = 'me'
const stateOf = (doc: Y.Doc) => toBase64(Y.encodeStateAsUpdate(doc))
const textOf = (doc: Y.Doc) => doc.getText('t').toString()
const docOf = (state: string) => {
  const doc = new Y.Doc()
  Y.applyUpdate(doc, fromBase64(state))
  return doc
}
const type = (doc: Y.Doc, s: string) => doc.getText('t').insert(doc.getText('t').length, s)
const slotsOf = (nodeId: string, user = ME) => idb.get(`kf.pending.${user}.${nodeId}`) ?? {}

/** A page on the server plus one unsaved local copy of it that adds `typed`. */
function pageWithUnsaved(nodeId: string, typed: string, copy: Partial<Pending> = {}, user = ME) {
  const base = new Y.Doc()
  type(base, 'base ')
  pages.set(nodeId, { revision: 3, epoch: 0, state: stateOf(base), puts: 0 })
  const local = docOf(stateOf(base))
  type(local, typed)
  const key = `kf.pending.${user}.${nodeId}`
  idb.set(key, { ...idb.get(key), ['slot-' + typed]: { state: stateOf(local), text: 'base ' + typed, baseRevision: 3, epoch: 0, at: 1, ...copy } })
  return local
}

let assign: ReturnType<typeof vi.fn>
const confirmDialog = vi.hoisted(() => vi.fn())
vi.mock('../ui/ConfirmDialog', () => ({ confirmDialog }))
let confirm: ReturnType<typeof vi.fn>

beforeEach(() => {
  contentApi.getContent.mockImplementation(async (id: string) => {
    const p = pages.get(id)!
    if (p.down) throw new TypeError('Failed to fetch')
    return { format: 'YJS_BLOCKNOTE_V1', state: p.state, revision: p.revision, contentEpoch: p.epoch }
  })
  contentApi.saveContent.mockImplementation(async (id: string, body: { baseRevision: number; state: string }) => {
    const p = pages.get(id)!
    p.puts++
    if (body.baseRevision !== p.revision) throw new ApiError(409, 'stale')
    p.state = body.state
    return { revision: ++p.revision, savedAt: '' }
  })
  contentApi.saveRecovered.mockImplementation(async (id: string, body: { state: string; text: string; baseEpoch: number }) => {
    if (pages.get(id)!.down) throw new TypeError('Failed to fetch')
    recovered.push({ nodeId: id, ...body })
    return { id: 'v', createdAt: '' }
  })
  apiMock.mockResolvedValue(null)
  assign = vi.fn()
  confirmDialog.mockReset()
  confirmDialog.mockResolvedValue(true)
  confirm = confirmDialog
  vi.stubGlobal('location', { assign })
})

afterEach(() => {
  pages.clear()
  recovered.length = 0
  idb.clear()
  vi.clearAllMocks()
  vi.unstubAllGlobals()
})

describe('logging out with unsaved local copies', () => {
  it('sendsEveryPendingCopyFirstAndThenLogsOutWithoutAsking', async () => {
    pageWithUnsaved('merge', 'typed offline')
    pageWithUnsaved('two-slots', 'tab one. ')
    pageWithUnsaved('two-slots-b', 'x')
    // a second copy of the same page from another tab
    const other = docOf(pages.get('two-slots')!.state)
    type(other, 'tab two. ')
    idb.set(`kf.pending.${ME}.two-slots`, { ...slotsOf('two-slots'), other: { state: stateOf(other), text: 'base tab two. ', baseRevision: 3, epoch: 0, at: 2 } })
    // a copy from before a restore: must not be merged
    const old = pageWithUnsaved('restored', 'from the old lineage')
    const restoredDoc = new Y.Doc()
    type(restoredDoc, 'restored')
    Object.assign(pages.get('restored')!, { state: stateOf(restoredDoc), revision: 4, epoch: 1 })
    // a copy the server already has
    pageWithUnsaved('already-there', 'sent before')
    pages.get('already-there')!.state = (Object.values(slotsOf('already-there'))[0] as Pending).state

    expect(await logOut(ME)).toBe(true)

    expect(textOf(docOf(pages.get('merge')!.state))).toBe('base typed offline')
    const both = textOf(docOf(pages.get('two-slots')!.state))
    expect(both).toContain('tab one. ')
    expect(both).toContain('tab two. ')
    expect(pages.get('two-slots')!.puts).toBe(1) // one save for the page, both copies merged
    expect(pages.get('restored')!.state).toBe(stateOf(restoredDoc)) // untouched
    expect(recovered).toEqual([{ nodeId: 'restored', state: stateOf(old), text: 'base from the old lineage', baseEpoch: 0 }])
    expect(pages.get('already-there')!.puts).toBe(0)
    for (const id of pages.keys()) expect(slotsOf(id), id).toEqual({})

    expect(confirm).not.toHaveBeenCalled()
    expect(apiMock).toHaveBeenCalledWith('/logout', expect.objectContaining({ method: 'POST' }))
    expect(assign).toHaveBeenCalledWith('/app/login')
  })

  it('aSaveThatRacesWithAnotherWriterIsMergedAgain', async () => {
    pageWithUnsaved('busy', 'mine. ')
    contentApi.saveContent.mockImplementationOnce(async () => {
      // someone saved between our read and our write
      const p = pages.get('busy')!
      const theirs = docOf(p.state)
      type(theirs, 'theirs. ')
      Object.assign(p, { state: stateOf(theirs), revision: p.revision + 1 })
      throw new ApiError(409, 'stale')
    })
    expect(await sendAllPending(ME)).toBe(0)
    const text = textOf(docOf(pages.get('busy')!.state))
    expect(text).toContain('mine. ')
    expect(text).toContain('theirs. ')
    expect(slotsOf('busy')).toEqual({})
  })

  it('whatCouldNotBeSentIsAskedAboutAndStayingKeepsIt', async () => {
    pageWithUnsaved('ok', 'fine')
    pageWithUnsaved('down-1', 'stuck one')
    pageWithUnsaved('down-2', 'stuck two')
    pages.get('down-1')!.down = true
    pages.get('down-2')!.down = true
    confirm.mockResolvedValue(false) // "no, stay"

    expect(await logOut(ME)).toBe(false)

    expect(confirm).toHaveBeenCalledWith(expect.objectContaining({ title: 'Log out anyway?', body: expect.stringContaining('on 2 pages that') }))
    expect(slotsOf('ok')).toEqual({}) // confirmed by the server, so cleared
    expect(Object.values(slotsOf('down-1'))).toMatchObject([{ text: 'base stuck one' }]) // still on the device
    expect(Object.values(slotsOf('down-2'))).toMatchObject([{ text: 'base stuck two' }])
    expect(apiMock).not.toHaveBeenCalled() // still logged in
    expect(assign).not.toHaveBeenCalled()
  })

  it('loggingOutAnywayClearsWhatIsLeftOfThisUserOnly', async () => {
    pageWithUnsaved('down', 'stuck')
    pages.get('down')!.down = true
    pageWithUnsaved('theirs', 'someone else on this browser', {}, 'other-user')
    idb.set(`kf.pending-bad.${ME}.broken`, { slot: { state: '!!!', text: '', baseRevision: 0, epoch: 0, at: 1 } }) // unreadable: cannot be sent either
    confirm.mockResolvedValue(true)

    expect(await logOut(ME)).toBe(true)

    expect(confirm).toHaveBeenCalledWith(expect.objectContaining({ title: 'Log out anyway?', body: expect.stringContaining('on 2 pages that') }))
    expect([...idb.keys()].filter((k) => k.includes(`.${ME}.`))).toEqual([])
    expect(Object.values(slotsOf('theirs', 'other-user'))).toHaveLength(1) // not ours to send or to delete
    expect(pages.get('theirs')!.puts).toBe(0)
    expect(apiMock).toHaveBeenCalledWith('/logout', expect.objectContaining({ method: 'POST' }))
    expect(assign).toHaveBeenCalledWith('/app/login')
  })

  it('onePageIsSingular', async () => {
    pageWithUnsaved('down', 'stuck')
    pages.get('down')!.down = true
    confirm.mockResolvedValue(false)
    await logOut(ME)
    expect(confirm).toHaveBeenCalledWith(expect.objectContaining({ title: 'Log out anyway?', body: expect.stringContaining('on 1 page that') }))
  })
})
