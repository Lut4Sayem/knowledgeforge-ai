import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ConfirmHost } from '../ui/ConfirmDialog'
import { AskPanel, Comments, Summarize } from './PageExtras'

const x = vi.hoisted(() => ({ getComments: vi.fn(), addComment: vi.fn(), deleteComment: vi.fn(), summarize: vi.fn(), ask: vi.fn() }))
const spaces = vi.hoisted(() => ({ getMe: vi.fn() }))
vi.mock('../api/pageExtras', () => x)
vi.mock('../api/spaces', () => spaces)

const c = (id: string, by: string) => ({ id, content: `text ${id}`, createdAt: '2026-01-01T00:00:00Z', createdByUserId: by, createdByFullName: by, createdByEmail: `${by}@x` })

function wrap(ui: React.ReactNode) {
  render(<QueryClientProvider client={new QueryClient()}><ConfirmHost /><MemoryRouter>{ui}</MemoryRouter></QueryClientProvider>)
}

afterEach(() => { cleanup(); vi.clearAllMocks() })

describe('page comments', () => {
  it('listsAddsAndDeletesOnlyOwnComments', async () => {
    spaces.getMe.mockResolvedValue({ id: 'me' })
    x.getComments.mockResolvedValue([c('1', 'me'), c('2', 'other')])
    x.addComment.mockResolvedValue(c('3', 'me'))
    x.deleteComment.mockResolvedValue({})
    wrap(<Comments nodeId="n1" />)
    expect(await screen.findByText('text 2')).toBeTruthy()
    expect(await screen.findAllByRole('button', { name: 'Delete' })).toHaveLength(1)
    await userEvent.type(screen.getByLabelText('Add a comment'), 'hello')
    await userEvent.click(screen.getByRole('button', { name: 'Comment' }))
    expect(x.addComment).toHaveBeenCalledWith('n1', 'hello')
    await userEvent.click(screen.getByRole('button', { name: 'Delete' }))
    await userEvent.click(screen.getByRole('button', { name: 'Delete comment' }))
    expect(x.deleteComment).toHaveBeenCalledWith('1')
  })

  it('emptyCommentCannotBeSent', async () => {
    spaces.getMe.mockResolvedValue({ id: 'me' })
    x.getComments.mockResolvedValue([])
    wrap(<Comments nodeId="n1" />)
    expect(await screen.findByText(/No comments yet/)).toBeTruthy()
    expect((screen.getByRole('button', { name: 'Comment' }) as HTMLButtonElement).disabled).toBe(true)
  })
})

describe('AI controls', () => {
  it('summarizeShowsTheSummary', async () => {
    x.summarize.mockResolvedValue({ summary: 'Short version', cached: false, trimmed: false, generatedAt: '' })
    wrap(<Summarize nodeId="n1" />)
    await userEvent.click(screen.getByRole('button', { name: 'Summarize' }))
    expect(await screen.findByText('Short version')).toBeTruthy()
  })

  it('summarizeShowsTheServersMessageOnLimit', async () => {
    x.summarize.mockRejectedValue(new Error("You've used today's 20 AI requests. Resets at 00:00 UTC."))
    wrap(<Summarize nodeId="n1" />)
    await userEvent.click(screen.getByRole('button', { name: 'Summarize' }))
    expect((await screen.findByRole('alert')).textContent).toMatch(/Resets at 00:00 UTC/)
  })

  it('askShowsAnswerWithSourceLinksAndHidesWhenNotConfigured', async () => {
    x.ask.mockResolvedValueOnce({ answer: 'Use Atlas', sources: [{ n: 1, id: 'p9', title: 'Search' }], usedDocuments: 1, trimmed: false, cached: false })
    wrap(<AskPanel spaceId="s1" />)
    await userEvent.type(screen.getByLabelText('Question'), 'how?')
    await userEvent.click(screen.getByRole('button', { name: 'Ask' }))
    expect(await screen.findByText('Use Atlas')).toBeTruthy()
    expect(screen.getByRole('link', { name: 'Search' }).getAttribute('href')).toBe('/p/p9')
    x.ask.mockRejectedValueOnce(new Error('AI is not configured on this server'))
    await userEvent.click(screen.getByRole('button', { name: 'Ask' }))
    await vi.waitFor(() => expect(screen.queryByLabelText('Question')).toBeNull())
  })
})
