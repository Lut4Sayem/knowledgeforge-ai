import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Command } from 'cmdk'
import { useEffect, useRef, useState } from 'react'
import { useMatch, useNavigate } from 'react-router-dom'
import { createNode, type NodeSummary } from '../api/nodes'
import { getRecent, searchAll, type SearchHit } from '../api/search'
import { getMySpaces } from '../api/spaces'
import Snippet from './Snippet'

const DEBOUNCE_MS = 200

interface Props {
  open: boolean
  onClose: () => void
}

/** Ctrl+K palette (task 7.2). Mounted by the Shell; the Shell also owns the shortcut and the focus return. */
export default function CommandPalette({ open, onClose }: Props) {
  return open ? <Palette onClose={onClose} /> : null // unmounted when closed: every open starts empty and fresh
}

function Palette({ onClose }: { onClose: () => void }) {
  const nav = useNavigate()
  const qc = useQueryClient()
  const activeId = useMatch('/p/:nodeId')?.params.nodeId
  const [q, setQ] = useState('')
  const [content, setContent] = useState<SearchHit[]>([])
  const seq = useRef(0)
  const dialog = useRef<HTMLDialogElement>(null)
  const term = q.trim()

  const recent = useQuery({ queryKey: ['recent'], queryFn: getRecent, staleTime: 0, gcTime: 0, retry: false })
  const spaces = useQuery({ queryKey: ['spaces'], queryFn: getMySpaces })

  useEffect(() => {
    const d = dialog.current
    if (d && typeof d.showModal === 'function') d.showModal()
    else d?.setAttribute('open', '') // jsdom has no showModal
    return () => {
      if (d && typeof d.close === 'function' && d.open) d.close()
    }
  }, [])

  // Server search for page content, after a pause. An answer that is no longer the latest question is dropped.
  useEffect(() => {
    const id = ++seq.current
    if (!term) {
      setContent([])
      return
    }
    const t = setTimeout(() => {
      searchAll({ q: term, limit: 10 }).then(
        (r) => { if (id === seq.current) setContent(r.filter((h) => h.type === 'PAGE')) },
        () => { if (id === seq.current) setContent([]) },
      )
    }, DEBOUNCE_MS)
    return () => clearTimeout(t)
  }, [term])

  // Title prefix match over the trees the sidebar has already loaded: instant, no request.
  const trees = qc.getQueriesData<NodeSummary[]>({ queryKey: ['tree'] })
  const lower = term.toLowerCase()
  const local = !term ? [] : trees.flatMap(([key, nodes]) =>
    (nodes ?? []).filter((n) => n.title.toLowerCase().startsWith(lower)).map((n) => ({ ...n, spaceId: String(key[1]) })),
  ).slice(0, 8)
  const localIds = new Set(local.map((n) => n.id))
  const inContent = content.filter((h) => h.nodeId && !localIds.has(h.nodeId))

  // "Current project" = the space whose tree holds the open page, else the private space, else the first one.
  const all = spaces.data ?? []
  const currentId = trees.find(([, nodes]) => nodes?.some((n) => n.id === activeId))?.[0][1]
  const current = all.find((s) => s.id === currentId) ?? all.find((s) => s.ownerType === 'USER') ?? all[0]
  const actions = !current ? [] : [
    { id: 'new-page', label: `New page in ${current.name}`, run: async () => {
      const page = await createNode(current.id, {})
      await qc.invalidateQueries({ queryKey: ['tree', current.id] })
      nav(`/p/${page.id}`)
    } },
    { id: 'open-trash', label: 'Open trash', run: () => nav(`/s/${current.id}/trash`) },
  ].filter((a) => !term || a.label.toLowerCase().includes(lower))

  function open(nodeId: string) {
    onClose()
    nav(`/p/${nodeId}`)
  }

  function onKeyDown(e: React.KeyboardEvent) {
    if (e.key === 'Escape') {
      e.preventDefault() // the browser's own Esc would close the dialog before focus is handed back
      onClose()
    }
  }

  return (
    <dialog ref={dialog} className="palette" aria-label="Search pages and actions" onKeyDown={onKeyDown}
      onClick={(e) => { if (e.target === dialog.current) onClose() }} onCancel={(e) => e.preventDefault()}>
      <Command shouldFilter={false} label="Search pages and actions">
        <Command.Input value={q} onValueChange={setQ} placeholder="Search pages and actions" maxLength={200} autoFocus />
        <Command.List>
          {term && local.length + inContent.length + actions.length === 0 && <Command.Empty>No matches</Command.Empty>}
          {!term && (recent.data ?? []).length > 0 && (
            <Command.Group heading="RECENT">
              {recent.data!.map((h) => (
                <Command.Item key={h.nodeId} value={`recent-${h.nodeId}`} onSelect={() => open(h.nodeId!)}>
                  {h.title || 'Untitled'} <small>{h.spaceName}</small>
                </Command.Item>
              ))}
            </Command.Group>
          )}
          {local.length > 0 && (
            <Command.Group heading="PAGES">
              {local.map((n) => (
                <Command.Item key={n.id} value={`page-${n.id}`} onSelect={() => open(n.id)}>{n.title || 'Untitled'}</Command.Item>
              ))}
            </Command.Group>
          )}
          {inContent.length > 0 && (
            <Command.Group heading="IN PAGE CONTENT">
              {inContent.map((h) => (
                <Command.Item key={h.nodeId} value={`content-${h.nodeId}`} onSelect={() => open(h.nodeId!)}>
                  {h.title || 'Untitled'} <Snippet html={h.snippetHtml} />
                </Command.Item>
              ))}
            </Command.Group>
          )}
          {actions.length > 0 && (
            <Command.Group heading="ACTIONS">
              {actions.map((a) => (
                <Command.Item key={a.id} value={`action-${a.id}`} onSelect={() => { onClose(); void a.run() }}>{a.label}</Command.Item>
              ))}
            </Command.Group>
          )}
        </Command.List>
        <p className="palette-foot">↑ ↓ to move · Enter to open · Esc to close · Empty box shows recent pages</p>
      </Command>
    </dialog>
  )
}
