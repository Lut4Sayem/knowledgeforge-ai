import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { createPortal } from 'react-dom'
import { useParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import { getNode, updateNode, type NodeDetail } from '../api/nodes'
import { getMySpaces } from '../api/spaces'
import { openContent, useAutosave, type Opened } from '../editor/autosave'
import Editor, { useKfEditor } from '../editor/Editor'
import { extractText } from '../editor/extractText'
import { pendingStore } from '../editor/pendingStore'
import SaveIndicator, { ConflictBanner } from '../editor/SaveIndicator'
import Breadcrumbs from './Breadcrumbs'

interface Slots {
  head: HTMLElement | null // top bar, next to the breadcrumbs
  banners: HTMLElement | null // between the top bar and the page
}

export default function PageView() {
  const { nodeId = '' } = useParams()
  const qc = useQueryClient()
  const node = useQuery({ queryKey: ['node', nodeId], queryFn: () => getNode(nodeId), retry: false })
  const spaces = useQuery({ queryKey: ['spaces'], queryFn: getMySpaces })
  const [title, setTitle] = useState('')
  const [head, setHead] = useState<HTMLElement | null>(null)
  const [banners, setBanners] = useState<HTMLElement | null>(null)
  const rename = useMutation({
    mutationFn: (t: string) => updateNode(nodeId, { title: t }),
    onSuccess: async (updated) => {
      qc.setQueryData(['node', nodeId], updated)
      await qc.invalidateQueries({ queryKey: ['tree', updated.spaceId] })
    },
  })

  useEffect(() => setTitle(node.data?.title ?? ''), [node.data?.title, nodeId])

  if (node.isLoading) return <p>Loading…</p>
  if (node.error) {
    const s = node.error instanceof ApiError ? node.error.status : 0
    return <p role="alert">{s === 404 ? 'This page does not exist.' : s === 403 ? "You don't have access to this page." : 'Could not load this page.'}</p>
  }
  const n = node.data!
  const canEdit = n.access === 'EDIT'
  const spaceName = spaces.data?.find((s) => s.id === n.spaceId)?.name

  return (
    <>
      <header className="page-head">
        <Breadcrumbs spaceName={spaceName} crumbs={n.breadcrumbs} current={n.title} />
        <span ref={setHead} />
      </header>
      <div ref={setBanners} />
      <article className="page">
        <input
          className="page-title"
          aria-label="Page title"
          placeholder="Untitled"
          maxLength={200}
          value={title}
          readOnly={!canEdit}
          onChange={(e) => setTitle(e.target.value)}
          onBlur={() => title.trim() !== n.title && rename.mutate(title)}
          onKeyDown={(e) => e.key === 'Enter' && e.currentTarget.blur()}
        />
        {rename.error && <p role="alert">{rename.error.message}</p>}
        {n.access === 'TITLE_ONLY' ? (
          <p className="page-note">You can see this page's title but not its content.</p>
        ) : (
          <PageContent key={n.id} node={n} slots={{ head, banners }} />
        )}
      </article>
    </>
  )
}

/** Loads the content (server copy + any unconfirmed local copy), then shows the editor. */
function PageContent({ node, slots }: { node: NodeDetail; slots: Slots }) {
  const canEdit = node.access === 'EDIT'
  const [opened, setOpened] = useState<Opened>()
  const [failed, setFailed] = useState(false)
  const [reloads, setReloads] = useState(0)

  useEffect(() => {
    let live = true
    setOpened(undefined)
    setFailed(false)
    openContent(node.id, canEdit).then(
      (o) => live && setOpened(o),
      () => live && setFailed(true),
    )
    return () => {
      live = false
    }
  }, [node.id, canEdit, reloads])

  if (failed) {
    return (
      <p role="alert">
        Could not load this page's content. <button type="button" onClick={() => setReloads((r) => r + 1)}>Try again</button>
      </p>
    )
  }
  if (!opened) return <p className="page-note">Loading…</p>
  return (
    <PageEditor
      node={node}
      opened={opened}
      slots={slots}
      // "Reload latest": give up my copy and load the server's.
      onReload={() => void pendingStore.clear(node.id, opened.slot).then(() => setReloads((r) => r + 1))}
    />
  )
}

function PageEditor({ node, opened, slots, onReload }: { node: NodeDetail; opened: Opened; slots: Slots; onReload: () => void }) {
  const canEdit = node.access === 'EDIT'
  const editor = useKfEditor(opened.ydoc, 'Me')
  const save = useAutosave(node.id, opened, () => extractText(editor.document), canEdit)

  return (
    <>
      {canEdit && slots.head && createPortal(<SaveIndicator state={save.state} />, slots.head)}
      {save.state === 'conflict' && slots.banners && createPortal(<ConflictBanner onReload={onReload} onKeepMine={save.keepMine} />, slots.banners)}
      {!canEdit && <p className="page-note">You can read this page but not edit it.</p>}
      <Editor editor={editor} editable={canEdit} />
    </>
  )
}
