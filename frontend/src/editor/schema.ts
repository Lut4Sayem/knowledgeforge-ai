// The one editor schema. The browser editor and the Node migration script must both import it from here,
// otherwise a page converted in Node would not open in the browser. Keep this file free of CSS imports.
import { BlockNoteSchema, defaultBlockSpecs, defaultInlineContentSpecs } from '@blocknote/core'
import { bookmarkBlock } from './BookmarkBlock'
import { statusInline } from './StatusBlock'

/** Y.XmlFragment that holds a page. BlockNote's helpers default to "prosemirror": always pass this. */
export const FRAGMENT = 'document-store'

export const kfSchema = BlockNoteSchema.create({
  blockSpecs: { ...defaultBlockSpecs, bookmark: bookmarkBlock() },
  inlineContentSpecs: { ...defaultInlineContentSpecs, status: statusInline },
})

export type KfEditor = typeof kfSchema.BlockNoteEditor
