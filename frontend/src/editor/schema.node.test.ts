// @vitest-environment node
// Node-side code must be able to run this same schema: markdown -> blocks -> Y.Doc, no browser.
import { ServerBlockNoteEditor } from '@blocknote/server-util'
import { getDocument } from '@blocknote/server-util/dom'
import { describe, expect, it } from 'vitest'
import * as Y from 'yjs'
import { fromBase64, toBase64 } from './base64'
import { extractText } from './extractText'
import { EMPTY_PAGE_STATE, FRAGMENT, kfSchema } from './schema'

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
  const emptyPage = () => {
    const doc = new Y.Doc()
    Y.applyUpdate(doc, fromBase64(EMPTY_PAGE_STATE))
    return doc
  }
  /** Types into the page's first paragraph the way the editor binding stores text. */
  const typeInFirstParagraph = (doc: Y.Doc, text: string) => {
    const group = doc.getXmlFragment(FRAGMENT).get(0) as Y.XmlElement
    const paragraph = (group.get(0) as Y.XmlElement).get(0) as Y.XmlElement
    paragraph.insert(paragraph.length, [new Y.XmlText(text)])
  }

  it('emptyPageStateIsOneEmptyParagraphAndStaysTheSameBytes', () => {
    const server = ServerBlockNoteEditor.create({ schema: kfSchema }, getDocument())
    expect(server.yDocToBlocks(emptyPage(), FRAGMENT)).toMatchObject([{ id: 'first-block', type: 'paragraph', content: [], children: [] }])

    // Regenerated the same way: if a BlockNote upgrade changes the encoding, this fails and the constant
    // must NOT simply be replaced (pages already saved build on the old bytes).
    const fresh = new Y.Doc()
    fresh.clientID = 1
    server.blocksToYXmlFragment([{ id: 'first-block', type: 'paragraph', content: [] }] as never, fresh.getXmlFragment(FRAGMENT))
    expect(toBase64(Y.encodeStateAsUpdate(fresh))).toBe(EMPTY_PAGE_STATE)
  })

  // DO NOT update this hash. Every page created so far has these exact bytes at the root of its Yjs history,
  // and every browser tab starts a new page from them. If the constant changes, a tab on the new bytes and a
  // tab (or a saved page) on the old ones have unrelated roots again, and merging them (stale save, D6)
  // silently drops one side's text. A different first block needs a migration of all saved pages, not an edit here.
  it('emptyPageStateIsPinned', async () => {
    const digest = await crypto.subtle.digest('SHA-256', fromBase64(EMPTY_PAGE_STATE))
    const hex = [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, '0')).join('')
    expect(hex).toBe('cd20d361e8b714573442a0fcb416a81eb4e913b6e25ce7ea5cb594ba6c9c14ac')
  })

  // Found in the browser: two tabs on a brand-new page, merged after a stale save, lost one tab's text.
  it('twoTabsStartingFromAnEmptyPageMergeIntoOneDocument', () => {
    const server = ServerBlockNoteEditor.create({ schema: kfSchema }, getDocument())
    const tabA = emptyPage()
    const tabB = emptyPage()
    typeInFirstParagraph(tabA, 'from A. ')
    typeInFirstParagraph(tabB, 'from B. ')

    Y.applyUpdate(tabB, Y.encodeStateAsUpdate(tabA)) // what a 409 does
    expect(tabB.getXmlFragment(FRAGMENT).length).toBe(1) // still ONE root block group
    const text = extractText(server.yDocToBlocks(tabB, FRAGMENT))
    expect(text).toContain('from A. ')
    expect(text).toContain('from B. ')
  })

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
