import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { ApiError, api } from '../api/client'
import { AuthLayout, Field, FormError, PasswordField } from './AuthLayout'
import { DEFAULT_NEXT, safeNext } from './safeNext'

type Errors = Partial<Record<'fullName' | 'email' | 'password' | 'confirm', string>>

export default function Register() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const rawNext = params.get('next')
  const loginUrl = '/login' + (rawNext && safeNext(rawNext) !== DEFAULT_NEXT ? '?next=' + encodeURIComponent(safeNext(rawNext)) : '')
  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [errors, setErrors] = useState<Errors>({})
  const [formError, setFormError] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    api('/api/me', { redirectOn401: false }).then(() => window.location.assign(DEFAULT_NEXT), () => {})
  }, [])

  async function submit(e: FormEvent) {
    e.preventDefault()
    setFormError('')
    const errs: Errors = {}
    if (!fullName.trim()) errs.fullName = 'Enter your full name.'
    if (!email.trim()) errs.email = 'Enter your email.'
    if (!password) errs.password = 'Choose a password.'
    else if (password.length < 8) errs.password = 'Use at least 8 characters.'
    if (!confirm) errs.confirm = 'Type your password again.'
    else if (confirm !== password) errs.confirm = "Passwords don't match."
    setErrors(errs)
    if (Object.keys(errs).length) return

    setBusy(true)
    try {
      await api('/api/auth/register', {
        method: 'POST',
        body: JSON.stringify({ fullName: fullName.trim(), email: email.trim(), password }),
        redirectOn401: false,
      })
      navigate(loginUrl)
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) setErrors({ email: 'That email already has an account.' })
      else setFormError(err instanceof ApiError ? "Couldn't create your account: " + err.message : "Can't reach the server. Check your connection and try again.")
      setBusy(false)
    }
  }

  return (
    <AuthLayout title="Create your account">
      <form onSubmit={submit} noValidate>
        <FormError message={formError} />
        <Field id="fullName" label="Full name" autoComplete="name" value={fullName} error={errors.fullName} onChange={(e) => setFullName(e.target.value)} />
        <Field id="email" label="Email" type="email" autoComplete="email" value={email} error={errors.email} onChange={(e) => setEmail(e.target.value)} />
        <PasswordField id="password" label="Password" autoComplete="new-password" value={password} error={errors.password} onChange={setPassword} />
        <PasswordField id="confirmPassword" label="Confirm password" autoComplete="new-password" value={confirm} error={errors.confirm} onChange={setConfirm} />
        <button type="submit" disabled={busy}>{busy ? 'Creating account…' : 'Create account'}</button>
      </form>
      <p>
        Already have an account? <Link to={loginUrl}>Log in</Link>
      </p>
    </AuthLayout>
  )
}
