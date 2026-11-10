// Logging out must not strand unsaved work, and must not leave it on a shared computer either:
// first every local copy of this user is sent (same rules as opening a page), and only what the server
// confirmed is cleared. If something could not be sent, the user decides.
import * as Y from 'yjs'
import { api, ApiError } from '../api/client'
import { getContent, saveContent, saveRecovered } from '../api/content'
import { fromBase64, toBase64 } from './base64'
import { clearPendingOf, pendingFor, pendingPages, type PendingStore } from './pendingStore'
import { EMPTY_PAGE_STATE } from './schema'

/** Sends one page's local copies. true = nothing of it is left unsent. */
async function sendPage(nodeId: string, store: PendingStore): Promise<boolean> {
  try {
    // A few rounds: someone else may save between our read and our write (409), then we merge again.
    for (let round = 0; round < 3; round++) {
      const server = await getContent(nodeId)
      const sameEpoch: string[] = []
      const doc = new Y.Doc()
      Y.applyUpdate(doc, fromBase64(server.state || EMPTY_PAGE_STATE))
      for (const [slot, copy] of Object.entries(await store.all())) {
        if (copy.state === server.state) {
          await store.clear(slot) // the server already has exactly this
        } else if (copy.epoch !== server.contentEpoch) {
          // From before a restore: never merged, kept as a private version (D6 epoch rule).
          await saveRecovered(nodeId, { state: copy.state, text: copy.text, baseEpoch: copy.epoch })
          await store.clear(slot)
        } else {
          Y.applyUpdate(doc, fromBase64(copy.state))
          sameEpoch.push(slot)
        }
      }
      if (!sameEpoch.length) return true
      try {
        const { docText } = await import('./docText') // the editor code is big; only load it when needed
        await saveContent(nodeId, { baseRevision: server.revision, state: toBase64(Y.encodeStateAsUpdate(doc)), text: docText(doc) })
      } catch (e) {
        if (e instanceof ApiError && e.status === 409) continue
        throw e
      }
      for (const slot of sameEpoch) await store.clear(slot)
      return true
    }
    return false
  } catch {
    return false // offline, refused, unreadable copy: it stays on the device
  }
}

/** Tries to send every local copy of this user. Returns on how many pages something is still unsent. */
export async function sendAllPending(userId: string): Promise<number> {
  const { nodes, unreadable } = await pendingPages(userId)
  const unsent = new Set(unreadable) // quarantined copies cannot be sent at all
  for (const nodeId of nodes) if (!(await sendPage(nodeId, pendingFor(userId, nodeId)))) unsent.add(nodeId)
  return unsent.size
}

/** false = the user chose to stay logged in. */
export async function logOut(userId: string): Promise<boolean> {
  const unsent = await sendAllPending(userId)
  if (unsent > 0 && !window.confirm(`You have unsaved changes on ${unsent} page${unsent === 1 ? '' : 's'}. Log out anyway?`)) return false
  await clearPendingOf(userId)
  try {
    await api('/logout', { method: 'POST', redirectOn401: false })
  } catch {
    // go to the login page anyway
  }
  location.assign('/app/login')
  return true
}
