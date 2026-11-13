import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import Profile from './Profile'

const spaces = vi.hoisted(() => ({ getMe: vi.fn(), getMyTeams: vi.fn() }))
const client = vi.hoisted(() => ({ api: vi.fn() }))
vi.mock('../api/spaces', () => spaces)
vi.mock('../api/client', () => client)

function setup(picture: string | null = null) {
  spaces.getMe.mockResolvedValue({ id: 'u1', fullName: 'Ada', email: 'a@x', profilePicture: picture })
  spaces.getMyTeams.mockResolvedValue([{ teamId: 't1', teamName: 'Alpha', role: 'ADMIN' }])
  client.api.mockResolvedValue({ id: 'u1', fullName: 'Ada L', email: 'a@x', profilePicture: null })
  render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter><Profile /></MemoryRouter>
    </QueryClientProvider>,
  )
}

afterEach(() => { cleanup(); vi.clearAllMocks() })

describe('profile page', () => {
  it('savesANewNameAndListsTeams', async () => {
    setup()
    const input = await screen.findByLabelText('Name')
    expect(await screen.findByRole('link', { name: 'Alpha' })).toBeTruthy()
    await userEvent.clear(input)
    await userEvent.type(input, 'Ada L')
    await userEvent.click(screen.getByRole('button', { name: 'Save' }))
    expect(client.api).toHaveBeenCalledWith('/api/me', { method: 'PUT', body: JSON.stringify({ fullName: 'Ada L' }) })
  })

  it('blankNameCannotBeSaved', async () => {
    setup()
    await userEvent.clear(await screen.findByLabelText('Name'))
    expect((screen.getByRole('button', { name: 'Save' }) as HTMLButtonElement).disabled).toBe(true)
    expect(screen.getByText(/Enter your name/)).toBeTruthy()
  })

  it('rejectsAWrongTypeBeforeUploadAndUploadsAGoodImage', async () => {
    setup()
    const input = await screen.findByLabelText('Profile picture')
    await userEvent.upload(input, new File(['x'], 'a.txt', { type: 'text/plain' }), { applyAccept: false })
    expect(await screen.findByText('Use a PNG, JPG, GIF or WebP image.')).toBeTruthy()
    expect(client.api).not.toHaveBeenCalled()
    await userEvent.upload(input, new File(['x'], 'a.png', { type: 'image/png' }))
    await waitFor(() => expect(client.api).toHaveBeenCalledWith('/api/me/picture', expect.objectContaining({ method: 'POST' })))
  })

  it('removeButtonOnlyWithAPicture', async () => {
    setup('pic.png')
    await userEvent.click(await screen.findByRole('button', { name: 'Remove picture' }))
    expect(client.api).toHaveBeenCalledWith('/api/me/picture', { method: 'DELETE' })
  })
})
