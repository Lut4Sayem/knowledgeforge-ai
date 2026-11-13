import { useQuery } from '@tanstack/react-query'
import { Navigate, useParams } from 'react-router-dom'
import { getTree } from '../api/nodes'
import { buildTree } from '../tree/buildTree'

/** /s/:spaceId (old space links): opens the project's first page. */
export default function SpaceHome() {
  const { spaceId = '' } = useParams()
  const tree = useQuery({ queryKey: ['tree', spaceId], queryFn: () => getTree(spaceId), retry: false })
  if (tree.isLoading) return <p>Loading…</p>
  if (!tree.data) return <p role="alert">Could not open this project: {tree.error?.message}</p>
  const first = buildTree(tree.data).find((n) => n.type === 'PAGE') ?? tree.data.find((n) => n.type === 'PAGE')
  if (first) return <Navigate to={`/p/${first.id}`} replace />
  return <p className="page-note">This project has no pages yet. Use “+ New page” in the sidebar.</p>
}
