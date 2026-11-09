import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import PageTree from './PageTree'

const nodesApi = vi.hoisted(() => ({ getTree: vi.fn(), createNode: vi.fn(), moveNode: vi.fn() }))
vi.mock('../api/nodes', () => nodesApi)

const Where = () => <p data-testid="where">{useLocation().pathname}</p>

function renderTree() {
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter>
        <PageTree spaceId="s1" spaceName="Thesis" />
        <Routes><Route path="*" element={<Where />} /></Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

afterEach(() => {
  cleanup()
  localStorage.clear()
  Object.values(nodesApi).forEach((f) => f.mockReset())
})

describe('PageTree', () => {
  it('newPageButtonCreatesAndNavigates', async () => {
    nodesApi.getTree.mockResolvedValue([])
    nodesApi.createNode.mockResolvedValue({ id: 'new1' })
    renderTree()

    await userEvent.click(await screen.findByRole('button', { name: /new page/i }))

    expect(nodesApi.createNode).toHaveBeenCalledWith('s1', { parentId: null })
    await waitFor(() => expect(screen.getByTestId('where').textContent).toBe('/p/new1'))
  })

  it('subPageButtonCreatesUnderThatPageAndOpensIt', async () => {
    nodesApi.getTree.mockResolvedValue([
      { id: 'a', parentId: null, rank: 'V', title: 'Alpha', icon: null, type: 'PAGE', publishState: 'PUBLISHED', locked: false, titleOnly: false },
    ])
    nodesApi.createNode.mockResolvedValue({ id: 'child1' })
    renderTree()

    await userEvent.click(await screen.findByRole('button', { name: 'New sub-page under Alpha' }))

    expect(nodesApi.createNode).toHaveBeenCalledWith('s1', { parentId: 'a' })
    await waitFor(() => expect(screen.getByTestId('where').textContent).toBe('/p/child1'))
    expect(JSON.parse(localStorage.getItem('kf.expanded.s1')!)).toEqual(['a']) // parent stays open
  })

  it('showsTheServerMessageWhenCreateFails', async () => {
    nodesApi.getTree.mockResolvedValue([])
    nodesApi.createNode.mockRejectedValue(new Error("You can't add pages to this space"))
    renderTree()

    await userEvent.click(await screen.findByRole('button', { name: /new page/i }))

    expect((await screen.findByRole('alert')).textContent).toBe("You can't add pages to this space")
  })
})
