import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  archiveNode,
  getSubtreeCount,
  lockNode,
  trashNode,
  unarchiveNode,
  unlockNode,
  unpublishNode,
  type NodeDetail,
} from '../api/nodes'
import { confirmTrash } from '../trash/ConfirmTrashDialog'

interface Props {
  node: NodeDetail
  /** The open editor still has changes the server has not confirmed: do not take the page's edit rights away yet. */
  unsaved: boolean
}

interface Item {
  label: string
  /** May return a notice to show after the action (e.g. where a restored page landed). */
  run: () => Promise<string | void>
  /** Why it cannot be used right now. */
  blocked?: string
}

/** The "..." menu of a page (wireframe Main). Only for page editors, even when the page is locked or archived. */
export default function PageMenu({ node, unsaved }: Props) {
  const qc = useQueryClient()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const root = useRef<HTMLDivElement>(null)
  const button = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    if (!open) return
    const first = root.current?.querySelector<HTMLElement>('[role="menuitem"]:not(:disabled)')
    first?.focus()
    const away = (e: MouseEvent) => !root.current?.contains(e.target as Node) && setOpen(false)
    document.addEventListener('mousedown', away)
    return () => document.removeEventListener('mousedown', away)
  }, [open])

  if (!node.canManage) return null

  const refresh = async () => {
    await Promise.all([
      qc.invalidateQueries({ queryKey: ['node', node.id] }),
      qc.invalidateQueries({ queryKey: ['tree', node.spaceId] }),
    ])
  }
  const lockedNote = node.locked ? 'Unlock the page first' : undefined
  const savingNote = unsaved ? 'Wait until the page is saved' : undefined

  const items: Item[] = []
  if (node.status === 'ARCHIVED') {
    items.push({
      label: 'Unarchive',
      run: async () => {
        const { movedToTop } = await unarchiveNode(node.id)
        await refresh()
        if (movedToTop) return 'This page was moved to the top level of its space, because its parent was deleted or is in the trash.'
      },
    })
  } else {
    if (node.publishState === 'PUBLISHED') {
      items.push({ label: 'Unpublish', blocked: lockedNote, run: async () => (await unpublishNode(node.id), await refresh()) })
    }
    items.push(
      node.locked
        ? { label: 'Unlock page', run: async () => (await unlockNode(node.id), await refresh()) }
        : { label: 'Lock page', blocked: savingNote, run: async () => (await lockNode(node.id), await refresh()) },
    )
    items.push({ label: 'Archive', blocked: lockedNote ?? savingNote, run: async () => (await archiveNode(node.id), await refresh()) })
    items.push({
      label: 'Move to trash',
      blocked: lockedNote ?? savingNote,
      run: async () => {
        const { count } = await getSubtreeCount(node.id)
        if (!(await confirmTrash(node.title, count))) return
        await trashNode(node.id)
        await qc.invalidateQueries({ queryKey: ['tree', node.spaceId] })
        navigate('/')
      },
    })
  }

  async function choose(item: Item) {
    setOpen(false)
    setError('')
    setNotice('')
    try {
      setNotice((await item.run()) || '')
    } catch (e) {
      setError(e instanceof Error ? e.message : 'That did not work.')
    }
    button.current?.focus()
  }

  function onKeyDown(e: React.KeyboardEvent) {
    if (e.key === 'Escape' && open) {
      e.stopPropagation()
      setOpen(false)
      button.current?.focus()
    } else if (open && (e.key === 'ArrowDown' || e.key === 'ArrowUp')) {
      e.preventDefault()
      const all = [...(root.current?.querySelectorAll<HTMLElement>('[role="menuitem"]:not(:disabled)') ?? [])]
      const i = all.indexOf(document.activeElement as HTMLElement)
      all[(i + (e.key === 'ArrowDown' ? 1 : -1) + all.length) % all.length]?.focus()
    }
  }

  return (
    <div className="page-menu" ref={root} onKeyDown={onKeyDown}>
      <button ref={button} type="button" className="page-menu-btn" aria-label="Page actions" aria-haspopup="menu" aria-expanded={open} onClick={() => setOpen((o) => !o)}>
        …
      </button>
      {open && (
        <div className="page-menu-list" role="menu" aria-label="Page actions">
          {items.map((item) => (
            <button key={item.label} type="button" role="menuitem" disabled={!!item.blocked} title={item.blocked} onClick={() => void choose(item)}>
              {item.label}
              {item.blocked && <small> ({item.blocked.toLowerCase()})</small>}
            </button>
          ))}
        </div>
      )}
      {error && <p role="alert" className="page-menu-error">{error}</p>}
      {notice && <p role="status" className="page-menu-note">{notice}</p>}
    </div>
  )
}
