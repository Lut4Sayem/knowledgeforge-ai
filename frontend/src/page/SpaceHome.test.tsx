import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import SpaceHome from './SpaceHome'

const nodes = vi.hoisted(() => ({ getTree: vi.fn() }))
vi.mock('../api/nodes', () => nodes)

const n = (id: string, parentId: string | null, rank: string) => ({ id, parentId, rank, title: id, icon: null, type: 'PAGE', publishState: 'PUBLISHED', locked: false, titleOnly: false })

function setup() {
  render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter initialEntries={['/s/s1']}>
        <Routes>
          <Route path="/s/:spaceId" element={<SpaceHome />} />
          <Route path="/p/:id" element={<p>opened page</p>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

afterEach(() => { cleanup(); vi.clearAllMocks() })

describe('space home', () => {
  it('opensTheFirstPage', async () => {
    nodes.getTree.mockResolvedValue([n('a', null, 'a0')])
    setup()
    expect(await screen.findByText('opened page')).toBeTruthy()
  })

  it('saysSoWhenTheProjectIsEmpty', async () => {
    nodes.getTree.mockResolvedValue([])
    setup()
    expect(await screen.findByText(/no pages yet/)).toBeTruthy()
  })
})
