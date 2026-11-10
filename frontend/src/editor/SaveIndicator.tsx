import type { SaveState } from './autosave'

// Exact texts from the Editing wireframe / domain spec 5.3. A conflict is explained by its banner instead.
const TEXT: Record<SaveState, string> = {
  saved: 'Saved',
  saving: 'Saving…',
  offline: 'Offline: changes kept on this device',
  retrying: "Couldn't save, retrying",
  conflict: 'Not saved',
}

export default function SaveIndicator({ state }: { state: SaveState }) {
  return (
    <span role="status" className={`save-indicator save-indicator--${state}`}>
      <span aria-hidden="true" className="save-dot" />
      {TEXT[state]}
    </span>
  )
}

export function ConflictBanner({ onReload, onKeepMine }: { onReload: () => void; onKeepMine: () => void }) {
  return (
    <div role="alert" className="banner">
      <span>This page changed somewhere else.</span>
      <button type="button" className="banner-btn" onClick={onReload}>Reload latest</button>
      <button type="button" className="banner-btn banner-btn--quiet" onClick={onKeepMine}>Keep mine</button>
    </div>
  )
}
