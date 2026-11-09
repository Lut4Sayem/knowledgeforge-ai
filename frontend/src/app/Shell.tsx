import { Outlet } from 'react-router-dom'
import Sidebar from '../tree/Sidebar'

export default function Shell() {
  return (
    <div className="shell">
      <Sidebar />
      <main className="main">
        <Outlet />
      </main>
    </div>
  )
}
