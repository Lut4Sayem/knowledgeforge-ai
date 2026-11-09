import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, api } from './client'

function respond(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status })
}

describe('api client', () => {
  const fetchMock = vi.fn()
  const assign = vi.fn()

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock)
    vi.stubGlobal('location', { pathname: '/app/p/1', search: '?x=1', assign })
    document.cookie = 'XSRF-TOKEN=tok123'
  })
  afterEach(() => {
    vi.unstubAllGlobals()
    fetchMock.mockReset()
    assign.mockReset()
    document.cookie = 'XSRF-TOKEN=; max-age=0'
  })

  it('sendsCsrfHeaderOnPost', async () => {
    fetchMock.mockResolvedValue(respond(200, { ok: true }))
    await api('/api/x', { method: 'POST', body: '{}' })
    const headers = fetchMock.mock.calls[0][1].headers as Headers
    expect(headers.get('X-XSRF-TOKEN')).toBe('tok123')
    expect(headers.get('Content-Type')).toBe('application/json')
  })

  it('redirectsToLoginOn401', () => {
    fetchMock.mockResolvedValue(respond(401, { message: 'Unauthorized' }))
    void api('/api/x')
    return vi.waitFor(() => expect(assign).toHaveBeenCalledWith('/app/login?next=' + encodeURIComponent('/app/p/1?x=1')))
  })

  it('throwsApiErrorWithServerMessage', async () => {
    fetchMock.mockResolvedValue(respond(409, { message: 'Already a member' }))
    const err = await api('/api/x').catch((e) => e)
    expect(err).toBeInstanceOf(ApiError)
    expect(err).toMatchObject({ status: 409, message: 'Already a member' })
  })
})
