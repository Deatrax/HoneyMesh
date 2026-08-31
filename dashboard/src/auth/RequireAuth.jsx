import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from './AuthContext.jsx'

// Wrap any <Route element={...}> that needs a logged-in analyst. If
// there's no token, bounce to /login and remember where we were headed
// so LoginPage can send the analyst back after a successful sign-in.
export default function RequireAuth({ children }) {
  const { isLoggedIn } = useAuth()
  const location = useLocation()

  if (!isLoggedIn) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }
  return children
}
