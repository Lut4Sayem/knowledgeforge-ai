import '@blocknote/core/fonts/inter.css'
import '@blocknote/mantine/style.css'
import './editor.css'
import { filterSuggestionItems } from '@blocknote/core'
import { withCollaboration } from '@blocknote/core/yjs'
import { BlockNoteView } from '@blocknote/mantine'
import { SuggestionMenuController, useCreateBlockNote } from '@blocknote/react'
import { useState } from 'react'
import type * as Y from 'yjs'
import { uploadAttachment } from '../api/attachments'
import { safeHttpUrl } from './BookmarkBlock'
import { extractText } from './extractText'
import { FRAGMENT, kfSchema, type KfEditor } from './schema'
import { getKfSlashItems } from './slashItems'

/**
 * Editor bound to the page's local Y.Doc. No provider in 1a: the browser saves the encoded doc itself.
 * `editable` is part of the editor's identity: flipping it on a live BlockNote editor leaves Ctrl+Z dead
 * (the Yjs undo manager does not survive it), so a change builds a fresh editor on the same Y.Doc.
 * The undo history starts over at that moment, which only happens when the soft lock changes hands.
 * Files (image and file blocks, drag-drop) upload to the page only while it is editable; `onError` gets the server's message.
 */
export function useKfEditor(
  ydoc: Y.Doc,
  userName: string,
  editable: boolean,
  files?: { nodeId: string; onError: (message: string) => void },
): KfEditor {
  return useCreateBlockNote(
    withCollaboration({
      schema: kfSchema,
      uploadFile:
        editable && files
          ? async (file: File) => {
              try {
                return await uploadAttachment(files.nodeId, file)
              } catch (e) {
                files.onError(e instanceof Error ? e.message : 'Upload failed')
                throw e
              }
            }
          : undefined,
      collaboration: { fragment: ydoc.getXmlFragment(FRAGMENT), user: { name: userName, color: '#1d6fcb' } },
    }),
    [ydoc, editable, files?.nodeId],
  ) as KfEditor
}

export default function Editor({ editor, editable }: { editor: KfEditor; editable: boolean }) {
  // A lone http(s) link pasted into the page: remembered so "Create bookmark" can turn it into a card.
  const [offer, setOffer] = useState<{ url: string; blockId: string } | null>(null)

  const onPaste = (e: React.ClipboardEvent) => {
    if (!editable) return
    const text = e.clipboardData?.getData('text/plain')?.trim() ?? ''
    if (/\s/.test(text) || !safeHttpUrl(text)) return setOffer(null)
    setTimeout(() => {
      // after the default paste has put the link into the page
      try {
        setOffer({ url: text, blockId: editor.getTextCursorPosition().block.id })
      } catch {
        setOffer({ url: text, blockId: editor.document[editor.document.length - 1].id })
      }
    }, 0)
  }

  const createBookmark = () => {
    if (!offer) return
    const block = editor.getBlock(offer.blockId)
    const props = { url: offer.url }
    if (block && extractText([block]).trim() === offer.url) editor.replaceBlocks([block], [{ type: 'bookmark', props }])
    else editor.insertBlocks([{ type: 'bookmark', props }], block ?? editor.document[editor.document.length - 1], 'after')
    setOffer(null)
  }

  return (
    <div onPasteCapture={onPaste}>
      <BlockNoteView editor={editor} editable={editable} slashMenu={false} theme="light">
        <SuggestionMenuController
          triggerCharacter="/"
          getItems={async (query) => filterSuggestionItems(getKfSlashItems(editor), query)}
        />
      </BlockNoteView>
      {editable && offer && (
        <p className="kf-offer" role="status">
          Link pasted.{' '}
          <button type="button" onClick={createBookmark}>
            Create bookmark
          </button>{' '}
          <button type="button" onClick={() => setOffer(null)}>
            Dismiss
          </button>
        </p>
      )}
    </div>
  )
}
