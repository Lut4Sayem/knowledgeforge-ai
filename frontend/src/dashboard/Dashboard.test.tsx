import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import Dashboard from './Dashboard'

const api = vi.hoisted(() => ({ getDashboard: vi.fn(), createTeam: vi.fn() }))
vi.mock('../api/teams', () => api)

const data = {
  teamCount: 1, spaceCount: 1, documentCount: 1,
  teams: [{ teamId: 't1', name: 'Alpha', description: null, role: 'ADMIN', spaceCount: 2, documentCount: 5 }],
  recentDocuments: [{ documentId: 'n1', title: 'Runbook', status: 'PUBLISHED', updatedAt: new Date().toISOString(), teamName: 'Alpha', spaceName: 'Docs' }],
  recentActivity: [],
}

function setup() {
  render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter initialEntries={['/']}>
        <Routes>
          <Route path="/" element={<Dashboard />} />
          <Route path="/teams/:teamId" element={<p>team page</p>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

afterEach(() => { cleanup(); vi.clearAllMocks() })

describe('dashboard', () => {
  it('showsTeamsWithCountsRecentPagesAndEmptyActivity', async () => {
    api.getDashboard.mockResolvedValue(data)
    setup()
    expect((await screen.findByRole('link', { name: 'Alpha' })).getAttribute('href')).toBe('/teams/t1')
    expect(screen.getByText(/2 spaces · 5 pages/)).toBeTruthy()
    expect(screen.getByRole('link', { name: 'Runbook' }).getAttribute('href')).toBe('/p/n1')
    expect(screen.getByText(/Nothing yet/)).toBeTruthy()
  })

  it('newTeamNeedsANameThenOpensTheTeam', async () => {
    api.getDashboard.mockResolvedValue(data)
    api.createTeam.mockResolvedValue({ id: 't9' })
    setup()
    await userEvent.click(await screen.findByRole('button', { name: 'New team' }))
    expect((screen.getByRole('button', { name: 'Create a team' }) as HTMLButtonElement).disabled).toBe(true)
    await userEvent.type(screen.getByLabelText('Team name'), 'Beta')
    await userEvent.click(screen.getByRole('button', { name: 'Create a team' }))
    expect(api.createTeam).toHaveBeenCalledWith('Beta', '')
    expect(await screen.findByText('team page')).toBeTruthy()
  })
})
