import { Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider } from './auth/AuthContext.jsx'
import RequireAuth from './auth/RequireAuth.jsx'
import Layout from './components/Layout.jsx'
import DecoysPage from './pages/DecoysPage.jsx'
import ThreatsPage from './pages/ThreatsPage.jsx'
import IncidentsPage from './pages/IncidentsPage.jsx'
import LoginPage from './pages/LoginPage.jsx'

function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route element={<Layout />}>
          <Route index element={<Navigate to="/decoys" replace />} />
          <Route path="/decoys" element={<DecoysPage />} />
          <Route path="/threats" element={<ThreatsPage />} />
          <Route
            path="/incidents"
            element={
              <RequireAuth>
                <IncidentsPage />
              </RequireAuth>
            }
          />
        </Route>
      </Routes>
    </AuthProvider>
  )
}

export default App
