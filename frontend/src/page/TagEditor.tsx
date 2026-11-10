import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { updateNode, type NodeDetail } from '../api/nodes'

/** Tag chips under the title; each links to the tag's page list. Editors can add and remove. */
export default function TagEditor({ node, canEdit }: { node: NodeDetail; canEdit: boolean }) {
  const qc = useQueryClient()
  const [draft, setDraft] = useState('')
  const [error, setError] = useState('')

  async function save(tags: string[]) {
    setError('')
    try {
      qc.setQueryData(['node', node.id], await updateNode(node.id, { tags }))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not save the tags.')
    }
  }

  function add() {
    const tag = draft.trim().replace(/^#/, '').toLowerCase()
    setDraft('')
    if (tag && !node.tags.includes(tag)) void save([...node.tags, tag])
  }

  if (!canEdit && node.tags.length === 0) return null
  return (
    <div className="tags">
      <ul className="tag-list" aria-label="Tags">
        {node.tags.map((t) => (
          <li key={t} className="tag">
            <Link to={`/tags/${encodeURIComponent(t)}`}>#{t}</Link>
            {canEdit && <button type="button" aria-label={`Remove tag ${t}`} onClick={() => void save(node.tags.filter((x) => x !== t))}>×</button>}
          </li>
        ))}
      </ul>
      {canEdit && (
        <input
          className="tag-input"
          aria-label="Add tag"
          placeholder="+ Add tag"
          maxLength={40}
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' || e.key === ',') {
              e.preventDefault()
              add()
            }
          }}
          onBlur={add}
        />
      )}
      {error && <p role="alert">{error}</p>}
    </div>
  )
}
