import '@blocknote/core/fonts/inter.css'
import '@blocknote/mantine/style.css'
import './editor.css'
import { filterSuggestionItems } from '@blocknote/core'
import { withCollaboration } from '@blocknote/core/yjs'
import { BlockNoteView } from '@blocknote/mantine'
import { SuggestionMenuController, useCreateBlockNote } from '@blocknote/react'
import type * as Y from 'yjs'
import { FRAGMENT, kfSchema, type KfEditor } from './schema'
import { getKfSlashItems } from './slashItems'

/**
 * Editor bound to the page's local Y.Doc. No provider in 1a: the browser saves the encoded doc itself.
 * `editable` is part of the editor's identity: flipping it on a live BlockNote editor leaves Ctrl+Z dead
 * (the Yjs undo manager does not survive it), so a change builds a fresh editor on the same Y.Doc.
 * The undo history starts over at that moment, which only happens when the soft lock changes hands.
 */
export function useKfEditor(ydoc: Y.Doc, userName: string, editable: boolean): KfEditor {
  return useCreateBlockNote(
    withCollaboration({
      schema: kfSchema,
      collaboration: { fragment: ydoc.getXmlFragment(FRAGMENT), user: { name: userName, color: '#1d6fcb' } },
    }),
    [ydoc, editable],
  ) as KfEditor
}

export default function Editor({ editor, editable }: { editor: KfEditor; editable: boolean }) {
  return (
    <BlockNoteView editor={editor} editable={editable} slashMenu={false} theme="light">
      <SuggestionMenuController
        triggerCharacter="/"
        getItems={async (query) => filterSuggestionItems(getKfSlashItems(editor), query)}
      />
    </BlockNoteView>
  )
}
