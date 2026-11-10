import { BlockNoteEditor } from '@blocknote/core'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import * as Y from 'yjs'
import Editor, { useKfEditor } from './Editor'
import { extractText } from './extractText'
import { FRAGMENT, kfSchema, type KfEditor } from './schema'
import { getKfSlashItems } from './slashItems'
import { StatusChip } from './StatusBlock'

// jsdom has no matchMedia; Mantine asks it for the colour scheme.
window.matchMedia ??= ((query: string) => ({ matches: false, media: query, addEventListener() {}, removeEventListener() {}, addListener() {}, removeListener() {} })) as never
document.elementsFromPoint ??= () => [] // nor layout; BlockNote's side menu asks on mouse moves

afterEach(cleanup)

function Harness({ ydoc, onEditor }: { ydoc: Y.Doc; onEditor: (e: KfEditor) => void }) {
  const editor = useKfEditor(ydoc, 'Tester')
  onEditor(editor)
  return <Editor editor={editor} editable />
}

async function mountEditor() {
  const ydoc = new Y.Doc()
  let editor!: KfEditor
  const view = render(<Harness ydoc={ydoc} onEditor={(e) => (editor = e)} />)
  const dom = await waitFor(() => {
    const el = view.container.querySelector<HTMLElement>('[contenteditable="true"]')
    if (!el) throw new Error('editor not mounted')
    return el
  })
  // y-prosemirror renders the fragment into the editor on a timeout after mount.
  await new Promise((r) => setTimeout(r, 50))
  return { ydoc, editor, dom }
}

describe('block editor', () => {
  it('slashMenuHasRequiredItems', () => {
    const editor = BlockNoteEditor.create({ schema: kfSchema })
    const keys = getKfSlashItems(editor).map((i) => i.key)
    for (const required of [
      'paragraph', 'heading', 'heading_2', 'heading_3', 'bullet_list', 'numbered_list', 'check_list',
      'quote', 'divider', 'code_block', 'image', 'file', 'table', 'status', 'bookmark',
    ]) {
      expect(keys, `slash menu is missing "${required}"`).toContain(required)
    }
  })

  it('undoRedoWithCtrlZY', async () => {
    const { ydoc, editor, dom } = await mountEditor()
    const fragment = ydoc.getXmlFragment(FRAGMENT)
    const texts = () => extractText(editor.document)

    editor.insertBlocks([{ type: 'paragraph', content: 'to undo' }], editor.document[0], 'after')
    expect(texts()).toContain('to undo')
    expect(fragment.toString()).toContain('to undo') // the edit lives in the Y.Doc

    fireEvent.keyDown(dom, { key: 'z', code: 'KeyZ', ctrlKey: true })
    expect(texts()).not.toContain('to undo')
    expect(fragment.toString()).not.toContain('to undo') // undone in the Y.Doc, so it was the Yjs undo manager

    fireEvent.keyDown(dom, { key: 'y', code: 'KeyY', ctrlKey: true })
    expect(texts()).toContain('to undo')
    expect(fragment.toString()).toContain('to undo')
  })

  it('statusChipEditableInPlace', async () => {
    const onChange = vi.fn()
    render(<StatusChip label="In progress" color="blue" editable onChange={onChange} />)

    await userEvent.click(screen.getByRole('button', { name: /In progress/ }))
    const input = screen.getByLabelText('Status label')
    await userEvent.clear(input)
    await userEvent.type(input, 'Done{Enter}')
    expect(onChange).toHaveBeenCalledWith({ label: 'Done' })

    await userEvent.click(screen.getByRole('radio', { name: 'green' }))
    expect(onChange).toHaveBeenCalledWith({ color: 'green' })
  })

  it('statusChipIsReadOnlyWhenNotEditable', async () => {
    render(<StatusChip label="Blocked" color="red" editable={false} onChange={() => {}} />)
    expect(screen.queryByRole('button')).toBeNull()
    expect(screen.getByText('Blocked')).toBeTruthy()
  })

  it('statusChipInsideTheEditorUpdatesTheDocument', async () => {
    const { editor, dom } = await mountEditor()
    editor.replaceBlocks(editor.document, [
      { type: 'paragraph', content: [{ type: 'status', props: { label: 'Todo', color: 'gray' } }] },
    ])
    const chip = await waitFor(() => {
      const el = dom.querySelector<HTMLElement>('.kf-status button')
      if (!el) throw new Error('chip not rendered')
      return el
    })
    await userEvent.click(chip)
    await userEvent.click(await screen.findByRole('radio', { name: 'purple' }))

    await waitFor(() => expect(JSON.stringify(editor.document)).toContain('"color":"purple"'))
  })

  it('extractTextIncludesStatusAndTable', () => {
    const cell = (text: string) => ({ type: 'tableCell', content: [{ type: 'text', text, styles: {} }] })
    const text = extractText([
      { type: 'heading', content: [{ type: 'text', text: 'Plan', styles: {} }], children: [] },
      {
        type: 'paragraph',
        content: [
          { type: 'text', text: 'Survey is ', styles: {} },
          { type: 'status', props: { label: 'In progress', color: 'blue' } },
          { type: 'text', text: ' see ', styles: {} },
          { type: 'link', href: 'https://example.com', content: [{ type: 'text', text: 'the form', styles: {} }] },
        ],
        children: [{ type: 'bulletListItem', content: [{ type: 'text', text: 'nested item', styles: {} }], children: [] }],
      },
      { type: 'table', content: { type: 'tableContent', rows: [{ cells: [cell('Name'), cell('Role')] }, { cells: [cell('Rafi'), cell('Lead')] }] }, children: [] },
      { type: 'bookmark', props: { url: 'https://kf.example/a', title: 'A site', description: '', imageUrl: '' }, children: [] },
      { type: 'paragraph', content: [], children: [] },
    ])

    expect(text.split('\n')).toEqual([
      'Plan',
      'Survey is In progress see the form',
      'nested item',
      'Name\tRole',
      'Rafi\tLead',
      'A site https://kf.example/a',
    ])
  })
})
