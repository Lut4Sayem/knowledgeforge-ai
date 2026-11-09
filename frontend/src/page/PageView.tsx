import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import { getNode, updateNode } from '../api/nodes'
import { getMySpaces } from '../api/spaces'
import Breadcrumbs from './Breadcrumbs'

export default function PageView() {
  const { nodeId = '' } = useParams()
  const qc = useQueryClient()
  const node = useQuery({ queryKey: ['node', nodeId], queryFn: () => getNode(nodeId), retry: false })
  const spaces = useQuery({ queryKey: ['spaces'], queryFn: getMySpaces })
  const [title, setTitle] = useState('')
  const rename = useMutation({
    mutationFn: (t: string) => updateNode(nodeId, { title: t }),
    onSuccess: async (updated) => {
      qc.setQueryData(['node', nodeId], updated)
      await qc.invalidateQueries({ queryKey: ['tree', updated.spaceId] })
    },
  })

  useEffect(() => setTitle(node.data?.title ?? ''), [node.data?.title, nodeId])

  if (node.isLoading) return <p>Loading…</p>
  if (node.error) {
    const s = node.error instanceof ApiError ? node.error.status : 0
    return <p role="alert">{s === 404 ? 'This page does not exist.' : s === 403 ? "You don't have access to this page." : 'Could not load this page.'}</p>
  }
  const n = node.data!
  const canEdit = n.access === 'EDIT'
  const spaceName = spaces.data?.find((s) => s.id === n.spaceId)?.name

  return (
    <article className="page">
      <Breadcrumbs spaceName={spaceName} crumbs={n.breadcrumbs} current={n.title} />
      <input
        className="page-title"
        aria-label="Page title"
        placeholder="Untitled"
        maxLength={200}
        value={title}
        readOnly={!canEdit}
        onChange={(e) => setTitle(e.target.value)}
        onBlur={() => title.trim() !== n.title && rename.mutate(title)}
        onKeyDown={(e) => e.key === 'Enter' && e.currentTarget.blur()}
      />
      {rename.error && <p role="alert">{rename.error.message}</p>}
      {/* The block editor arrives in Phase 4. */}
      <p className="page-note">{canEdit ? 'Editor coming soon.' : 'You can read this page but not edit it.'}</p>
    </article>
  )
}
