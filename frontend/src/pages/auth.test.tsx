import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import Login from './Login'
import Register from './Register'
import { safeNext } from './safeNext'

const apiMock = vi.hoisted(() => vi.fn())
vi.mock('../api/client', async (orig) => ({ ...(await orig<typeof import('../api/client')>()), api: apiMock }))

const assign = vi.fn()

beforeEach(() => {
  vi.stubGlobal('location', { origin: 'http://localhost', pathname: '/app/login', search: '', assign })
  // /api/me: not logged in
  apiMock.mockImplementation((path: string) => (path === '/api/me' ? Promise.reject(new ApiError(401, 'Unauthorized')) : Promise.resolve(null)))
})
afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
  apiMock.mockReset()
  assign.mockReset()
})

describe('Login', () => {
  it('loginShowsServerErrorOnBadCredentials', async () => {
    apiMock.mockImplementation((path: string) =>
      Promise.reject(path === '/api/me' ? new ApiError(401, 'Unauthorized') : new ApiError(401, 'Bad credentials')))
    render(<MemoryRouter><Login /></MemoryRouter>)
    await userEvent.type(screen.getByLabelText('Email'), 'a@b.co')
    await userEvent.type(screen.getByLabelText('Password'), 'wrongpass')
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }))
    expect((await screen.findAllByRole('alert'))[0].textContent).toMatch(/don't match/)
    expect(assign).not.toHaveBeenCalled()
  })

  it('loginRedirectsToSafeNextOnly', async () => {
    render(<MemoryRouter initialEntries={['/login?next=//evil.com']}><Login /></MemoryRouter>)
    await userEvent.type(screen.getByLabelText('Email'), 'a@b.co')
    await userEvent.type(screen.getByLabelText('Password'), 'secret123')
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }))
    await waitFor(() => expect(assign).toHaveBeenCalledWith('/dashboard.html'))

    expect(safeNext('/app/p/1?x=1')).toBe('/app/p/1?x=1')
    expect(safeNext('https://evil.com/x')).toBe('/dashboard.html')
    expect(safeNext('/\\evil.com')).toBe('/dashboard.html')
    expect(safeNext('/app/login')).toBe('/dashboard.html')
  })
})

describe('Register', () => {
  it('registerValidatesPasswordLength8', async () => {
    render(<MemoryRouter><Register /></MemoryRouter>)
    await userEvent.type(screen.getByLabelText('Full name'), 'Ann')
    await userEvent.type(screen.getByLabelText('Email'), 'a@b.co')
    await userEvent.type(screen.getByLabelText('Password'), '1234567')
    await userEvent.type(screen.getByLabelText('Confirm password'), '1234567')
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }))
    expect(await screen.findByText('Use at least 8 characters.')).toBeTruthy()
    expect(apiMock).not.toHaveBeenCalledWith('/api/auth/register', expect.anything())
  })
})
