import { BlockNoteEditor } from '@blocknote/core'
import { yDocToBlocks } from '@blocknote/core/yjs'
import type * as Y from 'yjs'
import { extractText } from './extractText'
import { FRAGMENT, kfSchema } from './schema'

let converter: BlockNoteEditor<never, never, never> | undefined

/** Plain text of a page document without a mounted editor (same result as extractText on the open editor). */
export function docText(ydoc: Y.Doc): string {
  converter ??= BlockNoteEditor.create({ schema: kfSchema }) as never
  return extractText(yDocToBlocks(converter!, ydoc, FRAGMENT))
}
