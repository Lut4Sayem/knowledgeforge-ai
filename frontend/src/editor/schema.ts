// The one editor schema. The browser editor and the Node migration script must both import it from here,
// otherwise a page converted in Node would not open in the browser. Keep this file free of CSS imports.
import { BlockNoteSchema, defaultBlockSpecs, defaultInlineContentSpecs } from '@blocknote/core'
import { bookmarkBlock } from './BookmarkBlock'
import { statusInline } from './StatusBlock'

/** Y.XmlFragment that holds a page. BlockNote's helpers default to "prosemirror": always pass this. */
export const FRAGMENT = 'document-store'

/**
 * What a page with no saved content starts from: one empty paragraph, encoded once with a fixed Yjs client id.
 * Every tab must start a new page from these exact bytes. If each one let the editor create its own first
 * block, their documents would have unrelated roots, and merging two of them (stale save, D6) makes the
 * editor throw one side's text away. The test in schema.node.test.ts regenerates and compares it.
 */
export const EMPTY_PAGE_STATE =
  'AQcBAAcBDmRvY3VtZW50LXN0b3JlAwpibG9ja0dyb3VwBwABAAMOYmxvY2tDb250YWluZXIHAAEBAwlwYXJhZ3JhcGgoAAECD2JhY2tncm91bmRDb2xvcgF3B2RlZmF1bHQoAAECCXRleHRDb2xvcgF3B2RlZmF1bHQoAAECDXRleHRBbGlnbm1lbnQBdwRsZWZ0KAABAQJpZAF3C2ZpcnN0LWJsb2NrAA=='

export const kfSchema = BlockNoteSchema.create({
  blockSpecs: { ...defaultBlockSpecs, bookmark: bookmarkBlock() },
  inlineContentSpecs: { ...defaultInlineContentSpecs, status: statusInline },
})

export type KfEditor = typeof kfSchema.BlockNoteEditor
