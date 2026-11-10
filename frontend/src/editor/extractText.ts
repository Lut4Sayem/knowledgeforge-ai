// Plain text of a page for search and diff: one line per block (one per table row), status labels included.
// Takes blocks, not the editor, so the Node migration script can use it on converted content too.
interface AnyBlock {
  type: string
  props?: Record<string, unknown>
  content?: unknown
  children?: AnyBlock[]
}

function inlineText(content: unknown): string {
  if (typeof content === 'string') return content
  if (!Array.isArray(content)) return ''
  return content
    .map((c) => {
      if (c.type === 'text') return c.text
      if (c.type === 'status') return c.props?.label ?? ''
      return inlineText(c.content) // links and anything else that wraps text
    })
    .join('')
}

function blockLines(block: AnyBlock): string[] {
  const c = block.content as { type?: string; rows?: { cells: unknown[] }[] } | undefined
  let own: string[]
  if (c?.type === 'tableContent') {
    // A cell is either {type: 'tableCell', content} or a bare inline array (older shape).
    own = c.rows!.map((row) => row.cells.map((cell) => inlineText((cell as { content?: unknown }).content ?? cell)).join('\t'))
  } else if (block.type === 'bookmark') {
    own = [[block.props?.title, block.props?.url].filter(Boolean).join(' ')]
  } else {
    own = [inlineText(block.content) || String(block.props?.caption ?? block.props?.name ?? '')]
  }
  return [...own, ...(block.children ?? []).flatMap(blockLines)]
}

export function extractText(blocks: readonly unknown[]): string {
  return (blocks as AnyBlock[])
    .flatMap(blockLines)
    .filter((line) => line.trim() !== '')
    .join('\n')
}
