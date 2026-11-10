import { createReactInlineContentSpec } from '@blocknote/react'
import { useState } from 'react'

export const STATUS_COLORS = ['gray', 'blue', 'green', 'yellow', 'red', 'purple'] as const
export type StatusColor = (typeof STATUS_COLORS)[number]

interface ChipProps {
  label: string
  color: string
  editable: boolean
  onChange: (patch: { label?: string; color?: StatusColor }) => void
}

/** Status lozenge. Click it (when editable) to change the label and colour in place. */
export function StatusChip({ label, color, editable, onChange }: ChipProps) {
  const [open, setOpen] = useState(false)
  const [draft, setDraft] = useState(label)
  const cls = `kf-status kf-status--${color}`
  if (!editable) return <span className={cls}>{label}</span>

  const commit = () => {
    const next = draft.trim()
    if (next && next !== label) onChange({ label: next })
  }
  return (
    <span className={cls} contentEditable={false}>
      <button type="button" aria-expanded={open} title="Edit status" onClick={() => { setDraft(label); setOpen(!open) }}>
        {label}
      </button>
      {open && (
        <span className="kf-status-edit">
          <input
            aria-label="Status label"
            maxLength={40}
            autoFocus
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            onBlur={commit}
            onKeyDown={(e) => {
              if (e.key === 'Enter') { e.preventDefault(); commit() }
              if (e.key === 'Escape') setOpen(false)
            }}
          />
          <span role="radiogroup" aria-label="Status colour">
            {STATUS_COLORS.map((c) => (
              <button
                key={c}
                type="button"
                role="radio"
                aria-checked={c === color}
                aria-label={c}
                className={`kf-status-swatch kf-status--${c}`}
                onClick={() => onChange({ color: c })}
              />
            ))}
          </span>
        </span>
      )}
    </span>
  )
}

export const statusInline = createReactInlineContentSpec(
  {
    type: 'status',
    propSchema: { label: { default: 'Status' }, color: { default: 'gray', values: STATUS_COLORS } },
    content: 'none',
  },
  {
    render: ({ inlineContent, updateInlineContent, editor }) => (
      <StatusChip
        label={inlineContent.props.label}
        color={inlineContent.props.color}
        editable={editor.isEditable}
        onChange={(patch) => updateInlineContent({ type: 'status', props: { ...inlineContent.props, ...patch } })}
      />
    ),
  },
)
