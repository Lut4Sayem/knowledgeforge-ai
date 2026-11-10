import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { getTagged } from '../api/nodes'

/** /app/tags/:tag: every page with this tag that I can open, across my spaces. */
export default function TagPage() {
  const { tag = '' } = useParams()
  const pages = useQuery({ queryKey: ['tagged', tag], queryFn: () => getTagged(tag), retry: false })
  return (
    <section>
      <h1>#{tag}</h1>
      {pages.isLoading && <p>Loading…</p>}
      {pages.isError && <p role="alert">{pages.error.message}</p>}
      {pages.isSuccess && pages.data.length === 0 && <p>No pages with this tag.</p>}
      <ul className="tag-pages">
        {pages.data?.map((p) => (
          <li key={p.id}>
            <Link to={`/p/${p.id}`}>{p.icon ? `${p.icon} ` : ''}{p.title || 'Untitled'}</Link>
            <small> {p.spaceName}{p.publishState === 'DRAFT' ? ' · Draft' : ''}</small>
          </li>
        ))}
      </ul>
    </section>
  )
}
