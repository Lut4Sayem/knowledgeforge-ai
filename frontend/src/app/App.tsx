import { lazy, Suspense } from 'react'
import { Route, Routes } from 'react-router-dom'
import Join from '../pages/Join'
import Login from '../pages/Login'
import Register from '../pages/Register'
import Shell from './Shell'

// The block editor is most of the bundle: keep it out of the login and register pages.
const PageView = lazy(() => import('../page/PageView'))

const Plain = ({ children }: { children: string }) => <p style={{ padding: 'var(--space-8)' }}>{children}</p>

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route path="/join/:code" element={<Join />} />
      <Route element={<Shell />}>
        <Route path="/" element={<Plain>Dashboard arrives with the ported pages.</Plain>} />
        <Route path="/p/:nodeId" element={<Suspense fallback={<p>Loading…</p>}><PageView /></Suspense>} />
        <Route path="*" element={<Plain>Not found</Plain>} />
      </Route>
    </Routes>
  )
}
