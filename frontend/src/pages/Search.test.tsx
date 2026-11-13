import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { SearchHit } from '../api/search'
import Search from './Search'

const api = vi.hoisted(() => ({ searchAll: vi.fn() }))
vi.mock('../api/search', () => api)

const page = (over: Partial<SearchHit>): SearchHit => ({
  type: 'PAGE', nodeId: 'p1', teamId: null, spaceId: 's1', spaceName: 'Docs', title: 'Runbook', path: '',
  snippetHtml: 'the <mark>runbook</mark> &lt;b&gt;', tags: ['ops'], publishState: 'DRAFT', archived: false, ...over,
})

function setup(url: string) {
  render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter initialEntries={[url]}><Search /></MemoryRouter>
    </QueryClientProvider>,
  )
}

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

describe('search page', () => {
  it('emptyBoxShowsHelpAndSendsNothing', () => {
    setup('/search')
    expect(screen.getByText('Find anything your teams have written')).toBeTruthy()
    expect(api.searchAll).not.toHaveBeenCalled()
  })

  it('tagAndStatusGoToTheServer', async () => {
    api.searchAll.mockResolvedValue([page({})])
    setup('/search?tag=ops&status=DRAFT')
    expect(await screen.findByRole('link', { name: 'Runbook' })).toBeTruthy()
    expect(api.searchAll).toHaveBeenCalledWith({ q: '', tag: 'ops', publishState: 'DRAFT', limit: 50 })
    expect(screen.getByText('· Draft')).toBeTruthy()
  })

  it('snippetKeepsMarkAndEscapesTheRest', async () => {
    api.searchAll.mockResolvedValue([page({})])
    setup('/search?q=runbook')
    const mark = await screen.findByText('runbook', { selector: 'mark' })
    expect(mark).toBeTruthy()
    expect(mark.parentElement?.textContent).toBe('the runbook <b>')
  })

  it('removingTheTagChipSearchesAgainWithoutIt', async () => {
    api.searchAll.mockResolvedValue([page({})])
    setup('/search?q=runbook&tag=ops')
    await userEvent.click(await screen.findByRole('button', { name: 'Remove tag filter ops' }))
    expect(screen.queryByRole('button', { name: 'Remove tag filter ops' })).toBeNull()
    expect(api.searchAll).toHaveBeenLastCalledWith({ q: 'runbook', tag: '', publishState: '', limit: 50 })
  })

  it('noMatchOffersClearFilters', async () => {
    api.searchAll.mockResolvedValue([])
    setup('/search?q=zzz&status=DRAFT')
    expect(await screen.findByText('Nothing matches “zzz”')).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Clear filters' })).toBeTruthy()
  })

  it('failureShowsMessageAndRetry', async () => {
    api.searchAll.mockRejectedValue(new Error('boom'))
    setup('/search?q=x')
    expect((await screen.findByRole('alert')).textContent).toContain('Search failed. boom')
    expect(screen.getByRole('button', { name: 'Try again' })).toBeTruthy()
  })
})
