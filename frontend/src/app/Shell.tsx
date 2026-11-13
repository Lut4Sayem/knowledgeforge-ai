import { useCallback, useEffect, useRef, useState } from 'react'
import { Outlet } from 'react-router-dom'
import CommandPalette from '../palette/CommandPalette'
import Sidebar from '../tree/Sidebar'

export default function Shell() {
  const [open, setOpen] = useState(false)
  const from = useRef<HTMLElement | null>(null) // what had focus before the palette: gets it back on close

  const show = useCallback(() => {
    from.current = document.activeElement instanceof HTMLElement ? document.activeElement : null
    setOpen(true)
  }, [])
  const hide = useCallback(() => {
    setOpen(false)
    if (from.current?.isConnected) from.current.focus()
  }, [])

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault()
        if (open) hide()
        else show()
      }
    }
    window.addEventListener('keydown', onKey, true) // capture: wins over the editor's own Ctrl+K
    return () => window.removeEventListener('keydown', onKey, true)
  }, [open, show, hide])

  return (
    <div className="shell">
      <Sidebar onSearch={show} />
      <main className="main">
        <Outlet />
      </main>
      <CommandPalette open={open} onClose={hide} />
    </div>
  )
}
