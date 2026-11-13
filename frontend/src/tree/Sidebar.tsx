import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useMatch } from 'react-router-dom'
import { getMe, getMySpaces, getMyTeams, type MySpace } from '../api/spaces'
import PageTree from './PageTree'

const TEAM_KEY = 'kf.team'

function savedTeam(): string | null {
  try {
    return localStorage.getItem(TEAM_KEY)
  } catch {
    return null
  }
}

// Loaded on demand: sending unsaved copies needs the editor code, which the rest of the shell does not.
const logOut = (userId: string) =>
  import('../editor/logout').then(
    (m) => m.logOut(userId),
    () => window.alert('Could not log out. Check your connection and try again.'), // offline: the code could not load
  )

export default function Sidebar({ onSearch }: { onSearch: () => void }) {
  const me = useQuery({ queryKey: ['me'], queryFn: getMe })
  const teams = useQuery({ queryKey: ['teams'], queryFn: getMyTeams })
  const spaces = useQuery({ queryKey: ['spaces'], queryFn: getMySpaces })
  const activeId = useMatch('/p/:nodeId')?.params.nodeId
  const [picked, setPicked] = useState(savedTeam)

  const teamList = teams.data ?? []
  const teamId = teamList.some((t) => t.teamId === picked) ? picked : teamList[0]?.teamId
  const projects = (spaces.data ?? []).filter((s) => s.ownerType === 'TEAM' && s.teamId === teamId)
  const priv = (spaces.data ?? []).find((s) => s.ownerType === 'USER')

  function pickTeam(id: string) {
    setPicked(id)
    try {
      localStorage.setItem(TEAM_KEY, id)
    } catch {
      // ignore: the choice just is not remembered
    }
  }

  return (
    <nav className="sidebar" aria-label="Workspace">
      {teamList.length > 0 && (
        <select className="sidebar-team" aria-label="Team" value={teamId ?? ''} onChange={(e) => pickTeam(e.target.value)}>
          {teamList.map((t) => <option key={t.teamId} value={t.teamId}>{t.teamName || 'Untitled team'}</option>)}
        </select>
      )}
      <button type="button" className="sidebar-search" onClick={onSearch}>Search <kbd>Ctrl K</kbd></button>
      <Link to="/" className="sidebar-link">Home</Link>

      <h2 className="sidebar-heading">Projects</h2>
      {projects.map((s) => <SpaceSection key={s.id} space={s} activeId={activeId} />)}
      {spaces.isSuccess && teamList.length > 0 && projects.length === 0 &&<p className="tree-note">No projects in this team yet.</p>}

      {priv && (
        <>
          <h2 className="sidebar-heading">Private</h2>
          <SpaceSection space={priv} activeId={activeId} />
        </>
      )}

      <div className="sidebar-foot">
        {me.data?.fullName}{' '}
        {me.data && <button type="button" className="tree-add" onClick={() => void logOut(me.data.id)}>Log out</button>}
      </div>
    </nav>
  )
}

function SpaceSection({ space, activeId }: { space: MySpace; activeId?: string }) {
  return (
    <details className="space" open>
      <summary>{space.icon ? `${space.icon} ` : ''}{space.name}</summary>
      <PageTree spaceId={space.id} spaceName={space.name} activeId={activeId} />
      <div className="space-links">
        <Link to={`/s/${space.id}/trash?tab=archived`}>Archived</Link>
        <Link to={`/s/${space.id}/trash`}>Trash</Link>
      </div>
    </details>
  )
}
