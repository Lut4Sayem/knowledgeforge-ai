import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { ApiError, api } from '../api/client'
import { AuthLayout, FormError } from './AuthLayout'
import { DEFAULT_NEXT } from './safeNext'

type State = 'loading' | 'ready' | 'sent' | 'error'

export default function Join() {
  const { code = '' } = useParams()
  const [state, setState] = useState<State>('loading')
  const [me, setMe] = useState('')
  const [message, setMessage] = useState('')
  const [formError, setFormError] = useState('')
  const [busy, setBusy] = useState(false)

  const fail = (m: string) => { setMessage(m); setState('error') }

  useEffect(() => {
    // A 401 here sends them to login and back via ?next=
    api<{ fullName?: string; email?: string }>('/api/me').then(
      (u) => { setMe(u?.fullName || u?.email || ''); setState('ready') },
      (err: Error) => fail(err.message),
    )
  }, [])

  async function join() {
    setFormError('')
    setBusy(true)
    try {
      await api(`/api/team-invites/${encodeURIComponent(code)}/join`, { method: 'POST' })
      setState('sent')
    } catch (err) {
      const e = err as ApiError
      if (e.status === 409 && /already a member/i.test(e.message)) {
        setMessage("You're already a member. Taking you to your dashboard…")
        setState('sent')
        setTimeout(() => window.location.assign(DEFAULT_NEXT), 1500)
      } else if (e.status === 409) {
        setMessage("You've already asked to join. It's waiting for approval.")
        setState('sent')
      } else if (e.status === 404) {
        fail("We couldn't find this invite. It may have been replaced; ask for a new link.")
      } else {
        setFormError(e.message)
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthLayout title="Join a team">
      {state === 'loading' && <p>Loading…</p>}
      {state === 'error' && <p role="alert">{message}</p>}
      {state === 'ready' && (
        <>
          <FormError message={formError} />
          <p>You're signed in as <strong>{me}</strong>. Invite code: <code>{code}</code></p>
          <button type="button" onClick={join} disabled={busy}>{busy ? 'Sending…' : 'Ask to join'}</button>
        </>
      )}
      {state === 'sent' && (
        <p role="status">{message || 'Request sent. A team admin has to approve it.'}</p>
      )}
    </AuthLayout>
  )
}
