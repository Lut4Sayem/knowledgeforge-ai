import 'fake-indexeddb/auto'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as Y from 'yjs'
import { ApiError } from '../api/client'
import type { NodeDetail } from '../api/nodes'
import { toBase64 } from '../editor/base64'
import { pendingFor } from '../editor/pendingStore'
import PageView from './PageView'

// jsdom has no matchMedia; Mantine asks it for the colour scheme. Nor layout; BlockNote's side menu asks on mouse moves.
window.matchMedia ??= ((query: string) => ({ matches: false, media: query, addEventListener() {}, removeEventListener() {}, addListener() {}, removeListener() {} })) as never
document.elementsFromPoint ??= () => []

const nodesApi = vi.hoisted(() => ({ getNode: vi.fn(), updateNode: vi.fn(), publishNode: vi.fn() }))
vi.mock('../api/nodes', () => nodesApi)
const spacesApi = vi.hoisted(() => ({ getMe: vi.fn(), getMySpaces: vi.fn() }))
vi.mock('../api/spaces', () => spacesApi)
const contentApi = vi.hoisted(() => ({ getContent: vi.fn(), saveContent: vi.fn(), saveRecovered: vi.fn(), acquireEditLock: vi.fn(), releaseEditLock: vi.fn() }))
vi.mock('../api/content', () => contentApi)

const NODE: NodeDetail = {
  id: 'n1', spaceId: 's1', parentId: null, title: 'Plan', icon: null, tags: [], publishState: 'PUBLISHED', locked: false,
  lockedBy: null, status: 'ACTIVE', access: 'EDIT', canManage: true, breadcrumbs: [], contentUpdatedAt: '',
}
const TRASHED = 'This page is in the trash'

beforeEach(() => {
  nodesApi.getNode.mockResolvedValue(NODE)
  spacesApi.getMe.mockResolvedValue({ id: 'u1', fullName: 'Me', email: 'm@x.test' })
  spacesApi.getMySpaces.mockResolvedValue([])
  contentApi.getContent.mockResolvedValue({ format: 'YJS_BLOCKNOTE_V1', state: '', revision: 0, contentEpoch: 0 })
  contentApi.acquireEditLock.mockResolvedValue({ mine: true, holder: { id: 'u1', name: 'Me' }, expiresAt: '' })
  contentApi.releaseEditLock.mockResolvedValue(null)
})
afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

function mount() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const view = render(
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={['/p/n1']}>
        <Routes>
          <Route path="/p/:nodeId" element={<PageView />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
  return { qc, view }
}
const editorDom = (c: HTMLElement) => c.querySelector('[contenteditable="true"]')

describe('an open page that is trashed, archived or locked meanwhile', () => {
  it('keepsTheEditorTheTextAndTheLocalCopyAndShowsTheServersMessage', async () => {
    // A copy typed earlier that the server never confirmed (what an interrupted session leaves behind).
    const unsaved = new Y.Doc()
    unsaved.getText('t').insert(0, 'typed before the page was trashed')
    await pendingFor('u1', 'n1').put('old-tab', { state: toBase64(Y.encodeStateAsUpdate(unsaved)), text: 'typed before the page was trashed', baseRevision: 0, epoch: 0, at: Date.now() })
    contentApi.saveContent.mockRejectedValue(new ApiError(404, TRASHED)) // the page was trashed by someone else

    const { qc, view } = mount()
    await waitFor(() => expect(editorDom(view.container)).not.toBeNull(), { timeout: 5000 })

    // The replayed copy is saved, the server refuses, and says why. The editor is still there.
    await waitFor(() => expect(screen.getByText(`Not saved: ${TRASHED}`)).toBeTruthy(), { timeout: 5000 })
    expect(editorDom(view.container)).not.toBeNull()
    const kept = Object.values(await pendingFor('u1', 'n1').all())
    expect(kept.length).toBeGreaterThan(0) // never dropped unsent
    expect(kept.some((p) => p.text.includes('typed before the page was trashed'))).toBe(true)

    // The page itself can no longer be read either: the message appears, the editor does not unmount.
    nodesApi.getNode.mockRejectedValue(new ApiError(404, TRASHED))
    await qc.invalidateQueries({ queryKey: ['node', 'n1'] })
    await waitFor(() => expect(screen.getAllByText(TRASHED).length).toBeGreaterThan(0))
    expect(screen.queryByText('This page does not exist.')).toBeNull()
    expect(editorDom(view.container)).not.toBeNull()
    expect(Object.values(await pendingFor('u1', 'n1').all()).length).toBeGreaterThan(0)
  }, 20_000)

  it('aPageThatNeverLoadedShowsOnlyTheError', async () => {
    nodesApi.getNode.mockRejectedValue(new ApiError(404, TRASHED))
    const { view } = mount()
    expect((await screen.findByRole('alert')).textContent).toBe('This page does not exist.')
    expect(editorDom(view.container)).toBeNull()
  })
})
