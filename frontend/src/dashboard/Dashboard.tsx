import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { createTeam, getDashboard } from '../api/teams'
import { timeAgo } from '../trash/TrashPage'

export default function Dashboard() {
  const dash = useQuery({ queryKey: ['dashboard'], queryFn: getDashboard })
  const qc = useQueryClient()
  const nav = useNavigate()
  const [creating, setCreating] = useState(false)
  const [name, setName] = useState('')
  const make = useMutation({
    mutationFn: () => createTeam(name.trim(), ''),
    onSuccess: async (t) => {
      // the sidebar's team picker and project list come from these
      await Promise.all(['dashboard', 'teams', 'spaces'].map((k) => qc.invalidateQueries({ queryKey: [k] })))
      nav(`/teams/${t.id}`)
    },
  })

  if (dash.isLoading) return <p>Loading…</p>
  if (!dash.data) return <p role="alert">Could not load your dashboard.</p>
  const d = dash.data

  return (
    <section className="portal">
      <h1>Dashboard</h1>
      {!creating ? (
        <button type="button" onClick={() => setCreating(true)}>New team</button>
      ) : (
        <form onSubmit={(e) => { e.preventDefault(); if (name.trim()) make.mutate() }}>
          <label>Team name <input value={name} onChange={(e) => setName(e.target.value)} maxLength={100} /></label>
          {!name.trim() && <p className="page-note">Give the team a name.</p>}
          <button type="submit" disabled={!name.trim() || make.isPending}>Create a team</button>
          <button type="button" onClick={() => setCreating(false)}>Cancel</button>
          {make.error && <p role="alert">{make.error.message}</p>}
        </form>
      )}

      <h2>My teams</h2>
      {d.teams.length === 0 && <p className="page-note">Start with a team</p>}
      <ul>
        {d.teams.map((t) => (
          <li key={t.teamId}>
            <Link to={`/teams/${t.teamId}`}>{t.name}</Link> · {t.role} · {t.spaceCount} spaces · {t.documentCount} pages
          </li>
        ))}
      </ul>

      <h2>Recently updated</h2>
      <ul>
        {d.recentDocuments.map((p) => (
          <li key={p.documentId}>
            <Link to={`/p/${p.documentId}`}>{p.title || 'Untitled'}</Link> · {p.teamName} / {p.spaceName} · {timeAgo(p.updatedAt, Date.now())}
          </li>
        ))}
      </ul>

      <h2>Activity</h2>
      {d.recentActivity.length === 0 && <p className="page-note">Nothing yet. Creating, editing and restoring documents shows up here.</p>}
      <ul>
        {d.recentActivity.map((a) => (
          <li key={a.id}>{a.actorFullName || a.actorEmail} {a.action.toLowerCase().replace(/_/g, ' ')} {a.targetTitle} · {a.teamName} · {timeAgo(a.createdAt, Date.now())}</li>
        ))}
      </ul>
    </section>
  )
}
