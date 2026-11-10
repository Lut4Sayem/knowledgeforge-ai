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

/** Editor bound to the page's local Y.Doc. No provider in 1a: the browser saves the encoded doc itself. */
export function useKfEditor(ydoc: Y.Doc, userName: string): KfEditor {
  return useCreateBlockNote(
    withCollaboration({
      schema: kfSchema,
      collaboration: { fragment: ydoc.getXmlFragment(FRAGMENT), user: { name: userName, color: '#1d6fcb' } },
    }),
    [ydoc],
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
