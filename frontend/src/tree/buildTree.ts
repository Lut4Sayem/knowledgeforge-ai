import type { NodeSummary } from '../api/nodes'

export interface TreeNode extends NodeSummary {
  children: TreeNode[]
}

// Plain code-unit order on purpose: rank keys are ordered that way on the server (localeCompare would differ).
const byRank = (a: NodeSummary, b: NodeSummary) =>
  a.rank < b.rank ? -1 : a.rank > b.rank ? 1 : a.id < b.id ? -1 : a.id > b.id ? 1 : 0

/** Flat list -> forest. A node whose parent is not in the list (hidden or gone) is shown at the top level. */
export function buildTree(nodes: NodeSummary[]): TreeNode[] {
  const byId = new Map<string, TreeNode>(nodes.map((n) => [n.id, { ...n, children: [] }]))
  const top: TreeNode[] = []
  for (const n of byId.values()) {
    const parent = n.parentId ? byId.get(n.parentId) : undefined
    ;(parent ? parent.children : top).push(n)
  }
  const sort = (list: TreeNode[]) => {
    list.sort(byRank)
    list.forEach((n) => sort(n.children))
  }
  sort(top)
  return top
}
