import { useEffect, useId, useRef, useState } from 'react'

export interface ConfirmOptions {
  title: string
  body?: string
  confirmLabel: string
  cancelLabel?: string
  danger?: boolean
}

type Ask = (o: ConfirmOptions) => Promise<boolean>
let ask: Ask | null = null

/**
 * Asks the user and resolves true (confirmed) or false (cancelled, Esc). Needs <ConfirmHost /> to be mounted
 * (App does that). Without a host nobody can answer, so the answer is no: nothing destructive happens.
 */
export function confirmDialog(o: ConfirmOptions): Promise<boolean> {
  return ask ? ask(o) : Promise.resolve(false)
}

export function ConfirmHost() {
  const [asking, setAsking] = useState<{ o: ConfirmOptions; answer: (yes: boolean) => void } | null>(null)
  const busy = useRef(false)

  useEffect(() => {
    ask = (o) =>
      busy.current
        ? Promise.resolve(false) // one question at a time
        : new Promise<boolean>((resolve) => {
            busy.current = true
            setAsking({
              o,
              answer: (yes) => {
                busy.current = false
                setAsking(null)
                resolve(yes)
              },
            })
          })
    return () => {
      ask = null
    }
  }, [])

  return asking ? <Dialog {...asking.o} answer={asking.answer} /> : null
}

function Dialog({ title, body, confirmLabel, cancelLabel = 'Cancel', danger, answer }: ConfirmOptions & { answer: (yes: boolean) => void }) {
  const ref = useRef<HTMLDialogElement>(null)
  const cancel = useRef<HTMLButtonElement>(null)
  const id = useId()

  useEffect(() => {
    const dialog = ref.current!
    const trigger = document.activeElement as HTMLElement | null
    // Native modal: the page behind is inert and the top layer sits above everything. jsdom has no showModal.
    if (typeof dialog.showModal === 'function') dialog.showModal()
    else dialog.setAttribute('open', '')
    cancel.current?.focus() // start on the safe choice
    return () => {
      if (typeof dialog.close === 'function') dialog.close()
      if (trigger?.isConnected) trigger.focus()
    }
  }, [])

  function onKeyDown(e: React.KeyboardEvent<HTMLDialogElement>) {
    if (e.key === 'Escape') {
      e.preventDefault()
      answer(false)
    } else if (e.key === 'Tab') {
      // The browser's own Tab handling can leave a modal dialog, so the loop is closed by hand.
      const buttons = [...e.currentTarget.querySelectorAll<HTMLButtonElement>('button:not(:disabled)')]
      const first = buttons[0]
      const last = buttons[buttons.length - 1]
      const inside = e.currentTarget.contains(document.activeElement)
      if (!inside || (e.shiftKey && document.activeElement === first)) {
        e.preventDefault()
        ;(e.shiftKey ? last : first).focus()
      } else if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault()
        first.focus()
      }
    }
  }

  return (
    <dialog
      ref={ref}
      className="kf-dialog"
      aria-labelledby={`${id}-title`}
      aria-describedby={body ? `${id}-body` : undefined}
      onKeyDown={onKeyDown}
      onCancel={(e) => {
        e.preventDefault() // the browser's Esc: we close it ourselves so focus goes back first
        answer(false)
      }}
    >
      <h2 id={`${id}-title`} className="kf-dialog-title">{title}</h2>
      {body && <p id={`${id}-body`} className="kf-dialog-body">{body}</p>}
      <div className="kf-dialog-actions">
        <button type="button" ref={cancel} onClick={() => answer(false)}>{cancelLabel}</button>
        <button type="button" className={danger ? 'kf-danger' : 'kf-primary'} onClick={() => answer(true)}>{confirmLabel}</button>
      </div>
    </dialog>
  )
}
