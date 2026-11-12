import { diffWords } from 'diff'
import './history.css'
import type { VersionAuthor } from '../api/versions'

// Light fill + underline per person, as in the wireframe.
const COLOURS = [
  { bg: '#dcecfa', line: '#1f6fc5' },
  { bg: '#ffe8d4', line: '#dd5b00' },
  { bg: '#dff3e3', line: '#1c8a3a' },
  { bg: '#efe3fa', line: '#7b3fb8' },
]

/** D3: a version by one person gets that person's colour; a version by several shares one colour and lists every name. */
function colourFor(authors: VersionAuthor[]) {
  if (authors.length !== 1) return COLOURS[0]
  let h = 0
  for (const c of authors[0].userId) h = (h * 31 + c.charCodeAt(0)) >>> 0
  return COLOURS[h % COLOURS.length]
}

/** Words added in `text` (coloured) and removed from `previousText` (struck through). */
export default function DiffView({ text, previousText, authors }: { text: string; previousText: string; authors: VersionAuthor[] }) {
  const colour = colourFor(authors)
  const parts = diffWords(previousText, text)
  return (
    <div className="kf-diff" style={{ '--ins-bg': colour.bg, '--ins-line': colour.line } as React.CSSProperties}>
      <p className="kf-diff-text">
        {parts.map((p, i) =>
          p.added ? <ins key={i}>{p.value}</ins> : p.removed ? <del key={i}>{p.value}</del> : <span key={i}>{p.value}</span>,
        )}
      </p>
      <p className="kf-diff-legend">
        <span aria-hidden="true" className="kf-diff-swatch" />
        {authors.length ? `${authors.map((a) => a.name).join(', ')}'s changes` : 'Changes'}
        <span>Struck-through text was removed</span>
      </p>
    </div>
  )
}
