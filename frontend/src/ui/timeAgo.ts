export function timeAgo(iso: string | null, now = Date.now()): string {
  if (!iso) return ''
  const mins = Math.max(0, Math.round((now - new Date(iso).getTime()) / 60_000))
  if (mins < 60) return mins <= 1 ? 'just now' : `${mins} minutes ago`
  if (mins < 24 * 60) return `${Math.round(mins / 60)} hours ago`
  const days = Math.round(mins / (24 * 60))
  return days === 1 ? 'yesterday' : `${days} days ago`
}
