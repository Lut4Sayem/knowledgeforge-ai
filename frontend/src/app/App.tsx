import { lazy, Suspense } from 'react'
import { Route, Routes } from 'react-router-dom'
import Join from '../pages/Join'
import Login from '../pages/Login'
import Logout from '../pages/Logout'
import Register from '../pages/Register'
import { ConfirmHost } from '../ui/ConfirmDialog'
import Shell from './Shell'

// The block editor is most of the bundle: keep it out of the login and register pages.
const PageView = lazy(() => import('../page/PageView'))
const TrashPage = lazy(() => import('../trash/TrashPage'))
const TagPage = lazy(() => import('../page/TagPage'))
const Search = lazy(() => import('../pages/Search'))

const Plain = ({ children }: { children: string }) => <p style={{ padding: 'var(--space-8)' }}>{children}</p>

export default function App() {
  return (
    <>
      <ConfirmHost />
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/logout" element={<Logout />} />
        <Route path="/register" element={<Register />} />
        <Route path="/join/:code" element={<Join />} />
        <Route element={<Shell />}>
          <Route path="/" element={<Plain>Dashboard arrives with the ported pages.</Plain>} />
          <Route path="/p/:nodeId" element={<Suspense fallback={<p>Loading…</p>}><PageView /></Suspense>} />
          <Route path="/s/:spaceId/trash" element={<Suspense fallback={<p>Loading…</p>}><TrashPage /></Suspense>} />
          <Route path="/tags/:tag" element={<Suspense fallback={<p>Loading…</p>}><TagPage /></Suspense>} />
          <Route path="/search" element={<Suspense fallback={<p>Loading…</p>}><Search /></Suspense>} />
          <Route path="*" element={<Plain>Not found</Plain>} />
        </Route>
      </Routes>
    </>
  )
}
