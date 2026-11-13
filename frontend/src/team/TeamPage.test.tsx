import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ConfirmHost } from '../ui/ConfirmDialog'
import TeamPage from './TeamPage'

const teams = vi.hoisted(() => ({
  getTeam: vi.fn(), getTeamSpaces: vi.fn(), getJoinRequests: vi.fn(), setRole: vi.fn(), removeMember: vi.fn(),
  answerJoinRequest: vi.fn(), createInvite: vi.fn(), createTeamSpace: vi.fn(),
}))
const spaces = vi.hoisted(() => ({ getMe: vi.fn() }))
vi.mock('../api/teams', () => teams)
vi.mock('../api/spaces', () => spaces)

const members = [
  { userId: 'u1', fullName: 'Ada', email: 'a@x', role: 'ADMIN' },
  { userId: 'u2', fullName: 'Bob', email: 'b@x', role: 'EDITOR' },
]

function setup(meId: string) {
  spaces.getMe.mockResolvedValue({ id: meId, fullName: 'x', email: 'x' })
  teams.getTeam.mockResolvedValue({ team: { id: 't1', name: 'Alpha', description: null }, teamMembers: members })
  teams.getTeamSpaces.mockResolvedValue([{ id: 's1', name: 'Docs', description: null }])
  teams.getJoinRequests.mockResolvedValue([{ id: 'r1', fullName: 'Cy', email: 'c@x', status: 'PENDING', requestedAt: '' }])
  teams.setRole.mockResolvedValue({})
  teams.removeMember.mockResolvedValue(null)
  teams.answerJoinRequest.mockResolvedValue({})
  teams.createInvite.mockResolvedValue({ inviteCode: 'abc' })
  render(
    <QueryClientProvider client={new QueryClient()}>
      <ConfirmHost />
      <MemoryRouter initialEntries={['/teams/t1']}><Routes><Route path="/teams/:teamId" element={<TeamPage />} /></Routes></MemoryRouter>
    </QueryClientProvider>,
  )
}

afterEach(() => { cleanup(); vi.clearAllMocks() })

describe('team page', () => {
  it('adminCanRemoveAMemberAfterConfirming', async () => {
    setup('u1')
    await userEvent.click(await screen.findByRole('button', { name: 'Remove' }))
    await userEvent.click(screen.getByRole('button', { name: 'Remove member' }))
    expect(teams.removeMember).toHaveBeenCalledWith('t1', 'u2')
  })

  it('makingAnAdminAsksFirstAndCancelChangesNothing', async () => {
    setup('u1')
    await userEvent.selectOptions(await screen.findByLabelText('Role of Bob'), 'ADMIN')
    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }))
    expect(teams.setRole).not.toHaveBeenCalled()
    await userEvent.selectOptions(screen.getByLabelText('Role of Bob'), 'VIEWER')
    expect(teams.setRole).toHaveBeenCalledWith('t1', 'u2', 'VIEWER')
  })

  it('adminAcceptsRequestsAndGetsAnAppJoinLink', async () => {
    setup('u1')
    await userEvent.click(await screen.findByRole('button', { name: 'Accept' }))
    expect(teams.answerJoinRequest).toHaveBeenCalledWith('t1', 'r1', true)
    await userEvent.click(screen.getByRole('button', { name: 'Create invite link' }))
    expect(((await screen.findByLabelText('Invite link')) as HTMLInputElement).value).toBe(`${location.origin}/app/join/abc`)
  })

  it('editorSeesNoAdminControls', async () => {
    setup('u2')
    await screen.findByText('Alpha')
    expect(screen.queryByRole('button', { name: 'Remove' })).toBeNull()
    expect(screen.queryByText('Join requests')).toBeNull()
    expect(teams.getJoinRequests).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: 'New space' })).toBeTruthy()
  })
})
