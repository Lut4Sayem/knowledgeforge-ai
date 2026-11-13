import { useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { searchAll, type SearchHit } from '../api/search'
import Snippet from '../palette/Snippet'

const DEBOUNCE_MS = 300
const GROUP_MAX = 5
const LIMIT = 50 // the server maximum: there is no "show more" paging
const TYPES = ['PAGE', 'SPACE', 'TEAM'] as const
type Type = (typeof TYPES)[number]
const LABEL: Record<Type, string> = { PAGE: 'Pages', SPACE: 'Spaces', TEAM: 'Teams' }

/** /app/search?q=&tag=&status=&type=: port of the old search.html onto /api/search (task 7.2). */
export default function Search() {
  const [params, setParams] = useSearchParams()
  const q = params.get('q') ?? ''
  const tag = params.get('tag') ?? ''
  const status = ['DRAFT', 'PUBLISHED'].includes(params.get('status') ?? '') ? params.get('status')! : ''
  const type = TYPES.find((t) => t === params.get('type')) // anything else = all
  const [text, setText] = useState(q)

  const set = (next: Record<string, string | undefined>) =>
    setParams((p) => {
      const u = new URLSearchParams(p)
      for (const [k, v] of Object.entries(next)) {
        if (v) u.set(k, v)
        else u.delete(k)
      }
      return u
    }, { replace: true })

  useEffect(() => setText(q), [q]) // the URL changed from outside (a tag link)
  useEffect(() => {
    if (text.trim() === q.trim()) return
    const t = setTimeout(() => set({ q: text.trim() }), DEBOUNCE_MS)
    return () => clearTimeout(t)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [text])

  const active = q.trim() !== '' || tag !== ''
  const hits = useQuery({
    queryKey: ['search', q.trim(), tag, status],
    queryFn: () => searchAll({ q: q.trim(), tag, publishState: status, limit: LIMIT }),
    enabled: active,
    retry: false,
  })
  // A page filter rules out spaces and teams, which have neither tags nor a publish state.
  const all = (hits.data ?? []).filter((h) => h.type === 'PAGE' || (!tag && !status))
  const counts = Object.fromEntries(TYPES.map((t) => [t, all.filter((h) => h.type === t).length])) as Record<Type, number>
  const shown = type ? [type] : TYPES.filter((t) => counts[t] > 0)

  return (
    <section className="search-page">
      <h1>Search</h1>
      <form role="search" onSubmit={(e) => { e.preventDefault(); set({ q: text.trim() }) }}>
        <input type="search" aria-label="Search" maxLength={200} value={text} onChange={(e) => setText(e.target.value)}
          placeholder="Search by title, text or tag" />
        <select aria-label="Status" value={status} onChange={(e) => set({ status: e.target.value })}>
          <option value="">Any status</option>
          <option value="DRAFT">Draft pages</option>
          <option value="PUBLISHED">Published pages</option>
        </select>
      </form>
      {tag && (
        <p>
          <span className="tag">#{tag} <button type="button" aria-label={`Remove tag filter ${tag}`} onClick={() => set({ tag: undefined })}>×</button></span>
        </p>
      )}

      {!active && (
        <div>
          <h2>Find anything your teams have written</h2>
          <p>Search by title, text or tag. Press Ctrl K from any page to jump here.</p>
        </div>
      )}
      {active && hits.isLoading && <p aria-busy="true">Searching…</p>}
      {hits.isError && (
        <p role="alert">Search failed. {hits.error.message} <button type="button" onClick={() => void hits.refetch()}>Try again</button></p>
      )}
      {hits.isSuccess && (
        <>
          <p className="sr-only" aria-live="polite">{all.length} {all.length === 1 ? 'result' : 'results'}</p>
          {all.length === 0 ? (
            <div>
              <h2>Nothing matches “{q.trim() || `#${tag}`}”</h2>
              <p>{tag || status ? 'Try removing a filter.' : 'Check the spelling or try a shorter word.'}</p>
              {(tag || status) && <button type="button" onClick={() => set({ tag: undefined, status: undefined })}>Clear filters</button>}
            </div>
          ) : (
            <>
              <div className="chips" role="group" aria-label="Result type">
                <button type="button" aria-pressed={!type} onClick={() => set({ type: undefined })}>All {all.length}</button>
                {TYPES.filter((t) => counts[t] > 0).map((t) => (
                  <button key={t} type="button" aria-pressed={type === t} onClick={() => set({ type: t })}>{LABEL[t]} {counts[t]}</button>
                ))}
              </div>
              {shown.map((t) => {
                const rows = all.filter((h) => h.type === t)
                const part = type ? rows : rows.slice(0, GROUP_MAX)
                return (
                  <section key={t}>
                    <h2>{LABEL[t]}</h2>
                    <ul className="results">{part.map((h) => <Row key={h.nodeId ?? h.spaceId ?? h.teamId} hit={h} />)}</ul>
                    {!type && rows.length > part.length && (
                      <button type="button" onClick={() => set({ type: t })}>Show all {rows.length}</button>
                    )}
                  </section>
                )
              })}
            </>
          )}
        </>
      )}
    </section>
  )
}

function Row({ hit }: { hit: SearchHit }) {
  const title = hit.title || 'Untitled'
  if (hit.type !== 'PAGE') {
    // Spaces and teams have no page of their own in the app yet, so they are listed but not linked.
    return <li><strong>{title}</strong> <small>{hit.type === 'SPACE' ? 'Space' : 'Team'}</small></li>
  }
  return (
    <li>
      <Link to={`/p/${hit.nodeId}`}>{title}</Link>
      {hit.publishState === 'DRAFT' && <small> · Draft</small>}
      {hit.archived && <small> · Archived</small>}
      <small> · {[hit.spaceName, hit.path].filter(Boolean).join(' / ')}</small>
      <div><Snippet html={hit.snippetHtml} /></div>
      {hit.tags.length > 0 && (
        <div className="tags">
          {hit.tags.map((t) => <span key={t} className="tag"><Link to={`/search?tag=${encodeURIComponent(t)}`}>#{t}</Link></span>)}
        </div>
      )}
    </li>
  )
}
