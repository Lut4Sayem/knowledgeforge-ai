export const DEFAULT_NEXT = '/app'

/** Returns a same-origin path from ?next=, never an external URL or an auth page. */
export function safeNext(raw: string | null): string {
  if (!raw || !raw.startsWith('/') || raw.startsWith('//') || raw.startsWith('/\\')) return DEFAULT_NEXT
  try {
    const url = new URL(raw, window.location.origin)
    if (url.origin !== window.location.origin) return DEFAULT_NEXT
    if (/^\/(app\/)?(login|register)(\.html)?$/.test(url.pathname)) return DEFAULT_NEXT
    return url.pathname + url.search + url.hash
  } catch {
    return DEFAULT_NEXT
  }
}
