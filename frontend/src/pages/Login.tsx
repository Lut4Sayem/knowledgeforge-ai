import { useEffect, useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { ApiError, api } from '../api/client'
import { AuthLayout, Field, FormError, PasswordField } from './AuthLayout'
import { safeNext } from './safeNext'

export default function Login() {
  const [params] = useSearchParams()
  const next = safeNext(params.get('next'))
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [errors, setErrors] = useState<{ email?: string; password?: string }>({})
  const [formError, setFormError] = useState('')
  const [busy, setBusy] = useState(false)

  // Already logged in (bookmarked login page): skip the form.
  useEffect(() => {
    api('/api/me', { redirectOn401: false }).then(() => window.location.assign(next), () => {})
  }, [next])

  async function submit(e: FormEvent) {
    e.preventDefault()
    setFormError('')
    const errs: typeof errors = {}
    if (!email.trim()) errs.email = 'Enter your email.'
    if (!password) errs.password = 'Enter your password.'
    setErrors(errs)
    if (errs.email || errs.password) return

    setBusy(true)
    try {
      await api('/api/auth/login', { method: 'POST', body: JSON.stringify({ email: email.trim(), password }), redirectOn401: false })
      window.location.assign(next)
    } catch (err) {
      if (err instanceof ApiError && (err.status === 401 || err.status === 403)) {
        setFormError("That email and password don't match. Check them and try again.")
      } else {
        setFormError(err instanceof ApiError ? "Couldn't log you in: " + err.message : "Can't reach the server. Check your connection and try again.")
      }
      setBusy(false)
    }
  }

  return (
    <AuthLayout title="Log in">
      <form onSubmit={submit} noValidate>
        <FormError message={formError} />
        <Field id="email" label="Email" type="email" autoComplete="email" value={email} error={errors.email} onChange={(e) => setEmail(e.target.value)} />
        <PasswordField id="password" label="Password" autoComplete="current-password" value={password} error={errors.password} onChange={setPassword} />
        <button type="submit" disabled={busy}>{busy ? 'Logging in…' : 'Log in'}</button>
      </form>
      <p>
        New here? <Link to={'/register' + (params.get('next') ? '?next=' + encodeURIComponent(params.get('next')!) : '')}>Create an account</Link>
      </p>
    </AuthLayout>
  )
}
