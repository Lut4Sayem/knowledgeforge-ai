import { Route, Routes } from 'react-router-dom'
import Join from '../pages/Join'
import Login from '../pages/Login'
import Register from '../pages/Register'

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<main style={{ padding: 'var(--space-8)' }}>KnowledgeForge app</main>} />
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route path="/join/:code" element={<Join />} />
      <Route path="*" element={<main style={{ padding: 'var(--space-8)' }}>Not found</main>} />
    </Routes>
  )
}
