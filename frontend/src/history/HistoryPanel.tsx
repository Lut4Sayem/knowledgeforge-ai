import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import './history.css'
import { getVersion, listVersions, nameVersion, type VersionSummary } from '../api/versions'
import { groupVersions } from './groupVersions'

/** What the page shows in place of the editor while a version is selected. */
export interface Viewing {
  version: VersionSummary
  text: string
  previousText: string
  canRestore: boolean
}

const time = (iso: string) => new Date(iso).toLocaleTimeString('en', { hour: 'numeric', minute: '2-digit' })
const names = (v: VersionSummary) => v.authors.map((a) => a.name).join(', ')

function title(v: VersionSummary): string {
  switch (v.kind) {
    case 'NAMED': return `★ ${v.name}`
    case 'PUBLISHED': return 'Published'
    case 'RESTORED': return 'Restored from an older version'
    case 'RECOVERED': return 'Recovered unsaved changes'
    default: return time(v.createdAt)
  }
}

export default function HistoryPanel({ nodeId, canEdit, viewingId, onView, onClose, now }: {
  nodeId: string
  canEdit: boolean
  viewingId: string | null
  onView: (v: Viewing) => void
  onClose: () => void
  onRestored?: () => void
  now?: Date
}) {
  const qc = useQueryClient()
  const list = useQuery({ queryKey: ['versions', nodeId], queryFn: () => listVersions(nodeId) })
  const [namedOnly, setNamedOnly] = useState(false)
  const [open, setOpen] = useState<Set<string>>(new Set()) // expanded session / private rows
  const [naming, setNaming] = useState(false)
  const [name, setName] = useState('')
  const [error, setError] = useState('')

  const all = useMemo(() => [...(list.data ?? [])].sort((a, b) => b.createdAt.localeCompare(a.createdAt)), [list.data])
  const shown = useMemo(() => (namedOnly ? all.filter((v) => v.kind === 'NAMED') : all), [all, namedOnly])
  const groups = useMemo(() => groupVersions(shown, now), [shown, now])

  async function select(v: VersionSummary) {
    setError('')
    try {
      const before = all[all.findIndex((x) => x.id === v.id) + 1] // the version before, even when the filter hides it
      const [cur, prev] = await Promise.all([getVersion(v.id), before ? getVersion(before.id) : Promise.resolve(null)])
      onView({ version: v, text: cur.text, previousText: prev?.text ?? '', canRestore: canEdit })
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not open this version')
    }
  }

  async function saveName() {
    if (!name.trim()) return
    try {
      await nameVersion(nodeId, name.trim())
      setName('')
      setNaming(false)
      setError('')
      await qc.invalidateQueries({ queryKey: ['versions', nodeId] })
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not name the version')
    }
  }

  const toggle = (key: string) =>
    setOpen((s) => {
      const n = new Set(s)
      if (!n.delete(key)) n.add(key)
      return n
    })

  const row = (v: VersionSummary) => (
    <button key={v.id} type="button" className="kf-version" aria-current={v.id === viewingId} onClick={() => select(v)}>
      <span className="kf-version-title">{title(v)}</span>
      <span className="kf-version-sub">{v.kind === 'AUTO' ? names(v) : `${names(v)} · ${time(v.createdAt)}`}</span>
    </button>
  )

  return (
    <aside className="kf-history" aria-label="Version history">
      <div className="kf-history-head">
        <h2>Version history</h2>
        <button type="button" aria-label="Close history" onClick={onClose}>×</button>
        <label>
          <input type="checkbox" checked={namedOnly} onChange={(e) => setNamedOnly(e.target.checked)} />
          Named versions only
        </label>
        {canEdit && !naming && <button type="button" className="banner-btn" onClick={() => setNaming(true)}>Name the current version…</button>}
        {canEdit && naming && (
          <form onSubmit={(e) => { e.preventDefault(); saveName() }}>
            <input aria-label="Version name" placeholder="Name the current version…" maxLength={100} autoFocus value={name} onChange={(e) => setName(e.target.value)} />
            <button type="submit" className="banner-btn">Save</button>
          </form>
        )}
        {error && <p role="alert">{error}</p>}
      </div>
      <div className="kf-history-list">
        {list.isLoading && <p className="page-note">Loading…</p>}
        {list.isError && <p role="alert">Could not load the history.</p>}
        {list.data && !shown.length && <p className="page-note">{namedOnly ? 'No named versions yet.' : 'No versions yet.'}</p>}
        {groups.map((g) => (
          <section key={g.label}>
            <h3>{g.label}</h3>
            {g.items.map((it, i) => {
              if (it.kind === 'version') return row(it.version)
              const key = `${g.label}:${i}`
              const expanded = open.has(key)
              if (it.kind === 'session') {
                return (
                  <div key={key}>
                    {row(it.versions[0])}
                    <button type="button" className="kf-version-more" aria-expanded={expanded} onClick={() => toggle(key)}>
                      {`${expanded ? '▾' : '▸'} ${it.versions.length} checkpoints in this session`}
                    </button>
                    {expanded && it.versions.slice(1).map(row)}
                  </div>
                )
              }
              const n = it.versions.length
              return (
                <div key={key}>
                  <button type="button" className="kf-version-more" aria-expanded={expanded} onClick={() => toggle(key)}>
                    {`${n} private ${n === 1 ? 'draft' : 'drafts'} · Only you can see these`}
                  </button>
                  {expanded && it.versions.map(row)}
                </div>
              )
            })}
          </section>
        ))}
      </div>
    </aside>
  )
}
