import { useState } from 'react'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import * as Y from 'yjs'
import Editor, { useKfEditor } from './Editor'
import type { KfEditor } from './schema'

window.matchMedia ??= ((query: string) => ({ matches: false, media: query, addEventListener() {}, removeEventListener() {}, addListener() {}, removeListener() {} })) as never
document.elementsFromPoint ??= () => []
;(globalThis as { ClipboardEvent?: unknown }).ClipboardEvent ??= class extends Event {} // jsdom has none; BlockNote's paste code names it

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

const json = (status: number, body: unknown) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

function Harness({ editable = true, onEditor, onError = () => {} }: { editable?: boolean; onEditor: (e: KfEditor) => void; onError?: (m: string) => void }) {
  const [ydoc] = useState(() => new Y.Doc())
  const editor = useKfEditor(ydoc, 'Tester', editable, { nodeId: 'n1', onError })
  onEditor(editor)
  return <Editor editor={editor} editable={editable} />
}

async function mount(props: { editable?: boolean; onError?: (m: string) => void } = {}) {
  let editor!: KfEditor
  const view = render(<Harness {...props} onEditor={(e) => (editor = e)} />)
  await new Promise((r) => setTimeout(r, 80))
  return { editor, view }
}

describe('files and bookmarks in pages', () => {
  it('pasteUrlOffersBookmark', async () => {
    const { editor, view } = await mount()
    const dom = view.container.querySelector<HTMLElement>('[contenteditable="true"]')!
    const url = 'https://example.com/article'
    fireEvent.paste(dom, { clipboardData: { types: ['text/plain'], files: [], getData: (t: string) => (t === 'text/plain' ? url : '') } })
    vi.stubGlobal('fetch', vi.fn(async () => json(200, { url, title: 'An article', description: 'About it', imageUrl: null })))

    fireEvent.click(await screen.findByRole('button', { name: 'Create bookmark' }))
    await waitFor(() => expect(editor.document.some((b) => b.type === 'bookmark' && (b.props as { url: string }).url === url)).toBe(true))
    expect(screen.queryByRole('button', { name: 'Create bookmark' })).toBeNull()
  })

  it('bookmarkFallsBackToUrl', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      if (String(input).startsWith('/api/link-preview?url=' + encodeURIComponent('https://ok.example/p'))) {
        return json(200, { url: 'https://ok.example/p', title: 'Good title', description: 'Desc', imageUrl: 'https://ok.example/i.png' })
      }
      return json(500, { message: 'boom' })
    })
    vi.stubGlobal('fetch', fetchMock)
    const { editor, view } = await mount()
    editor.insertBlocks(
      [{ type: 'bookmark', props: { url: 'https://down.example/x' } }, { type: 'bookmark', props: { url: 'https://ok.example/p' } }],
      editor.document[0],
      'after',
    )
    expect(await screen.findByText('Good title')).toBeTruthy()
    expect(screen.getByText('https://down.example/x')).toBeTruthy() // the bare URL, no error shown

    const img = view.container.querySelector('.kf-bookmark img')!
    expect(img.getAttribute('referrerpolicy')).toBe('no-referrer')
    expect(img.getAttribute('loading')).toBe('lazy')
  })

  it('rejectedFileShowsServerMessage', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => json(413, { message: 'File is too large (max 5 MB)' })))
    const onError = vi.fn()
    const { editor } = await mount({ onError })
    await expect(editor.uploadFile!(new File(['x'], 'big.pdf'))).rejects.toThrow('File is too large (max 5 MB)')
    expect(onError).toHaveBeenCalledWith('File is too large (max 5 MB)')
  })

  it('uploadsGoToTheNodeAndReturnTheDownloadUrl', async () => {
    const urls: string[] = []
    const fetchMock = vi.fn(async (u: string) => (urls.push(u), json(201, { id: 'a1' })))
    vi.stubGlobal('fetch', fetchMock)
    const { editor } = await mount()
    expect(await editor.uploadFile!(new File(['x'], 'a.png'))).toBe('/api/attachments/a1/download')
    expect(urls[0]).toBe('/api/nodes/n1/attachments/upload')
  })

  it('readOnlyPageHasNoUpload', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    const { editor, view } = await mount({ editable: false })
    expect(editor.uploadFile).toBeUndefined()
    fireEvent.paste(view.container.querySelector('.bn-editor')!, { clipboardData: { types: ['text/plain'], files: [], getData: () => 'https://example.com/x' } })
    expect(screen.queryByRole('button', { name: 'Create bookmark' })).toBeNull()
    expect(fetchMock).not.toHaveBeenCalled()
  })
})
