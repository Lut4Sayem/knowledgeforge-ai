import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import { getMe, getMyTeams, type Me } from '../api/spaces'

const TYPES = ['image/png', 'image/jpeg', 'image/gif', 'image/webp']
const MAX = 2 * 1024 * 1024

export default function Profile() {
  const qc = useQueryClient()
  const me = useQuery({ queryKey: ['me'], queryFn: getMe })
  const teams = useQuery({ queryKey: ['my-teams'], queryFn: getMyTeams })
  const [name, setName] = useState('')
  const [error, setError] = useState('')
  const file = useRef<HTMLInputElement>(null)
  useEffect(() => setName(me.data?.fullName ?? ''), [me.data?.fullName])

  const done = (u: Me) => { qc.setQueryData(['me'], u); setError('') }
  const fail = (e: Error) => setError(e.message)
  const save = useMutation({
    mutationFn: () => api<Me>('/api/me', { method: 'PUT', body: JSON.stringify({ fullName: name.trim() }) }),
    onSuccess: done, onError: fail,
  })
  const upload = useMutation({
    mutationFn: (f: File) => { const b = new FormData(); b.append('file', f); return api<Me>('/api/me/picture', { method: 'POST', body: b }) },
    onSuccess: done, onError: fail,
  })
  const remove = useMutation({ mutationFn: () => api<Me>('/api/me/picture', { method: 'DELETE' }), onSuccess: done, onError: fail })

  function pick(f: File | undefined) {
    if (!f) return
    if (!TYPES.includes(f.type)) return setError('Use a PNG, JPG, GIF or WebP image.')
    if (f.size > MAX) return setError('The limit is 2 MB.')
    upload.mutate(f)
  }

  if (!me.data) return me.isLoading ? <p>Loading…</p> : <p role="alert">Could not load your profile.</p>
  const dirty = name.trim() !== me.data.fullName

  return (
    <section className="portal">
      <h1>Profile</h1>
      {error && <p role="alert">{error}</p>}
      {me.data.profilePicture && <img src={`/api/users/${me.data.id}/picture?v=${encodeURIComponent(me.data.profilePicture)}`} alt="Your profile picture" width={96} height={96} />}
      <p>
        <input ref={file} type="file" accept={TYPES.join(',')} aria-label="Profile picture" onChange={(e) => { pick(e.target.files?.[0]); e.target.value = '' }} />
        {me.data.profilePicture && <button type="button" onClick={() => remove.mutate()}>Remove picture</button>}
      </p>
      <form onSubmit={(e) => { e.preventDefault(); if (name.trim()) save.mutate() }}>
        <label>Name <input value={name} onChange={(e) => setName(e.target.value)} maxLength={100} /></label>
        {!name.trim() && <p className="page-note">Enter your name. Teammates see it on documents and comments.</p>}
        <button type="submit" disabled={!name.trim() || !dirty || save.isPending}>Save</button>
        {dirty && <span className="page-note"> Unsaved changes</span>}
      </form>
      <p className="page-note">{me.data.email}</p>

      <h2>My teams</h2>
      {teams.data?.length === 0 && <p className="page-note">You are not in a team yet.</p>}
      <ul>{teams.data?.map((t) => <li key={t.teamId}><Link to={`/teams/${t.teamId}`}>{t.teamName}</Link> · {t.role}</li>)}</ul>
    </section>
  )
}
