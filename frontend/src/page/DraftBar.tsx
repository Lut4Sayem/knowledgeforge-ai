interface Props {
  publishState: 'DRAFT' | 'PUBLISHED'
  canEdit: boolean
  busy: boolean
  onPublish: () => void
}

/** Shown on draft pages only (wireframe Main). */
export default function DraftBar({ publishState, canEdit, busy, onPublish }: Props) {
  if (publishState === 'PUBLISHED') return null
  return (
    <div className="draft-bar" role="status">
      <strong>Draft</strong>
      <span>Your team can see this page. Publish it when it's final.</span>
      {canEdit && <button type="button" onClick={onPublish} disabled={busy}>Publish</button>}
    </div>
  )
}
