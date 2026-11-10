import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Branch } from '../api/nodes'
import { ConfirmHost } from '../ui/ConfirmDialog'
import { confirmTrash } from './ConfirmTrashDialog'
import TrashPage, { daysLeft, timeAgo } from './TrashPage'

const api = vi.hoisted(() => ({ getTrash: vi.fn(), getArchived: vi.fn(), restoreBranch: vi.fn(), unarchiveNode: vi.fn(), deleteForever: vi.fn() }))
vi.mock('../api/nodes', () => api)
const spaces = vi.hoisted(() => ({ getMySpaces: vi.fn() }))
vi.mock('../api/spaces', () => spaces)

const DAY = 24 * 60 * 60 * 1000
const branch = (over: Partial<Branch> = {}): Branch => ({
  id: 'b1', title: 'Old outline', icon: null, path: [{ id: 'p', title: 'Overview' }], descendantCount: 4,
  by: { id: 'u', name: 'Siam' }, at: new Date(Date.now() - 2 * DAY).toISOString(), purgeAt: new Date(Date.now() + 28 * DAY).toISOString(), parentGone: false, canRestore: true, ...over,
})

beforeEach(() => {
  spaces.getMySpaces.mockResolvedValue([{ id: 's1', name: 'Thesis', icon: null, ownerType: 'TEAM', teamId: 't', teamName: 'T', myRole: 'ADMIN' }])
  api.getArchived.mockResolvedValue({ canPurge: true, items: [] })
})
afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

