import DiffView from './DiffView'
import type { Viewing } from './HistoryPanel'

/** The selected version as a diff against the one before it, with "Back to current" and (editors) "Restore this version". */
export default function VersionViewer({ viewing, busy, error, onBack, onRestore }: {
  viewing: Viewing
  busy: boolean
  error: string
  onBack: () => void
  onRestore: () => void
}) {
  const when = new Date(viewing.version.createdAt).toLocaleString('en', { dateStyle: 'medium', timeStyle: 'short' })
  return (
    <section aria-label="Version comparison">
      <div className="kf-viewer-bar">
        <span><strong>Viewing:</strong> {when} · compared with the version before</span>
        <button type="button" className="banner-btn" onClick={onBack}>Back to current</button>
        {viewing.canRestore && <button type="button" className="kf-primary" disabled={busy} onClick={onRestore}>Restore this version</button>}
      </div>
      {error && <p role="alert">{error}</p>}
      <DiffView text={viewing.text} previousText={viewing.previousText} authors={viewing.version.authors} />
    </section>
  )
}
