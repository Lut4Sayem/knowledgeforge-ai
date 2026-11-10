// Release 1a only (domain spec 5.4): one editor at a time, by courtesy. Real-time editing replaces this in 1b.
import { useEffect, useRef, useState } from 'react'
import { ApiError } from '../api/client'
import { acquireEditLock, releaseEditLock } from '../api/content'

/**
 * checking: asking the server · editing: I hold the lock · blocked: someone else does (banner up) ·
 * readonly: I chose to only read · denied: the server says I may not edit this page any more
 */
export type LockMode = 'checking' | 'editing' | 'blocked' | 'readonly' | 'denied'

const HEARTBEAT_MS = 20_000 // the server lets a lock expire 60 s after the last call; also how often the banner re-checks

export function useEditLock(nodeId: string, canEdit: boolean) {
  const [mode, setMode] = useState<LockMode>('checking')
  const [holder, setHolder] = useState('Someone')
  const acquire = useRef<(force: boolean) => void>(() => {})
  const stopWaiting = useRef(() => {})

  useEffect(() => {
    if (!canEdit) return
    let live = true
    let mine = false
    let waiting = false // banner is up: keep asking, so it goes away when the other person leaves
    stopWaiting.current = () => (waiting = false)
    acquire.current = (force) => {
      acquireEditLock(nodeId, force).then(
        (lock) => {
          if (!live) return
          mine = lock.mine
          waiting = !lock.mine
          if (lock.holder && !lock.mine) setHolder(lock.holder.name)
          setMode((m) => (lock.mine ? 'editing' : m === 'readonly' ? m : 'blocked'))
        },
        (e) => {
          if (!live) return
          if (e instanceof ApiError && (e.status === 403 || e.status === 404)) {
            // Locked, permission removed or page gone: typing on would only pile up changes that cannot be saved.
            mine = waiting = false
            setMode('denied')
            return
          }
          // shortcut: if the lock can't be checked (network) we let the user edit; the revision check on
          // save still stops silent overwrites. Revisit only if the soft lock outlives 1b.
          setMode((m) => (m === 'checking' ? 'editing' : m))
        },
      )
    }
    const release = (keepalive: boolean) => {
      if (mine) void releaseEditLock(nodeId, keepalive).catch(() => {})
    }
    const onPageHide = () => release(true)

    acquire.current(false)
    const heartbeat = setInterval(() => (mine || waiting) && acquire.current(false), HEARTBEAT_MS)
    window.addEventListener('pagehide', onPageHide)
    return () => {
      live = false
      clearInterval(heartbeat)
      window.removeEventListener('pagehide', onPageHide)
      release(false)
    }
  }, [nodeId, canEdit])

  return {
    mode,
    holder,
    editAnyway: () => acquire.current(true),
    openReadOnly: () => {
      stopWaiting.current() // a reader does not take the lock when it becomes free
      setMode('readonly')
    },
  }
}

interface Props {
  holder: string
  onReadOnly: () => void
  onEditAnyway: () => void
}

export default function SoftLockBanner({ holder, onReadOnly, onEditAnyway }: Props) {
  return (
    <div role="alert" className="banner">
      <span>{holder} is editing this page right now.</span>
      <button type="button" className="banner-btn" onClick={onReadOnly}>Open read-only</button>
      <button type="button" className="banner-btn banner-btn--quiet" onClick={onEditAnyway}>Edit anyway</button>
    </div>
  )
}
