import {
  DndContext, KeyboardSensor, PointerSensor, closestCenter, useDndContext, useDraggable, useDroppable, useSensor,
  useSensors, type DragEndEvent,
} from '@dnd-kit/core'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { createNode, getTree, moveNode, type MoveBody, type NodeSummary } from '../api/nodes'
import { buildTree, type TreeNode } from './buildTree'

const storageKey = (spaceId: string) => `kf.expanded.${spaceId}`

function loadExpanded(spaceId: string): Set<string> {
  try {
    const raw = localStorage.getItem(storageKey(spaceId))
    return new Set(raw ? (JSON.parse(raw) as string[]) : [])
  } catch {
    return new Set()
  }
}

function saveExpanded(spaceId: string, ids: Set<string>) {
  try {
    localStorage.setItem(storageKey(spaceId), JSON.stringify([...ids]))
  } catch {
    // private mode or blocked storage: the tree just starts collapsed next time
  }
}

interface Props {
  spaceId: string
  spaceName: string
  activeId?: string
}

export default function PageTree({ spaceId, spaceName, activeId }: Props) {
  const qc = useQueryClient()
  const navigate = useNavigate()
  const { data, isLoading, error } = useQuery({ queryKey: ['tree', spaceId], queryFn: () => getTree(spaceId) })
  const nodes = useMemo<NodeSummary[]>(() => data ?? [], [data])
  const tree = useMemo(() => buildTree(nodes), [nodes])
  const [expanded, setExpanded] = useState(() => loadExpanded(spaceId))
  const [message, setMessage] = useState('')
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 5 } }),
    useSensor(KeyboardSensor),
  )

  const expand = (ids: string[]) =>
    setExpanded((prev) => {
      if (ids.every((id) => prev.has(id))) return prev
      const next = new Set(prev)
      ids.forEach((id) => next.add(id))
      saveExpanded(spaceId, next)
      return next
    })

  // Open the branch that holds the current page.
  useEffect(() => {
    if (!activeId) return
    const byId = new Map(nodes.map((n) => [n.id, n]))
    const chain: string[] = []
    for (let p = byId.get(activeId)?.parentId; p && byId.has(p) && !chain.includes(p); p = byId.get(p)?.parentId) chain.push(p)
    if (chain.length) expand(chain)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeId, nodes])

  const toggle = (id: string) =>
    setExpanded((prev) => {
      const next = new Set(prev)
      if (!next.delete(id)) next.add(id)
      saveExpanded(spaceId, next)
      return next
    })

  async function newPage(parentId: string | null) {
    setMessage('')
    try {
      const created = await createNode(spaceId, { parentId })
      if (parentId) expand([parentId])
      await qc.invalidateQueries({ queryKey: ['tree', spaceId] })
      navigate(`/p/${created.id}`)
    } catch (e) {
      setMessage(e instanceof Error ? e.message : 'Could not create the page')
    }
  }

  async function onDragEnd({ active, over }: DragEndEvent) {
    if (!over) return
    const id = String(active.id)
    const [kind, targetId] = String(over.id).split(':')
    if (targetId === id) return
    let body: MoveBody
    if (kind === 'in') {
      body = { parentId: targetId }
    } else if (kind === 'after') {
      body = { parentId: nodes.find((n) => n.id === targetId)?.parentId ?? null, afterId: targetId }
    } else {
      body = { parentId: null }
    }
    setMessage('')
    try {
      await moveNode(id, body)
      if (kind === 'in') expand([targetId])
      await qc.invalidateQueries({ queryKey: ['tree', spaceId] })
      await qc.invalidateQueries({ queryKey: ['node', id] })
    } catch (e) {
      setMessage(e instanceof Error ? e.message : 'Could not move the page')
    }
  }

  if (isLoading) return <p className="tree-note">Loading…</p>
  if (error) return <p className="tree-note" role="alert">Could not load pages</p>

  return (
    <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={onDragEnd}>
      {message && <p className="tree-note" role="alert">{message}</p>}
      <ul className="tree">
        {tree.map((node) => (
          <Row key={node.id} node={node} depth={0} activeId={activeId} expanded={expanded} onToggle={toggle} onNew={newPage} />
        ))}
      </ul>
      <TopLevelDrop spaceId={spaceId} />
      <button type="button" className="tree-add" onClick={() => newPage(null)}>
        + New page<span className="sr-only"> in {spaceName}</span>
      </button>
    </DndContext>
  )
}

interface RowProps {
  node: TreeNode
  depth: number
  activeId?: string
  expanded: Set<string>
  onToggle: (id: string) => void
  onNew: (parentId: string) => void
}

function Row({ node, depth, activeId, expanded, onToggle, onNew }: RowProps) {
  const drag = useDraggable({ id: node.id })
  const into = useDroppable({ id: `in:${node.id}` })
  const after = useDroppable({ id: `after:${node.id}` })
  const open = expanded.has(node.id)
  const title = node.title || 'Untitled'
  return (
    <li className={drag.isDragging ? 'tree-item is-dragging' : 'tree-item'}>
      <div
        ref={(el) => { drag.setNodeRef(el); into.setNodeRef(el) }}
        className={`tree-row${into.isOver ? ' is-over' : ''}${node.id === activeId ? ' is-active' : ''}`}
        style={{ paddingLeft: depth * 16 }}
      >
        <button type="button" className="tree-toggle" aria-expanded={open} aria-label={`${open ? 'Collapse' : 'Expand'} ${title}`}
          onClick={() => onToggle(node.id)} disabled={node.children.length === 0}>
          {node.children.length === 0 ? '·' : open ? '▾' : '▸'}
        </button>
        <Link to={`/p/${node.id}`} className="tree-link" aria-current={node.id === activeId ? 'page' : undefined}>
          {node.icon && <span aria-hidden="true">{node.icon} </span>}
          {title}
        </Link>
        {node.publishState === 'DRAFT' && <span className="tree-badge">Draft</span>}
        <button type="button" className="tree-btn" aria-label={`New sub-page under ${title}`} onClick={() => onNew(node.id)}>+</button>
        <button type="button" className="tree-btn tree-grip" aria-label={`Move ${title}`}
          {...drag.listeners} {...drag.attributes}>⋮⋮</button>
      </div>
      {open && node.children.length > 0 && (
        <ul className="tree">
          {node.children.map((c) => (
            <Row key={c.id} node={c} depth={depth + 1} activeId={activeId} expanded={expanded} onToggle={onToggle} onNew={onNew} />
          ))}
        </ul>
      )}
      <div ref={after.setNodeRef} className={`tree-gap${after.isOver ? ' is-over' : ''}`} style={{ marginLeft: depth * 16 }} />
    </li>
  )
}

/** Only visible while dragging: drop here to make a page top-level (at the end). */
function TopLevelDrop({ spaceId }: { spaceId: string }) {
  const { active } = useDndContext()
  const { setNodeRef, isOver } = useDroppable({ id: `top:${spaceId}` })
  if (!active) return null
  return <div ref={setNodeRef} className={`tree-top-drop${isOver ? ' is-over' : ''}`}>Move to top level</div>
}
