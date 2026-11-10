import { createReactBlockSpec } from '@blocknote/react'

/** Only http(s) links may be rendered: page content is written by other users. */
export function safeHttpUrl(value: string): string | null {
  try {
    const u = new URL(value.trim())
    return u.protocol === 'http:' || u.protocol === 'https:' ? u.href : null
  } catch {
    return null
  }
}

/** Link card. Renders from its props only; the preview fetch that fills title/description/image comes in Phase 6. */
export const bookmarkBlock = createReactBlockSpec(
  {
    type: 'bookmark',
    propSchema: { url: { default: '' }, title: { default: '' }, description: { default: '' }, imageUrl: { default: '' } },
    content: 'none',
  },
  {
    render: ({ block, editor }) => {
      const { url, title, description, imageUrl } = block.props
      const href = safeHttpUrl(url)
      const image = safeHttpUrl(imageUrl)
      if (!href) {
        if (!editor.isEditable) return <div className="kf-bookmark">{url || 'Empty bookmark'}</div>
        return (
          <input
            className="kf-bookmark kf-bookmark-input"
            aria-label="Bookmark link"
            placeholder="Paste a link and press Enter"
            defaultValue={url}
            onKeyDown={(e) => {
              if (e.key !== 'Enter') return
              e.preventDefault()
              const next = safeHttpUrl(e.currentTarget.value)
              if (next) editor.updateBlock(block, { props: { url: next } })
            }}
          />
        )
      }
      return (
        <a className="kf-bookmark" href={href} target="_blank" rel="noopener noreferrer" contentEditable={false}>
          <span className="kf-bookmark-text">
            <strong>{title || href}</strong>
            {description && <span>{description}</span>}
            {title && <small>{href}</small>}
          </span>
          {image && <img src={image} alt="" loading="lazy" />}
        </a>
      )
    },
  },
)
