import { Link } from 'react-router-dom'

interface Props {
  spaceName?: string
  crumbs: { id: string; title: string }[]
  current: string
}

export default function Breadcrumbs({ spaceName, crumbs, current }: Props) {
  return (
    <nav className="crumbs" aria-label="Breadcrumb">
      <ol>
        {spaceName && <li>{spaceName}</li>}
        {crumbs.map((c) => <li key={c.id}><Link to={`/p/${c.id}`}>{c.title || 'Untitled'}</Link></li>)}
        <li aria-current="page">{current || 'Untitled'}</li>
      </ol>
    </nav>
  )
}
