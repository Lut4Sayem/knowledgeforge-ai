import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { addComment, ask, deleteComment, getComments, summarize } from '../api/pageExtras'
import { getMe } from '../api/spaces'
import { confirmDialog } from '../ui/ConfirmDialog'

// "AI is not configured on this server" (503): the AI controls disappear instead of failing on every click.
const NOT_CONFIGURED = /not configured/i

/** Page-level comments under the page. Viewers may comment too. */
export function Comments({ nodeId }: { nodeId: string }) {
  const qc = useQueryClient()
  const me = useQuery({ queryKey: ['me'], queryFn: getMe })
  const list = useQuery({ queryKey: ['comments', nodeId], queryFn: () => getComments(nodeId) })
  const [text, setText] = useState('')
  const [error, setError] = useState('')
  const refresh = () => qc.invalidateQueries({ queryKey: ['comments', nodeId] })
  const add = useMutation({
    mutationFn: () => addComment(nodeId, text.trim()),
    onSuccess: async () => { setText(''); setError(''); await refresh() },
    onError: (e: Error) => setError(e.message),
  })

  async function remove(id: string) {
    if (!(await confirmDialog({ title: 'Delete your comment?', body: "This can't be undone.", confirmLabel: 'Delete comment', danger: true }))) return
    try { await deleteComment(id); setError(''); await refresh() } catch (e) { setError((e as Error).message) }
  }

  return (
    <section className="comments" aria-label="Comments">
      <h2>Comments</h2>
      {list.isError && <p role="alert">Could not load comments: {list.error.message}</p>}
      {list.data?.length === 0 && <p className="page-note">No comments yet. Questions and suggestions go here.</p>}
      <ul>
        {list.data?.map((c) => (
          <li key={c.id}>
            <strong>{c.createdByFullName || c.createdByEmail}</strong> · {new Date(c.createdAt).toLocaleString()}
            <p>{c.content}</p>
            {c.createdByUserId === me.data?.id && <button type="button" onClick={() => remove(c.id)}>Delete</button>}
          </li>
        ))}
      </ul>
      <form onSubmit={(e) => { e.preventDefault(); if (text.trim()) add.mutate() }}>
        <label>Add a comment <textarea value={text} onChange={(e) => setText(e.target.value)} maxLength={2000} rows={3} /></label>
        <button type="submit" disabled={!text.trim() || add.isPending}>Comment</button>
        {error && <p role="alert">{error}</p>}
      </form>
    </section>
  )
}

/** Summarize button for one page. */
export function Summarize({ nodeId }: { nodeId: string }) {
  const run = useMutation({ mutationFn: () => summarize(nodeId) })
  if (run.error && NOT_CONFIGURED.test(run.error.message)) return null
  return (
    <section aria-label="Summary">
      <button type="button" onClick={() => run.mutate()} disabled={run.isPending}>{run.isPending ? 'Summarizing…' : 'Summarize'}</button>
      {run.data && <p>{run.data.summary}{run.data.trimmed && <em> (only the start of the page was used)</em>}</p>}
      {run.error && <p role="alert">{run.error.message}</p>}
    </section>
  )
}

/** Ask a question across the pages of the project (space) this page is in. */
export function AskPanel({ spaceId }: { spaceId: string }) {
  const [q, setQ] = useState('')
  const run = useMutation({ mutationFn: () => ask(spaceId, q.trim()) })
  if (run.error && NOT_CONFIGURED.test(run.error.message)) return null
  return (
    <section aria-label="Ask">
      <h2>Ask this project</h2>
      <form onSubmit={(e) => { e.preventDefault(); if (q.trim()) run.mutate() }}>
        <label>Question <input value={q} onChange={(e) => setQ(e.target.value)} maxLength={500} /></label>
        <button type="submit" disabled={!q.trim() || run.isPending}>{run.isPending ? 'Asking…' : 'Ask'}</button>
      </form>
      {run.data && (
        <>
          <p>{run.data.answer}</p>
          {run.data.sources.length > 0 && (
            <ol>{run.data.sources.map((s) => <li key={s.id}><Link to={`/p/${s.id}`}>{s.title}</Link></li>)}</ol>
          )}
        </>
      )}
      {run.error && <p role="alert">{run.error.message}</p>}
    </section>
  )
}
