import type { SaveState } from './autosave'

// The first four texts are exact (Editing wireframe / domain spec 5.3).
const TEXT: Record<SaveState, string> = {
  saved: 'Saved',
  saving: 'Saving…',
  offline: 'Offline: changes kept on this device',
  retrying: "Couldn't save, retrying",
  failed: 'Not saved',
  recovered: 'Saved',
}

/** `local` false = the unsaved changes could not be stored in the browser, so "kept on this device" would be a lie. */
export default function SaveIndicator({ state, local }: { state: SaveState; local: boolean }) {
  const text = state === 'offline' && !local ? 'Offline: changes are only in this open tab' : TEXT[state]
  return (
    <span role="status" className={`save-indicator save-indicator--${state}`}>
      <span aria-hidden="true" className="save-dot" />
      {text}
    </span>
  )
}

/** A message about saving that needs more room than the indicator. */
export function SaveNotice({ children, onDismiss }: { children: string; onDismiss?: () => void }) {
  return (
    <div role="alert" className="banner">
      <span>{children}</span>
      {onDismiss && <button type="button" className="banner-btn" onClick={onDismiss}>OK</button>}
    </div>
  )
}
