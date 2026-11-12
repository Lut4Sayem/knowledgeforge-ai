import { createReactBlockSpec } from '@blocknote/react'
import { useEffect, useState } from 'react'
import { getLinkPreview } from '../api/attachments'

/** Only http(s) links may be rendered: page content is written by other users. */
export function safeHttpUrl(value: string): string | null {
  try {
    const u = new URL(value.trim())
    return u.protocol === 'http:' || u.protocol === 'https:' ? u.href : null
  } catch {
    return null
  }
}

type Props = { url: string; title: string; description: string; imageUrl: string }

/**
 * Link card. A card without a title asks the server for one; any failure leaves the bare URL.
 * Only an editor saves the result into the block; a read-only page just shows it.
 */
function BookmarkCard({ props, href, save }: { props: Props; href: string; save?: (p: Partial<Props>) => void }) {
  const [fetched, setFetched] = useState<Partial<Props>>({})
  useEffect(() => {
    if (props.title) return
    let cancelled = false
    getLinkPreview(href)
      .then((p) => {
        const next = { title: p.title ?? '', description: p.description ?? '', imageUrl: p.imageUrl ?? '' }
        if (cancelled || !next.title) return
        setFetched(next)
        save?.(next)
      })
      .catch(() => {}) // the bare URL stays
    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [href, props.title])
  const title = props.title || fetched.title
  const description = props.description || fetched.description
  const image = safeHttpUrl(props.imageUrl || fetched.imageUrl || '')
  return (
    <a className="kf-bookmark" href={href} target="_blank" rel="noopener noreferrer" contentEditable={false}>
      <span className="kf-bookmark-text">
        <strong>{title || href}</strong>
        {description && <span>{description}</span>}
        {title && <small>{href}</small>}
      </span>
      {/* a third-party image: no referrer, so the page address is not sent along */}
      {image && <img src={image} alt="" loading="lazy" referrerPolicy="no-referrer" />}
    </a>
  )
}

/** Link card block. */
export const bookmarkBlock = createReactBlockSpec(
  {
    type: 'bookmark',
    propSchema: { url: { default: '' }, title: { default: '' }, description: { default: '' }, imageUrl: { default: '' } },
    content: 'none',
  },
  {
    render: ({ block, editor }) => {
      const href = safeHttpUrl(block.props.url)
      if (!href) {
        if (!editor.isEditable) return <div className="kf-bookmark">{block.props.url || 'Empty bookmark'}</div>
        return (
          <input
            className="kf-bookmark kf-bookmark-input"
            aria-label="Bookmark link"
            placeholder="Paste a link and press Enter"
            defaultValue={block.props.url}
            onKeyDown={(e) => {
              if (e.key !== 'Enter') return
              e.preventDefault()
              const next = safeHttpUrl(e.currentTarget.value)
              if (next) editor.updateBlock(block, { props: { url: next } })
            }}
          />
        )
      }
      return <BookmarkCard props={block.props} href={href} save={editor.isEditable ? (p) => editor.updateBlock(block, { props: p }) : undefined} />
    },
  },
)
