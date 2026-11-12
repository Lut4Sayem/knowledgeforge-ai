import type { VersionSummary } from '../api/versions'

export type HistoryItem =
  | { kind: 'version'; version: VersionSummary }
  /** Consecutive automatic checkpoints close together, newest first. */
  | { kind: 'session'; versions: VersionSummary[] }
  /** The reader's own private versions of that day (recovered unsaved changes included). */
  | { kind: 'private'; versions: VersionSummary[] }

export interface DayGroup {
  label: string
  items: HistoryItem[]
}

// shortcut: the server does not say which session a checkpoint belongs to, so checkpoints less than
// 30 minutes apart count as one session; send the session id with each version if that guess ever matters.
const SESSION_GAP_MS = 30 * 60 * 1000

const dayStart = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime()

function dayLabel(d: Date, now: Date): string {
  const days = Math.round((dayStart(now) - dayStart(d)) / 86_400_000)
  if (days === 0) return 'Today'
  if (days === 1) return 'Yesterday'
  return d.toLocaleDateString('en', { month: 'short', day: 'numeric', ...(d.getFullYear() !== now.getFullYear() && { year: 'numeric' }) })
}

/** Newest first, by day. Private versions go to one row at the end of their day. */
export function groupVersions(versions: VersionSummary[], now: Date = new Date()): DayGroup[] {
  const sorted = [...versions].sort((a, b) => b.createdAt.localeCompare(a.createdAt))
  const groups: DayGroup[] = []
  const priv = new Map<string, VersionSummary[]>()

  for (const v of sorted) {
    const d = new Date(v.createdAt)
    const label = dayLabel(d, now)
    let g = groups.find((x) => x.label === label)
    if (!g) groups.push((g = { label, items: [] }))
    if (v.private) {
      priv.set(label, [...(priv.get(label) ?? []), v])
      continue
    }
    const last = g.items.at(-1)
    const prev = last?.kind === 'session' ? last.versions.at(-1) : last?.kind === 'version' ? last.version : undefined
    if (v.kind === 'AUTO' && prev?.kind === 'AUTO' && new Date(prev.createdAt).getTime() - d.getTime() <= SESSION_GAP_MS) {
      g.items[g.items.length - 1] = { kind: 'session', versions: [...(last?.kind === 'session' ? last.versions : [prev]), v] }
    } else {
      g.items.push({ kind: 'version', version: v })
    }
  }
  for (const g of groups) {
    const p = priv.get(g.label)
    if (p) g.items.push({ kind: 'private', versions: p })
  }
  return groups
}
