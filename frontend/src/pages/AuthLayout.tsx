import { useState, type InputHTMLAttributes, type ReactNode } from 'react'

export function AuthLayout({ title, children }: { title: string; children: ReactNode }) {
  return (
    <>
      <main style={{ maxWidth: 400, margin: 'var(--space-12) auto', padding: '0 var(--space-4)' }}>
        <h1>{title}</h1>
        {children}
      </main>
      <footer className="site-footer" style={{ textAlign: 'center', color: 'var(--color-steel)', padding: 'var(--space-4)' }}>
        <span>&copy; {new Date().getFullYear()} KnowledgeForge</span>{' '}
        <a href="https://github.com/Lut4Sayem/knowledgeforge-ai" target="_blank" rel="noopener noreferrer">
          Source on GitHub
        </a>
      </footer>
    </>
  )
}

export function Field({ label, error, id, ...props }: { label: string; error?: string } & InputHTMLAttributes<HTMLInputElement> & { id: string }) {
  return (
    <div style={{ marginBottom: 'var(--space-4)' }}>
      <label htmlFor={id} style={{ display: 'block' }}>{label}</label>
      <input id={id} aria-invalid={!!error} aria-describedby={error ? id + '-error' : undefined} {...props} style={{ width: '100%' }} />
      {error && <div id={id + '-error'} role="alert" style={{ color: 'var(--color-error-text)' }}>{error}</div>}
    </div>
  )
}

export function PasswordField(props: { label: string; error?: string; id: string; value: string; onChange: (v: string) => void; autoComplete: string }) {
  const [shown, setShown] = useState(false)
  const { onChange, ...rest } = props
  return (
    <div>
      <Field {...rest} type={shown ? 'text' : 'password'} onChange={(e) => onChange(e.target.value)} />
      <button type="button" onClick={() => setShown(!shown)} aria-pressed={shown} style={{ marginTop: 'calc(-1 * var(--space-2))', marginBottom: 'var(--space-4)' }}>
        {shown ? 'Hide password' : 'Show password'}
      </button>
    </div>
  )
}

export function FormError({ message }: { message: string }) {
  return message ? <div role="alert" style={{ color: 'var(--color-error-text)', marginBottom: 'var(--space-4)' }}>{message}</div> : null
}
