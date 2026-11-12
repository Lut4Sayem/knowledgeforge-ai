import { useEffect } from 'react'
import { endSession } from '../api/versions'

/** Editors only: when the page is left (tab closed, or the route changes) the edit session becomes a version right away. */
export function useEndSessionOnLeave(nodeId: string, enabled: boolean) {
  useEffect(() => {
    if (!enabled) return
    const end = () => endSession(nodeId)
    window.addEventListener('pagehide', end)
    return () => {
      window.removeEventListener('pagehide', end)
      end()
    }
  }, [nodeId, enabled])
}
