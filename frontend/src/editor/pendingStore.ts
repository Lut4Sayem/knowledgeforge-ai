// Unconfirmed page content, kept in IndexedDB until the server confirms it (domain spec 5.3).
// One record per node holding one slot per editing session (tab): two tabs on the same page must not
// overwrite each other's unsaved copy.
import { get, update } from 'idb-keyval'

export interface Pending {
  state: string // base64 Yjs state
  text: string
  baseRevision: number
  at: number // ms; the newest slot is replayed first
}
export type Slots = Record<string, Pending>

const key = (nodeId: string) => `kf.pending.${nodeId}`

// IndexedDB can be missing or blocked (private windows). Editing must still work then, so failures are
// swallowed: the page just has no local safety copy.
export const pendingStore = {
  put: (nodeId: string, slot: string, pending: Pending): Promise<void> =>
    update<Slots>(key(nodeId), (slots) => ({ ...slots, [slot]: pending })).catch(() => {}),

  all: (nodeId: string): Promise<Slots> =>
    get<Slots>(key(nodeId)).then((slots) => slots ?? {}, () => ({})),

  clear: (nodeId: string, slot: string): Promise<void> =>
    update<Slots>(key(nodeId), (slots) => {
      const rest = { ...slots }
      delete rest[slot]
      return rest
    }).catch(() => {}),
}
