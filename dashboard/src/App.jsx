import { Routes, Route, Navigate } from 'react-router-dom'
import Layout from './components/Layout.jsx'
import DecoysPage from './pages/DecoysPage.jsx'
import ThreatsPage from './pages/ThreatsPage.jsx'
import IncidentsPage from './pages/IncidentsPage.jsx'

function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<Navigate to="/decoys" replace />} />
        <Route path="/decoys" element={<DecoysPage />} />
        <Route path="/threats" element={<ThreatsPage />} />
        <Route path="/incidents" element={<IncidentsPage />} />
      </Route>
    </Routes>
  )
}

export default App