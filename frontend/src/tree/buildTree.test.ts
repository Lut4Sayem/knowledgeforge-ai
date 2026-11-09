import { describe, expect, it } from 'vitest'
import type { NodeSummary } from '../api/nodes'
import { buildTree } from './buildTree'

const n = (id: string, parentId: string | null, rank: string): NodeSummary => ({
  id, parentId, rank, title: id, icon: null, type: 'PAGE', publishState: 'DRAFT', locked: false, titleOnly: false,
})

describe('buildTree', () => {
  it('buildTreeNestsAndSorts', () => {
    const tree = buildTree([n('c2', 'a', 'b'), n('b', null, 'V'), n('a', null, 'F'), n('c1', 'a', 'a'), n('g', 'c1', 'V')])
    expect(tree.map((t) => t.id)).toEqual(['a', 'b'])
    expect(tree[0].children.map((t) => t.id)).toEqual(['c1', 'c2'])
    expect(tree[0].children[0].children.map((t) => t.id)).toEqual(['g'])
  })

  it('sorts by plain character order, ties by id', () => {
    const tree = buildTree([n('x', null, 'a'), n('y', null, 'B'), n('w', null, 'a')])
    expect(tree.map((t) => t.id)).toEqual(['y', 'w', 'x']) // 'B' < 'a' in code-unit order
  })

  it('buildTreeHandlesOrphansAtTop', () => {
    const tree = buildTree([n('a', null, 'F'), n('orphan', 'missing-parent', 'G')])
    expect(tree.map((t) => t.id)).toEqual(['a', 'orphan'])
  })
})
