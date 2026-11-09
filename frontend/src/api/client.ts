export class ApiError extends Error {
  status: number
  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

function csrfToken(): string | null {
  const m = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/)
  return m ? decodeURIComponent(m[1]) : null
}

/** JSON fetch with session cookie + CSRF header. 401 sends the user to the login page. */
export async function api<T>(
  path: string,
  { redirectOn401 = true, ...init }: RequestInit & { redirectOn401?: boolean } = {},
): Promise<T> {
  const headers = new Headers(init.headers)
  const token = csrfToken()
  if (token) headers.set('X-XSRF-TOKEN', token)
  if (init.body && !(init.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  const res = await fetch(path, { credentials: 'include', ...init, headers })

  if (res.status === 401 && redirectOn401) {
    const next = location.pathname + location.search
    location.assign('/app/login?next=' + encodeURIComponent(next))
    return new Promise<T>(() => {}) // page is navigating away
  }

  const text = await res.text()
  let data: unknown = null
  try {
    data = text ? JSON.parse(text) : null
  } catch {
    data = text
  }
  if (!res.ok) {
    const message =
      data && typeof data === 'object' && 'message' in data ? String((data as { message: unknown }).message) : res.statusText
    throw new ApiError(res.status, message || 'Request failed')
  }
  return data as T
}
