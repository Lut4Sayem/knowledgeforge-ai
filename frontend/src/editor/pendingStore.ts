// Unconfirmed page content, kept in IndexedDB until the server confirms it (domain spec 5.3).
// One record per user and node, holding one slot per editing session (tab): two tabs on the same page
// must not overwrite each other's unsaved copy, and another account on this browser must never see it.
import { delMany, get, keys, update } from 'idb-keyval'

export interface Pending {
  state: string // base64 Yjs state
  text: string
  baseRevision: number
  epoch: number // content epoch the copy is based on; it may only be merged into the same epoch
  at: number // ms, last write (information only: a copy is never dropped for being old)
}
export type Slots = Record<string, Pending>

export interface PendingStore {
  /** Unique per user and node; also the prefix of the slot lock names. */
  key: string
  /** false = it could not be stored (IndexedDB blocked or full): there is NO local copy. */
  put(slot: string, pending: Pending): Promise<boolean>
  all(): Promise<Slots>
  clear(slot: string): Promise<void>
  /** Moves a copy that cannot be read out of the way, so the page still opens. */
  quarantine(slot: string): Promise<void>
}

const PREFIX = 'kf.pending'

export function pendingFor(userId: string, nodeId: string): PendingStore {
  const key = `${PREFIX}.${userId}.${nodeId}`
  const drop = (slot: string) =>
    update<Slots>(key, (slots) => {
      const rest = { ...slots }
      delete rest[slot]
      return rest
    })
  // Editing must keep working without IndexedDB (private windows), so nothing here throws or rejects.
  // (async + try: idb-keyval can also throw synchronously when the database cannot be opened.)
  return {
    key,
    async put(slot, pending) {
      try {
        await update<Slots>(key, (slots) => ({ ...slots, [slot]: pending }))
        return true
      } catch {
        return false
      }
    },
    async all() {
      try {
        return (await get<Slots>(key)) ?? {}
      } catch {
        return {}
      }
    },
    async clear(slot) {
      try {
        await drop(slot)
      } catch {
        // nothing stored, nothing to clear
      }
    },
    async quarantine(slot) {
      try {
        const broken = (await get<Slots>(key))?.[slot]
        if (broken) await update<Slots>(`${PREFIX}-bad.${userId}.${nodeId}`, (bad) => ({ ...bad, [slot]: broken }))
        await drop(slot)
      } catch {
        // same
      }
    },
  }
}

const mine = (userId: string) => [`${PREFIX}.${userId}.`, `${PREFIX}-bad.${userId}.`]

/** Pages (node ids) this user has local copies of: sendable ones, and quarantined ones that cannot be read. */
export async function pendingPages(userId: string): Promise<{ nodes: string[]; unreadable: string[] }> {
  const [good, bad] = mine(userId)
  try {
    const all = (await keys()).map(String)
    const under = (prefix: string) => all.filter((k) => k.startsWith(prefix)).map((k) => k.slice(prefix.length))
    return { nodes: under(good), unreadable: under(bad) }
  } catch {
    return { nodes: [], unreadable: [] }
  }
}

/** Logout: this user's copies must not stay on a shared computer. Call it only after trying to send them. */
export async function clearPendingOf(userId: string): Promise<void> {
  try {
    await delMany((await keys()).filter((k) => mine(userId).some((prefix) => String(k).startsWith(prefix))))
  } catch {
    // no IndexedDB, so nothing was stored
  }
}
