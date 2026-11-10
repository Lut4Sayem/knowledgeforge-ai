// @vitest-environment node
// The migration script (Phase 9) runs this same schema in Node: markdown -> blocks -> Y.Doc, no browser.
import { ServerBlockNoteEditor } from '@blocknote/server-util'
import { getDocument } from '@blocknote/server-util/dom'
import { describe, expect, it } from 'vitest'
import * as Y from 'yjs'
import { fromBase64, toBase64 } from './base64'
import { extractText } from './extractText'
import { FRAGMENT, kfSchema } from './schema'

const md = `# Title

Some **bold** and a [link](https://example.com).

- one
  - nested

- [ ] todo

| A | B |
|---|---|
| 1 | 2 |
`

type Loose = { id?: string; children?: Loose[]; [k: string]: unknown }
const strip = (blocks: Loose[]): Loose[] =>
  blocks.map((b) => {
    const rest = { ...b, children: strip(b.children ?? []) }
    delete rest.id
    return rest
  })

describe('kfSchema in Node', () => {
  it('customBlocksRoundTripThroughMarkdownToYDoc', async () => {
    const server = ServerBlockNoteEditor.create({ schema: kfSchema }, getDocument())
    const fromMarkdown = await server.tryParseMarkdownToBlocks(md)
    expect(fromMarkdown.map((b) => b.type)).toEqual(['heading', 'paragraph', 'bulletListItem', 'checkListItem', 'table'])

    const custom = [
      {
        type: 'paragraph',
        content: [
          { type: 'text', text: 'State: ', styles: {} },
          { type: 'status', props: { label: 'In progress', color: 'blue' } },
        ],
      },
      { type: 'bookmark', props: { url: 'https://example.com/a', title: 'Example', description: 'A page', imageUrl: 'https://example.com/a.png' } },
    ]
    // Through a first Y.Doc so the custom blocks get their defaults and ids filled in like the parsed ones.
    const blocks = server.yDocToBlocks(server.blocksToYDoc([...fromMarkdown, ...custom] as never, FRAGMENT), FRAGMENT)

    const ydoc = server.blocksToYDoc(blocks, FRAGMENT)
    expect(ydoc.getXmlFragment(FRAGMENT).length).toBeGreaterThan(0)
    expect(ydoc.getXmlFragment('prosemirror').length).toBe(0) // the helper's default fragment must stay unused

    // Same path as server -> browser: bytes -> base64 -> a fresh doc.
    const b64 = toBase64(Y.encodeStateAsUpdate(ydoc))
    const copy = new Y.Doc()
    Y.applyUpdate(copy, fromBase64(b64))
    const back = server.yDocToBlocks(copy, FRAGMENT)

    expect(strip(back as Loose[])).toEqual(strip(blocks as Loose[]))
    const json = JSON.stringify(back)
    expect(json).toContain('"type":"status","props":{"label":"In progress","color":"blue"}')
    expect(back.at(-1)).toMatchObject({
      type: 'bookmark',
      props: { url: 'https://example.com/a', title: 'Example', description: 'A page', imageUrl: 'https://example.com/a.png' },
    })
    expect(extractText(back)).toContain('State: In progress')
  })
})
