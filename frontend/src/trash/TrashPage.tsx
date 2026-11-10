import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import {
  deleteForever,
  getArchived,
  getTrash,
  restoreBranch,
  unarchiveNode,
  type Branch,
} from '../api/nodes'
import { getMySpaces } from '../api/spaces'
import { confirmDeleteForever } from './ConfirmTrashDialog'

const DAY = 24 * 60 * 60 * 1000

export function timeAgo(iso: string | null, now = Date.now()): string {
  if (!iso) return ''
  const mins = Math.max(0, Math.round((now - new Date(iso).getTime()) / 60_000))
  if (mins < 60) return mins <= 1 ? 'just now' : `${mins} minutes ago`
  if (mins < 24 * 60) return `${Math.round(mins / 60)} hours ago`
  const days = Math.round(mins / (24 * 60))
  return days === 1 ? 'yesterday' : `${days} days ago`
}

export function daysLeft(purgeAt: string | null, now = Date.now()): string {
  if (!purgeAt) return ''
  const days = Math.max(0, Math.ceil((new Date(purgeAt).getTime() - now) / DAY))
  return days === 1 ? '1 day' : `${days} days`
}

/** Trash and Archived tabs of one space (wireframe Trash). */
export default function TrashPage() {
  const { spaceId = '' } = useParams()
  const [params, setParams] = useSearchParams()
  const tab = params.get('tab') === 'archived' ? 'archived' : 'trash'
  const qc = useQueryClient()
  const spaces = useQuery({ queryKey: ['spaces'], queryFn: getMySpaces })
  const trash = useQuery({ queryKey: ['trash', spaceId], queryFn: () => getTrash(spaceId), retry: false })
  const archived = useQuery({ queryKey: ['archived', spaceId], queryFn: () => getArchived(spaceId), retry: false })
  const [notice, setNotice] = useState('')
  const [error, setError] = useState('')
  const spaceName = spaces.data?.find((s) => s.id === spaceId)?.name ?? 'this space'

  const active = tab === 'trash' ? trash : archived
  const refresh = () =>
    Promise.all([
      qc.invalidateQueries({ queryKey: ['trash', spaceId] }),
      qc.invalidateQueries({ queryKey: ['archived', spaceId] }),
      qc.invalidateQueries({ queryKey: ['tree', spaceId] }),
    ])

  const restore = useMutation({
    mutationFn: (b: Branch) => (tab === 'trash' ? restoreBranch(b.id) : unarchiveNode(b.id)),
    onSuccess: async (result, b) => {
      setError('')
      setNotice(
        result.movedToTop
          ? `"${b.title || 'Untitled'}" was restored to the top level of ${spaceName}, because its parent was deleted or is in the trash.`
          : `"${b.title || 'Untitled'}" was restored.`,
      )
      await refresh()
    },
    onError: (e) => setError(e.message),
  })
  const forever = useMutation({
    mutationFn: async (b: Branch) => {
      if (!(await confirmDeleteForever(b.title, b.descendantCount))) return false
      await deleteForever(b.id)
      return true
    },
    onSuccess: async (done) => {
      setError('')
      if (done) {
        setNotice('Deleted forever.')
        await refresh()
      }
    },
    onError: (e) => setError(e.message),
  })

  const canPurge = !!trash.data?.canPurge
  const items = active.data?.items ?? []
  const label = (t: 'trash' | 'archived', q: typeof trash) => `${t === 'trash' ? 'Trash' : 'Archived'}${q.data ? ` (${q.data.items.length})` : ''}`

  return (
    <section className="trash-page">
      <h1>{spaceName}: {tab === 'trash' ? 'Trash' : 'Archived'}</h1>
      <div role="tablist" aria-label="Trash and archive" className="tabs">
        {(['trash', 'archived'] as const).map((t) => (
          <button key={t} type="button" role="tab" aria-selected={tab === t} onClick={() => setParams(t === 'trash' ? {} : { tab: t })}>
            {label(t, t === 'trash' ? trash : archived)}
          </button>
        ))}
      </div>
      <p className="page-note">
        {tab === 'trash'
          ? 'Pages in the trash are deleted for good 30 days after they were trashed. Restoring brings back the page and all its sub-pages.'
          : 'Archived pages are read-only and still show up in search. Unarchiving brings back the page and its sub-pages.'}
      </p>
      {notice && <p role="status" className="banner">{notice}</p>}
      {error && <p role="alert" className="banner">{error}</p>}
      {active.isLoading && <p>Loading…</p>}
      {active.isError && <p role="alert">{active.error.message}</p>}
      {active.isSuccess && items.length === 0 && <p>{tab === 'trash' ? 'The trash is empty.' : 'Nothing is archived.'}</p>}
      {items.length > 0 && (
        <table className="trash-table">
          <thead>
            <tr>
              <th scope="col">Page</th>
              <th scope="col">Was in</th>
              <th scope="col">{tab === 'trash' ? 'Deleted by' : 'Archived by'}</th>
              {tab === 'trash' && <th scope="col">Deleted forever in</th>}
              <th scope="col">Actions</th>
            </tr>
          </thead>
          <tbody>
            {items.map((b) => (
              <tr key={b.id}>
                <th scope="row">
                  {b.icon ? `${b.icon} ` : ''}{b.title || 'Untitled'}
                  {b.descendantCount > 0 && <small> + {b.descendantCount} sub-page{b.descendantCount === 1 ? '' : 's'}</small>}
                  {b.parentGone && <small className="trash-note"> Its parent is also deleted or archived: restores to the top of {spaceName}</small>}
                </th>
                <td>{[spaceName, ...b.path.map((p) => p.title || 'Untitled')].join(' / ')}</td>
                <td>{b.by?.name ?? 'Unknown'}{b.at ? ` · ${timeAgo(b.at)}` : ''}</td>
                {tab === 'trash' && <td>{daysLeft(b.purgeAt)}</td>}
                <td className="trash-actions">
                  {b.canRestore && (
                    <button type="button" onClick={() => restore.mutate(b)} disabled={restore.isPending}>{tab === 'trash' ? 'Restore' : 'Unarchive'}</button>
                  )}
                  {tab === 'trash' && canPurge && (
                    <button type="button" className="danger" onClick={() => forever.mutate(b)} disabled={forever.isPending}>Delete forever</button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {tab === 'trash' && !canPurge && trash.isSuccess && items.length > 0 && <p className="page-note">Only team admins can delete forever.</p>}
      <p><Link to="/">Back to home</Link></p>
    </section>
  )
}
