import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useParams } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { SearchHit } from '../api/search'
import Shell from '../app/Shell'

const api = vi.hoisted(() => ({ searchAll: vi.fn(), getRecent: vi.fn() }))
vi.mock('../api/search', () => api)
vi.mock('../api/spaces', () => ({
  getMe: async () => ({ id: 'u1', fullName: 'Ann', email: 'a@x.test' }),
  getMyTeams: async () => [],
  getMySpaces: async () => [],
}))
vi.mock('../api/nodes', () => ({ getTree: async () => [], createNode: vi.fn() }))

// cmdk needs these; jsdom has neither
globalThis.ResizeObserver ??= class { observe() {} unobserve() {} disconnect() {} }
Element.prototype.scrollIntoView = () => {}

const hit = (id: string, title: string): SearchHit => ({
  type: 'PAGE', nodeId: id, teamId: null, spaceId: 's1', spaceName: 'Docs', title, path: '', snippetHtml: '', tags: [], publishState: 'PUBLISHED', archived: false,
})

function Opened() {
  return <p>opened {useParams().nodeId}</p>
}

function setup() {
  render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter>
        <Routes>
          <Route element={<Shell />}>
            <Route path="/p/:nodeId" element={<Opened />} />
            <Route path="*" element={<p>home</p>} />
          </Route>
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

const dialog = () => screen.findByRole('dialog', { name: 'Search pages and actions' })

beforeEach(() => {
  api.getRecent.mockResolvedValue([hit('p1', 'Roadmap'), hit('p2', 'Standup notes')])
  api.searchAll.mockResolvedValue([])
})
afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

describe('command palette', () => {
  it('opensOnCtrlK', async () => {
    setup()
    expect(screen.queryByRole('dialog')).toBeNull()
    await userEvent.keyboard('{Control>}k{/Control}')
    expect(await dialog()).toBeTruthy()
  })

  it('emptyShowsRecent', async () => {
    setup()
    await userEvent.keyboard('{Control>}k{/Control}')
    expect(await screen.findByText('Roadmap')).toBeTruthy()
    expect(screen.getByText('Standup notes')).toBeTruthy()
  })

  it('enterOpensPage', async () => {
    setup()
    await userEvent.keyboard('{Control>}k{/Control}')
    await screen.findByText('Roadmap')
    await userEvent.keyboard('{ArrowDown}{Enter}') // first item is preselected: down moves to the second
    expect(await screen.findByText('opened p2')).toBeTruthy()
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  it('escRestoresFocus', async () => {
    setup()
    const trigger = await screen.findByRole('button', { name: /^Search/ })
    trigger.focus()
    await userEvent.click(trigger)
    await dialog()
    await userEvent.keyboard('{Escape}')
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
    expect(document.activeElement).toBe(trigger)
  })

  it('staleResponsesIgnored', async () => {
    const answers: Record<string, (r: SearchHit[]) => void> = {}
    api.searchAll.mockImplementation(({ q }: { q: string }) => new Promise((res) => { answers[q] = res }))
    setup()
    await userEvent.keyboard('{Control>}k{/Control}')
    await dialog()
    await userEvent.keyboard('a')
    await waitFor(() => expect(answers.a).toBeTruthy(), { timeout: 2000 })
    await userEvent.keyboard('b')
    await waitFor(() => expect(answers.ab).toBeTruthy(), { timeout: 2000 })
    answers.ab([hit('n', 'Newer answer')])
    expect(await screen.findByText('Newer answer')).toBeTruthy()
    answers.a([hit('o', 'Older answer')]) // arrives late: must not replace the newer one
    await new Promise((r) => setTimeout(r, 50))
    expect(screen.queryByText('Older answer')).toBeNull()
    expect(screen.getByText('Newer answer')).toBeTruthy()
  })
})
