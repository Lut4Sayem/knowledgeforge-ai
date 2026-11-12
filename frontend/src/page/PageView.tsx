import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { createPortal } from 'react-dom'
import { useParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import { getNode, publishNode, updateNode, type NodeDetail } from '../api/nodes'
import { restoreVersion } from '../api/versions'
import { getMe, getMySpaces } from '../api/spaces'
import { openContent, RECOVERED_NOTICE, useAutosave, type Opened, type SaveState } from '../editor/autosave'
import Editor, { useKfEditor } from '../editor/Editor'
import { extractText } from '../editor/extractText'
import SaveIndicator, { SaveNotice } from '../editor/SaveIndicator'
import { useEndSessionOnLeave } from '../editor/session'
import SoftLockBanner, { useEditLock } from '../editor/SoftLockBanner'
import HistoryPanel, { type Viewing } from '../history/HistoryPanel'
import VersionViewer from '../history/VersionViewer'
import Breadcrumbs from './Breadcrumbs'
import DraftBar from './DraftBar'
import PageMenu from './PageMenu'
import TagEditor from './TagEditor'

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
  const [saveState, setSaveState] = useState<SaveState>('saved')
  const [historyOpen, setHistoryOpen] = useState(false)
  const [viewing, setViewing] = useState<Viewing | null>(null)
  const [contentKey, setContentKey] = useState(0) // bumped after a restore: the editor opens again on the server's state
  const restore = useMutation({
    mutationFn: (id: string) => restoreVersion(id),
    onSuccess: async () => {
      setViewing(null)
      setContentKey((k) => k + 1)
      await qc.invalidateQueries({ queryKey: ['versions', nodeId] })
      await qc.invalidateQueries({ queryKey: ['node', nodeId] })
    },
  })
  const rename = useMutation({
    mutationFn: (t: string) => updateNode(nodeId, { title: t }),
    onSuccess: async (updated) => {
      qc.setQueryData(['node', nodeId], updated)
      await qc.invalidateQueries({ queryKey: ['tree', updated.spaceId] })
    },
  })

  const publish = useMutation({
    mutationFn: () => publishNode(nodeId),
    onSuccess: async (updated) => {
      qc.setQueryData(['node', nodeId], updated)
      await qc.invalidateQueries({ queryKey: ['tree', updated.spaceId] })
    },
  })

  useEffect(() => setTitle(node.data?.title ?? ''), [node.data?.title, nodeId])

  // Only a page that never loaded is replaced by an error. If a later refresh fails (trashed, archived, locked or
  // access removed meanwhile), the editor stays mounted so nothing typed is lost; the server's words go above it.
  if (!node.data) {
    if (node.isLoading) return <p>Loading…</p>
    const s = node.error instanceof ApiError ? node.error.status : 0
    return <p role="alert">{s === 404 ? 'This page does not exist.' : s === 403 ? "You don't have access to this page." : 'Could not load this page.'}</p>
  }
  const n = node.data
  const canEdit = n.access === 'EDIT'
  const spaceName = spaces.data?.find((s) => s.id === n.spaceId)?.name

  return (
    <>
      <header className="page-head">
        <Breadcrumbs spaceName={spaceName} crumbs={n.breadcrumbs} current={n.title} />
        <span ref={setHead} />
        {n.access !== 'TITLE_ONLY' && (
          <button type="button" className="banner-btn" aria-pressed={historyOpen} onClick={() => setHistoryOpen((o) => !o)}>History</button>
        )}
        <PageMenu node={n} unsaved={saveState !== 'saved' && saveState !== 'recovered'} />
      </header>
      <div ref={setBanners} />
      {node.isError && <p role="alert" className="banner">{node.error.message}</p>}
      <DraftBar publishState={n.publishState} canEdit={canEdit && n.status === 'ACTIVE'} busy={publish.isPending} onPublish={() => publish.mutate()} />
      {publish.error && <p role="alert">{publish.error.message}</p>}
      {n.status === 'ARCHIVED' && <p className="page-note" role="status">This page is archived and read-only.</p>}
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
        <TagEditor node={n} canEdit={canEdit} />
        {n.access === 'TITLE_ONLY' ? (
          <p className="page-note">You can see this page's title but not its content.</p>
        ) : me.data ? (
          <>
            {viewing && (
              <VersionViewer viewing={viewing} busy={restore.isPending} error={restore.error?.message ?? ''} onBack={() => setViewing(null)} onRestore={() => restore.mutate(viewing.version.id)} />
            )}
            {/* hidden, not unmounted: the editor keeps saving what was typed */}
            <div hidden={!!viewing}>
              <PageContent key={`${n.id}:${contentKey}`} node={n} userId={me.data.id} slots={{ head, banners }} onSaveState={setSaveState} />
            </div>
          </>
        ) : (
          <p className="page-note">Loading…</p>
        )}
      </article>
      {historyOpen && (
        <HistoryPanel
          nodeId={n.id}
          canEdit={canEdit && n.status === 'ACTIVE'}
          viewingId={viewing?.version.id ?? null}
          onView={setViewing}
          onClose={() => { setHistoryOpen(false); setViewing(null) }}
        />
      )}
    </>
  )
}

/** Loads the content (server copy + this user's unconfirmed local copies), then shows the editor. */
function PageContent({ node, userId, slots, onSaveState }: { node: NodeDetail; userId: string; slots: Slots; onSaveState: (s: SaveState) => void }) {
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
          onSaveState={onSaveState}
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

function PageEditor({ node, opened, slots, onRecovered, onSaveState }: { node: NodeDetail; opened: Opened; slots: Slots; onRecovered: () => void; onSaveState: (s: SaveState) => void }) {
  const canEdit = node.access === 'EDIT'
  const lock = useEditLock(node.id, canEdit)
  const editable = canEdit && lock.mode === 'editing'
  const editor = useKfEditor(opened.ydoc, 'Me', editable)
  useEndSessionOnLeave(node.id, canEdit)
  const save = useAutosave(node.id, opened, () => extractText(editor.document), canEdit)
  const problem = save.state === 'failed' ? `Not saved: ${save.message}` : save.state === 'retrying' || save.state === 'offline' ? save.message : undefined

  useEffect(() => {
    onSaveState(save.state)
    return () => onSaveState('saved') // nothing is unsaved once this editor is gone
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [save.state])

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
      {!canEdit && <p className="page-note">{node.status === 'ARCHIVED' ? 'This page is archived and read-only.' : node.locked ? 'This page is locked.' : 'You can read this page but not edit it.'}</p>}
      {lock.mode === 'readonly' && <p className="page-note">Opened read-only: {lock.holder} is editing this page.</p>}
      {lock.mode === 'denied' && <p className="page-note" role="alert">You can no longer edit this page{lock.reason ? `: ${lock.reason}` : '.'}</p>}
      <Editor editor={editor} editable={editable} />
    </>
  )
}
