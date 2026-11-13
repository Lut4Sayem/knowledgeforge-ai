import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import MoveToTeamDialog from './MoveToTeamDialog'

const api = vi.hoisted(() => ({ getSubtreeCount: vi.fn(), getTree: vi.fn(), moveToTeam: vi.fn() }))
const spaces = vi.hoisted(() => ({ getMySpaces: vi.fn() }))
vi.mock('../api/nodes', () => api)
vi.mock('../api/spaces', () => spaces)

const space = (id: string, name: string, role: string, teamId = 't1') =>
  ({ id, name, icon: null, ownerType: 'TEAM', teamId, teamName: 'CSE391 Team', myRole: role })
const row = (id: string, title: string, parentId: string | null, titleOnly = false) =>
  ({ id, parentId, rank: id, title, icon: null, type: 'PAGE', publishState: 'DRAFT', locked: false, titleOnly })

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

function mount(onClose = vi.fn()) {
  api.getSubtreeCount.mockResolvedValue({ count: 3 })
  api.getTree.mockResolvedValue([row('a', 'Overview', null), row('b', 'Budget', null, true)])
  api.moveToTeam.mockResolvedValue({})
  spaces.getMySpaces.mockResolvedValue([
    { id: 'me', name: 'Private', icon: null, ownerType: 'USER', teamId: null, teamName: null, myRole: null },
    space('s1', 'Thesis', 'EDITOR'),
    space('s2', 'Read only', 'VIEWER'),
  ])
  render(
    <QueryClientProvider client={new QueryClient()}>
      <MoveToTeamDialog node={{ id: 'n1', spaceId: 'me', title: 'Interview plan' }} onClose={onClose} />
    </QueryClientProvider>,
  )
  return onClose
}

describe('MoveToTeamDialog', () => {
  it('listsOnlyEditableParents', async () => {
    mount()
    expect((await screen.findByRole('radio', { name: 'Overview' })).hasAttribute('disabled')).toBe(false)
    expect(screen.getByRole('radio', { name: "Budget (you can't edit here)" }).hasAttribute('disabled')).toBe(true)
    // projects I can only view, and my private space, are not offered
    expect(screen.queryByRole('option', { name: 'Read only' })).toBeNull()
    expect(screen.queryByRole('option', { name: 'Private' })).toBeNull()
    expect(await screen.findByText(/includes 3 sub-pages/)).toBeTruthy()
  })

  it('movesUnderTheChosenParent', async () => {
    const onClose = mount()
    await userEvent.click(await screen.findByRole('radio', { name: 'Overview' }))
    await userEvent.click(screen.getByRole('button', { name: 'Move to team' }))
    await waitFor(() => expect(api.moveToTeam).toHaveBeenCalledWith('n1', { spaceId: 's1', parentId: 'a' }))
    await waitFor(() => expect(onClose).toHaveBeenCalled())
  })

  it('showsTheServerMessageWhenTheMoveFails', async () => {
    mount()
    api.moveToTeam.mockRejectedValue(new Error("You can't edit this space"))
    await userEvent.click(await screen.findByRole('radio', { name: 'Overview' }))
    await userEvent.click(screen.getByRole('button', { name: 'Move to team' }))
    expect((await screen.findByRole('alert')).textContent).toContain("You can't edit this space")
  })
})