function mount(url = '/s/s1/trash') {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter initialEntries={[url]}>
        <ConfirmHost />
        <Routes>
          <Route path="/s/:spaceId/trash" element={<TrashPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('trash page', () => {
  it('listsBranchesWithWasInDeletedByAndDaysLeft', async () => {
    api.getTrash.mockResolvedValue({ canPurge: true, items: [branch()] })
    mount()
    const row = (await screen.findByRole('row', { name: /Old outline/ }))
    expect(within(row).getByText('+ 4 sub-pages')).toBeTruthy()
    expect(row.textContent).toContain('Thesis / Overview')
    expect(row.textContent).toContain('Siam · 2 days ago')
    expect(row.textContent).toContain('28 days')
  })

  it('hidesRestoreAndUnarchiveFromPeopleWhoCannotUseThem', async () => {
    api.getTrash.mockResolvedValue({ canPurge: false, items: [branch({ canRestore: false }), branch({ id: 'b2', title: 'Mine', canRestore: true })] })
    api.getArchived.mockResolvedValue({ canPurge: false, items: [branch({ id: 'a1', title: 'Old archive', canRestore: false })] })
    mount()
    const viewerRow = await screen.findByRole('row', { name: /Old outline/ })
    expect(within(viewerRow).queryByRole('button')).toBeNull()
    expect(within(screen.getByRole('row', { name: /Mine/ })).getByRole('button', { name: 'Restore' })).toBeTruthy()
    await userEvent.click(screen.getByRole('tab', { name: /Archived/ }))
    const row = await screen.findByRole('row', { name: /Old archive/ })
    expect(within(row).queryByRole('button', { name: 'Unarchive' })).toBeNull()
  })

  it('deleteForeverOnlyForAdmins', async () => {
    api.getTrash.mockResolvedValue({ canPurge: false, items: [branch()] })
    mount()
    await screen.findByRole('row', { name: /Old outline/ })
    expect(screen.queryByRole('button', { name: 'Delete forever' })).toBeNull()
    expect(screen.getByText('Only team admins can delete forever.')).toBeTruthy()
    cleanup()

    api.getTrash.mockResolvedValue({ canPurge: true, items: [branch()] })
    api.deleteForever.mockResolvedValue(null)
    mount()
    await userEvent.click(await screen.findByRole('button', { name: 'Delete forever' }))
    const dialog = await screen.findByRole('dialog', { name: 'Delete "Old outline" forever?' })
    expect(dialog.textContent).toContain('its 4 sub-pages')
    expect(api.deleteForever).not.toHaveBeenCalled()
    await userEvent.click(within(dialog).getByRole('button', { name: 'Delete forever' }))
    await waitFor(() => expect(api.deleteForever).toHaveBeenCalledWith('b1'))
  })

  it('cancellingDeleteForeverDeletesNothing', async () => {
    api.getTrash.mockResolvedValue({ canPurge: true, items: [branch()] })
    mount()
    await userEvent.click(await screen.findByRole('button', { name: 'Delete forever' }))
    await userEvent.click(await screen.findByRole('button', { name: 'Cancel' }))
    expect(api.deleteForever).not.toHaveBeenCalled()
  })

  it('restoreToTopShowsNotice', async () => {
    api.getTrash.mockResolvedValue({ canPurge: true, items: [branch({ parentGone: true })] })
    api.restoreBranch.mockResolvedValue({ movedToTop: true })
    mount()
    const row = await screen.findByRole('row', { name: /Old outline/ })
    expect(row.textContent).toContain('restores to the top of Thesis') // said before the click
    await userEvent.click(within(row).getByRole('button', { name: 'Restore' }))
    const note = await screen.findByRole('status')
    expect(note.textContent).toBe('"Old outline" was restored to the top level of Thesis, because its parent was deleted or is in the trash.')
    expect(api.restoreBranch).toHaveBeenCalledWith('b1')
  })

  it('plainRestoreSaysSoWithoutTheTopNotice', async () => {
    api.getTrash.mockResolvedValue({ canPurge: true, items: [branch()] })
    api.restoreBranch.mockResolvedValue({ movedToTop: false })
    mount()
    await userEvent.click(await screen.findByRole('button', { name: 'Restore' }))
    expect((await screen.findByRole('status')).textContent).toBe('"Old outline" was restored.')
  })

  it('archivedTabOffersUnarchiveAndNoDelete', async () => {
    api.getTrash.mockResolvedValue({ canPurge: true, items: [] })
    api.getArchived.mockResolvedValue({ canPurge: true, items: [branch({ purgeAt: null })] })
    api.unarchiveNode.mockResolvedValue({ movedToTop: false })
    mount('/s/s1/trash?tab=archived')
    await userEvent.click(await screen.findByRole('button', { name: 'Unarchive' }))
    expect(api.unarchiveNode).toHaveBeenCalledWith('b1')
    expect(screen.queryByRole('button', { name: 'Delete forever' })).toBeNull()
  })

  it('refusedRestoreShowsTheServersMessage', async () => {
    api.getTrash.mockResolvedValue({ canPurge: true, items: [branch()] })
    api.restoreBranch.mockRejectedValue(new Error('This page was trashed together with another page. Restore that page instead.'))
    mount()
    await userEvent.click(await screen.findByRole('button', { name: 'Restore' }))
    expect((await screen.findByRole('alert')).textContent).toContain('Restore that page instead.')
  })

  it('emptyTrashSaysSo', async () => {
    api.getTrash.mockResolvedValue({ canPurge: true, items: [] })
    mount()
    expect(await screen.findByText('The trash is empty.')).toBeTruthy()
  })
})

describe('trash confirm text', () => {
  it('confirmShowsSubpageCount', async () => {
    render(<ConfirmHost />)
    void confirmTrash('Methodology', 2)
    const dialog = await screen.findByRole('dialog', { name: 'Move "Methodology" to trash?' })
    expect(dialog.textContent).toContain('This also moves its 2 sub-pages. You can restore them from the trash for 30 days.')
  })
})

describe('time helpers', () => {
  const now = Date.parse('2026-11-10T12:00:00Z')
  it('timeAgo', () => {
    expect(timeAgo('2026-11-10T11:59:30Z', now)).toBe('just now')
    expect(timeAgo('2026-11-10T09:00:00Z', now)).toBe('3 hours ago')
    expect(timeAgo('2026-11-09T12:00:00Z', now)).toBe('yesterday')
    expect(timeAgo('2026-11-03T12:00:00Z', now)).toBe('7 days ago')
  })
  it('daysLeftRoundsUpAndNeverGoesNegative', () => {
    expect(daysLeft('2026-11-11T11:00:00Z', now)).toBe('1 day')
    expect(daysLeft('2026-12-08T12:00:00Z', now)).toBe('28 days')
    expect(daysLeft('2026-11-09T12:00:00Z', now)).toBe('0 days')
  })
})
