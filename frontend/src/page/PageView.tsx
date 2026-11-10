import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { createPortal } from 'react-dom'
import { useParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import { getNode, updateNode, type NodeDetail } from '../api/nodes'
import { getMe, getMySpaces } from '../api/spaces'
import { openContent, RECOVERED_NOTICE, useAutosave, type Opened } from '../editor/autosave'
import Editor, { useKfEditor } from '../editor/Editor'
import { extractText } from '../editor/extractText'
import SaveIndicator, { SaveNotice } from '../editor/SaveIndicator'
import SoftLockBanner, { useEditLock } from '../editor/SoftLockBanner'
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
  const me = useQuery({ queryKey: ['me'], queryFn: getMe })
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
        ) : me.data ? (
          <PageContent key={n.id} node={n} userId={me.data.id} slots={{ head, banners }} />
        ) : (
          <p className="page-note">Loading…</p>
        )}
      </article>
    </>
  )
}

/** Loads the content (server copy + this user's unconfirmed local copies), then shows the editor. */
function PageContent({ node, userId, slots }: { node: NodeDetail; userId: string; slots: Slots }) {
  const canEdit = node.access === 'EDIT'
  const [opened, setOpened] = useState<Opened>()
  const [failed, setFailed] = useState(false)
  const [reloads, setReloads] = useState(0)
  const [notice, setNotice] = useState('')

  useEffect(() => {
    let live = true
    setOpened(undefined)
    setFailed(false)
    openContent(node.id, canEdit, userId).then(
      (o) => (live ? setOpened(o) : o.release()), // nobody will use it: its slot must not look alive
      () => live && setFailed(true),
    )
    return () => {
      live = false
    }
  }, [node.id, canEdit, userId, reloads])

  if (failed) {
    return (
      <p role="alert">
        Could not load this page's content. <button type="button" onClick={() => setReloads((r) => r + 1)}>Try again</button>
      </p>
    )
  }
  return (
    <>
      {notice && slots.banners && createPortal(<SaveNotice onDismiss={() => setNotice('')}>{notice}</SaveNotice>, slots.banners)}
      {opened ? (
        <PageEditor
          node={node}
          opened={opened}
          slots={slots}
          // The local copy is now a private version: say so and load what the server has.
          onRecovered={() => {
            setNotice(RECOVERED_NOTICE)
            setReloads((r) => r + 1)
          }}
        />
      ) : (
        <p className="page-note">Loading…</p>
      )}
    </>
  )
}

function PageEditor({ node, opened, slots, onRecovered }: { node: NodeDetail; opened: Opened; slots: Slots; onRecovered: () => void }) {
  const canEdit = node.access === 'EDIT'
  const lock = useEditLock(node.id, canEdit)
  const editable = canEdit && lock.mode === 'editing'
  const editor = useKfEditor(opened.ydoc, 'Me', editable)
  const save = useAutosave(node.id, opened, () => extractText(editor.document), canEdit)
  const problem = save.state === 'failed' ? `Not saved: ${save.message}` : save.state === 'retrying' || save.state === 'offline' ? save.message : undefined

  useEffect(() => {
    if (save.state === 'recovered') onRecovered()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [save.state])

  return (
    <>
      {canEdit && slots.head && createPortal(<SaveIndicator state={save.state} local={save.local} />, slots.head)}
      {problem && slots.banners && createPortal(<SaveNotice>{problem}</SaveNotice>, slots.banners)}
      {lock.mode === 'blocked' && slots.banners && createPortal(
        <SoftLockBanner holder={lock.holder} onReadOnly={lock.openReadOnly} onEditAnyway={lock.editAnyway} />,
        slots.banners,
      )}
      {!canEdit && <p className="page-note">{node.locked ? 'This page is locked.' : 'You can read this page but not edit it.'}</p>}
      {lock.mode === 'readonly' && <p className="page-note">Opened read-only: {lock.holder} is editing this page.</p>}
      {lock.mode === 'denied' && <p className="page-note">You can no longer edit this page.</p>}
      <Editor editor={editor} editable={editable} />
    </>
  )
}
