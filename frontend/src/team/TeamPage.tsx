import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { getMe } from '../api/spaces'
import {
  answerJoinRequest, createInvite, createTeamSpace, getJoinRequests, getTeam, getTeamSpaces, removeMember, setRole, type Member,
} from '../api/teams'
import { confirmDialog } from '../ui/ConfirmDialog'

const ROLES = ['ADMIN', 'EDITOR', 'VIEWER']

export default function TeamPage() {
  const { teamId = '' } = useParams()
  const qc = useQueryClient()
  const team = useQuery({ queryKey: ['team', teamId], queryFn: () => getTeam(teamId), retry: false })
  const spaces = useQuery({ queryKey: ['team-spaces', teamId], queryFn: () => getTeamSpaces(teamId) })
  const me = useQuery({ queryKey: ['me'], queryFn: getMe })
  const mine = team.data?.teamMembers.find((m) => m.userId === me.data?.id)
  const admin = mine?.role === 'ADMIN'
  const requests = useQuery({ queryKey: ['join-requests', teamId], queryFn: () => getJoinRequests(teamId), enabled: admin })
  const [link, setLink] = useState('')
  const [spaceName, setSpaceName] = useState('')
  const [error, setError] = useState('')
  const refresh = () => Promise.all(['team', 'dashboard', 'teams'].map((k) => qc.invalidateQueries({ queryKey: [k] })))
  const run = (fn: () => Promise<unknown>) => fn().then(() => setError(''), (e: Error) => setError(e.message))

  const addSpace = useMutation({
    mutationFn: () => createTeamSpace(teamId, spaceName.trim(), ''),
    onSuccess: async () => {
      setSpaceName('')
      await qc.invalidateQueries({ queryKey: ['team-spaces', teamId] })
      await qc.invalidateQueries({ queryKey: ['spaces'] }) // the sidebar's project list
    },
  })

  async function changeRole(m: Member, role: string) {
    if (role === 'ADMIN' && !(await confirmDialog({ title: `Make ${m.fullName} an admin?`, confirmLabel: 'Make admin' }))) return
    await run(async () => { await setRole(teamId, m.userId, role); await refresh() })
  }
  async function remove(m: Member) {
    if (!(await confirmDialog({ title: `Remove ${m.fullName}?`, confirmLabel: 'Remove member', danger: true }))) return
    await run(async () => { await removeMember(teamId, m.userId); await refresh() })
  }

  if (team.isLoading) return <p>Loading…</p>
  if (!team.data) return <p role="alert">Couldn't load this team: {team.error?.message}</p>
  const { team: t, teamMembers } = team.data

  return (
    <section className="portal">
      <h1>{t.name}</h1>
      {t.description && <p>{t.description}</p>}
      {error && <p role="alert">{error}</p>}

      <h2>Spaces</h2>
      {/* old space page until the cutover (9.4) maps spaces into /app */}
      <ul>{spaces.data?.map((s) => <li key={s.id}><a href={`/space.html?id=${s.id}`}>{s.name}</a></li>)}</ul>
      {mine && mine.role !== 'VIEWER' && (
        <form onSubmit={(e) => { e.preventDefault(); if (spaceName.trim()) addSpace.mutate() }}>
          <label>Space name <input value={spaceName} onChange={(e) => setSpaceName(e.target.value)} maxLength={100} /></label>
          <button type="submit" disabled={!spaceName.trim() || addSpace.isPending}>New space</button>
          {addSpace.error && <p role="alert">{addSpace.error.message}</p>}
        </form>
      )}

      <h2>Members</h2>
      <ul>
        {teamMembers.map((m) => (
          <li key={m.userId}>
            {m.fullName} ({m.email}) ·{' '}
            {admin && m.userId !== me.data?.id ? (
              <>
                <select aria-label={`Role of ${m.fullName}`} value={m.role} onChange={(e) => changeRole(m, e.target.value)}>
                  {ROLES.map((r) => <option key={r}>{r}</option>)}
                </select>{' '}
                <button type="button" onClick={() => remove(m)}>Remove</button>
              </>
            ) : m.role}
          </li>
        ))}
      </ul>

      {admin && (
        <>
          <h2>Join requests</h2>
          {requests.data?.length === 0 && <p className="page-note">No pending requests</p>}
          <ul>
            {requests.data?.map((r) => (
              <li key={r.id}>
                {r.fullName} ({r.email}){' '}
                {([true, false] as const).map((ok) => (
                  <button key={String(ok)} type="button" onClick={() => run(async () => { await answerJoinRequest(teamId, r.id, ok); await Promise.all([refresh(), requests.refetch()]) })}>
                    {ok ? 'Accept' : 'Reject'}
                  </button>
                ))}
              </li>
            ))}
          </ul>

          <h2>Invite link</h2>
          <button type="button" onClick={() => run(async () => setLink(`${location.origin}/app/join/${(await createInvite(teamId)).inviteCode}`))}>
            {link ? 'New invite link' : 'Create invite link'}
          </button>
          {link && <p><input readOnly aria-label="Invite link" value={link} onFocus={(e) => e.currentTarget.select()} /></p>}
        </>
      )}
    </section>
  )
}
