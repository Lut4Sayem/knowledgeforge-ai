import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useId, useMemo, useRef, useState } from 'react'
import { getSubtreeCount, getTree, moveToTeam, type NodeDetail } from '../api/nodes'
import { getMySpaces } from '../api/spaces'
import { buildTree, type TreeNode } from '../tree/buildTree'

interface Props {
  node: Pick<NodeDetail, 'id' | 'spaceId' | 'title'>
  onClose: () => void
}

const flatten = (list: TreeNode[], depth = 0): { n: TreeNode; depth: number }[] =>
  list.flatMap((n) => [{ n, depth }, ...flatten(n.children, depth + 1)])

/** Move a private page (and its sub-pages) into a team project (wireframe MoveToTeam, domain spec 4.4). */
export default function MoveToTeamDialog({ node, onClose }: Props) {
  const qc = useQueryClient()
  const id = useId()
  const ref = useRef<HTMLDialogElement>(null)
  const [teamId, setTeamId] = useState('')
  const [spaceId, setSpaceId] = useState('')
  const [parentId, setParentId] = useState('') // '' = top of the project
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const spaces = useQuery({ queryKey: ['spaces'], queryFn: getMySpaces })
  const count = useQuery({ queryKey: ['subtree-count', node.id], queryFn: () => getSubtreeCount(node.id) })
  // Only projects where I can edit are offered.
  const projects = useMemo(
    () => (spaces.data ?? []).filter((s) => s.ownerType === 'TEAM' && (s.myRole === 'ADMIN' || s.myRole === 'EDITOR')),
    [spaces.data],
  )
  const teams = useMemo(() => [...new Map(projects.map((s) => [s.teamId!, s.teamName ?? 'Team'])).entries()], [projects])
  const team = teamId || teams[0]?.[0] || ''
  const inTeam = projects.filter((s) => s.teamId === team)
  const space = inTeam.find((s) => s.id === spaceId) ?? inTeam[0]
  const tree = useQuery({ queryKey: ['tree', space?.id], queryFn: () => getTree(space!.id), enabled: !!space })
  const rows = useMemo(() => flatten(buildTree(tree.data ?? [])), [tree.data])
  const parentTitle = rows.find((r) => r.n.id === parentId)?.n.title ?? space?.name

  useEffect(() => {
    const d = ref.current!
    if (typeof d.showModal === 'function') d.showModal()
    else d.setAttribute('open', '')
    return () => {
      if (typeof d.close === 'function') d.close()
    }
  }, [])

  async function submit() {
    if (!space) return
    setBusy(true)
    setError('')
    try {
      await moveToTeam(node.id, { spaceId: space.id, parentId: parentId || null })
      await Promise.all([
        qc.invalidateQueries({ queryKey: ['node', node.id] }),
        qc.invalidateQueries({ queryKey: ['tree', node.spaceId] }),
        qc.invalidateQueries({ queryKey: ['tree', space.id] }),
      ])
      onClose()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'That did not work.')
      setBusy(false)
    }
  }

  const subs = count.data?.count ?? 0
  return (
    <dialog ref={ref} className="kf-dialog kf-move" aria-labelledby={`${id}-t`}
      onCancel={(e) => { e.preventDefault(); onClose() }}>
      <h2 id={`${id}-t`} className="kf-dialog-title">Move "{node.title}" to a team project</h2>
      <p className="kf-dialog-body">From your private space{subs > 0 && ` · includes ${subs} sub-page${subs === 1 ? '' : 's'}`}</p>
      {spaces.isSuccess && projects.length === 0 ? (
        <p role="status">You can't edit any team project yet.</p>
      ) : (
        <>
          <div className="kf-move-pick">
            <label>Team
              <select value={team} onChange={(e) => { setTeamId(e.target.value); setSpaceId(''); setParentId('') }}>
                {teams.map(([tid, name]) => <option key={tid} value={tid}>{name}</option>)}
              </select>
            </label>
            <label>Project
              <select value={space?.id ?? ''} onChange={(e) => { setSpaceId(e.target.value); setParentId('') }}>
                {inTeam.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
              </select>
            </label>
          </div>
          <fieldset className="kf-move-parents">
            <legend>Put it under</legend>
            <label><input type="radio" name={`${id}-p`} checked={parentId === ''} onChange={() => setParentId('')} />Top of {space?.name}</label>
            {rows.map(({ n, depth }) => (
              <label key={n.id} style={{ paddingLeft: 16 * (depth + 1) }}>
                <input type="radio" name={`${id}-p`} checked={parentId === n.id} disabled={n.titleOnly} onChange={() => setParentId(n.id)} />
                {n.title}{n.titleOnly && " (you can't edit here)"}
              </label>
            ))}
          </fieldset>
          <section aria-label="What happens" className="kf-move-info">
            <strong>What happens when you move it</strong>
            <span>✓ Content, files, tags and comments come along.</span>
            <span>✓ Everyone with access to "{parentTitle}" can see it.</span>
            <span>✓ Your earlier versions stay private. The team's history starts at "Published to team".</span>
            <span>✓ Public links on these pages are turned off.</span>
            <span>It can't be moved back to your private space later. You can copy it instead.</span>
          </section>
        </>
      )}
      {error && <p role="alert">{error}</p>}
      <div className="kf-dialog-actions">
        <button type="button" onClick={onClose}>Cancel</button>
        <button type="button" className="kf-primary" disabled={busy || !space} onClick={() => void submit()}>Move to team</button>
      </div>
    </dialog>
  )
}
