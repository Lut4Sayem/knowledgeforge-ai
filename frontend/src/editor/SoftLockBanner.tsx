// Release 1a only (domain spec 5.4): one editor at a time, by courtesy. Real-time editing replaces this in 1b.
import { useEffect, useRef, useState } from 'react'
import { acquireEditLock, releaseEditLock } from '../api/content'

/** checking: asking the server · editing: I hold the lock · blocked: someone else does · readonly: I chose to only read */
export type LockMode = 'checking' | 'editing' | 'blocked' | 'readonly'

const HEARTBEAT_MS = 20_000 // the server lets a lock expire 60 s after the last call

export function useEditLock(nodeId: string, canEdit: boolean) {
  const [mode, setMode] = useState<LockMode>('checking')
  const [holder, setHolder] = useState('Someone')
  const acquire = useRef<(force: boolean) => void>(() => {})

  useEffect(() => {
    if (!canEdit) return
    let live = true
    let mine = false
    acquire.current = (force) => {
      acquireEditLock(nodeId, force).then(
        (lock) => {
          if (!live) return
          mine = lock.mine
          if (lock.holder && !lock.mine) setHolder(lock.holder.name)
          setMode(lock.mine ? 'editing' : 'blocked')
        },
        // shortcut: if the lock can't be checked we let the user edit; the revision check on save still
        // stops silent overwrites. Revisit only if the soft lock outlives 1b.
        () => live && setMode((m) => (m === 'checking' ? 'editing' : m)),
      )
    }
    const release = (keepalive: boolean) => {
      if (mine) void releaseEditLock(nodeId, keepalive).catch(() => {})
    }
    const onPageHide = () => release(true)

    acquire.current(false)
    const heartbeat = setInterval(() => mine && acquire.current(false), HEARTBEAT_MS)
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
    openReadOnly: () => setMode('readonly'),
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
