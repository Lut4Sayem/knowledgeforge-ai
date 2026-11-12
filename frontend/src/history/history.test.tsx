import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, renderHook, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactNode } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { VersionSummary } from '../api/versions'
import { useEndSessionOnLeave } from '../editor/session'
import DiffView from './DiffView'
import HistoryPanel from './HistoryPanel'
import { groupVersions } from './groupVersions'

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

const NOW = new Date(2026, 10, 12, 15, 0) // Thu 12 Nov 2026, 15:00 local
const at = (day: number, h: number, m = 0) => new Date(2026, 10, day, h, m).toISOString()
const sayem = { userId: 'u1', name: 'Sayem', avatarUrl: null }
const rafi = { userId: 'u2', name: 'Rafi', avatarUrl: null }

function v(id: string, kind: VersionSummary['kind'], createdAt: string, extra: Partial<VersionSummary> = {}): VersionSummary {
  return { id, kind, name: null, authors: [sayem], createdAt, updatedAt: createdAt, private: false, ...extra }
}

const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

function renderPanel(versions: VersionSummary[], canEdit: boolean, onView = vi.fn()) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
    const u = String(input)
    if (u === '/api/nodes/n1/versions') return json(versions)
    const m = u.match(/^\/api\/versions\/(\w+)$/)
    if (m) return json({ ...versions.find((x) => x.id === m[1]), state: '', text: 'text of ' + m[1] })
    return json({})
  })
  vi.stubGlobal('fetch', fetchMock)
  const wrap = ({ children }: { children: ReactNode }) => <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  render(<HistoryPanel nodeId="n1" canEdit={canEdit} viewingId={null} onView={onView} onClose={() => {}} onRestored={() => {}} now={NOW} />, { wrapper: wrap })
  return { onView, fetchMock }
}

describe('groupVersions', () => {
  it('groupsByDayAndSession', () => {
    const groups = groupVersions(
      [
        v('a3', 'AUTO', at(12, 14, 15)),
        v('a2', 'AUTO', at(12, 14, 0)), // 15 min before: same session
        v('n1', 'NAMED', at(12, 11, 2), { name: 'Draft' }),
        v('a1', 'AUTO', at(11, 21, 0)),
        v('old', 'AUTO', at(2, 9, 0)),
      ],
      NOW,
    )
    expect(groups.map((g) => g.label)).toEqual(['Today', 'Yesterday', 'Nov 2'])
    const today = groups[0].items
    expect(today[0]).toMatchObject({ kind: 'session' })
    expect(today[0].kind === 'session' && today[0].versions.map((x) => x.id)).toEqual(['a3', 'a2'])
    expect(today[1]).toMatchObject({ kind: 'version' })
  })

  it('recoveredShownAsPrivateDraft', async () => {
    const list = [
      v('p1', 'RECOVERED', at(12, 10), { private: true }),
      v('p2', 'AUTO', at(12, 9), { private: true }),
      v('a1', 'AUTO', at(12, 8)),
    ]
    const g = groupVersions(list, NOW)[0].items
    const last = g.at(-1)
    expect(last).toMatchObject({ kind: 'private' })
    expect(last?.kind === 'private' && last.versions).toHaveLength(2)

    renderPanel(list, true)
    expect(await screen.findByText('2 private drafts · Only you can see these')).toBeTruthy()
    await userEvent.click(screen.getByRole('button', { name: /2 private drafts/ }))
    expect(screen.getByText('Recovered unsaved changes')).toBeTruthy()
  })
})

describe('history panel', () => {
  const list = [v('n1', 'NAMED', at(12, 11), { name: 'Before submission' }), v('a1', 'AUTO', at(12, 9))]

  it('namedOnlyFilter', async () => {
    renderPanel(list, true)
    expect(await screen.findByText('★ Before submission')).toBeTruthy()
    expect(screen.getAllByRole('button', { name: /AM|PM|\d/ }).length).toBeGreaterThan(1)
    await userEvent.click(screen.getByLabelText('Named versions only'))
    expect(screen.getByText('★ Before submission')).toBeTruthy()
    expect(screen.queryByText(/^9:00/)).toBeNull()
  })

  it('selectingShowsTheVersionAgainstTheOneBefore', async () => {
    const { onView } = renderPanel(list, true)
    await userEvent.click(await screen.findByText('★ Before submission'))
    await waitFor(() => expect(onView).toHaveBeenCalled())
    expect(onView.mock.calls[0][0]).toMatchObject({ version: { id: 'n1' }, text: 'text of n1', previousText: 'text of a1' })
  })

  it('restoreHiddenForViewers', async () => {
    const { onView } = renderPanel(list, false)
    await userEvent.click(await screen.findByText('★ Before submission'))
    await waitFor(() => expect(onView).toHaveBeenCalled())
    expect(onView.mock.calls[0][0].canRestore).toBe(false)
    expect(screen.queryByRole('button', { name: /Name the current version/ })).toBeNull()
  })
})

describe('DiffView', () => {
  it('diffMarksInsertAndDelete', () => {
    const { container } = render(
      <DiffView text="Collect 120 students" previousText="Collect 100 students" authors={[rafi]} />,
    )
    expect(container.querySelector('del')!.textContent).toBe('100')
    expect(container.querySelector('ins')!.textContent).toBe('120')
    expect(screen.getByText("Rafi's changes")).toBeTruthy()
  })

  it('manyAuthorsShareOneColourAndAllNamesAreListed', () => {
    render(<DiffView text="a b" previousText="a" authors={[rafi, sayem]} />)
    expect(screen.getByText("Rafi, Sayem's changes")).toBeTruthy()
  })
})

describe('leaving the page', () => {
  it('leavingPageEndsSession', () => {
    const fetchMock = vi.fn(async () => json(null, 204))
    vi.stubGlobal('fetch', fetchMock)
    const { unmount } = renderHook(() => useEndSessionOnLeave('n9', true))
    expect(fetchMock).not.toHaveBeenCalled()
    fireEvent(window, new Event('pagehide'))
    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    expect(url).toBe('/api/nodes/n9/session/end')
    expect(init).toMatchObject({ method: 'POST', keepalive: true })
    unmount() // route change inside the app
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it('viewersDoNotEndSessions', () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    const { unmount } = renderHook(() => useEndSessionOnLeave('n9', false))
    fireEvent(window, new Event('pagehide'))
    unmount()
    expect(fetchMock).not.toHaveBeenCalled()
  })
})

