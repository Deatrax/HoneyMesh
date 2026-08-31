import { createContext, useContext, useState, useCallback, useMemo } from 'react'

const AuthContext = createContext(null)

const STORAGE_KEY = 'honeymesh.auth'

function loadStoredAuth() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

// Wraps login state (token/username/roles) so any page can ask "am I
// logged in, and as who" without re-fetching. Persists to localStorage
// so a page refresh doesn't log the analyst out mid-shift — this is a
// real app running in a real browser tab, not an embedded preview, so
// localStorage is the right tool here.
export function AuthProvider({ children }) {
  const [auth, setAuth] = useState(loadStoredAuth)

  const login = useCallback(async (username, password) => {
    const res = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password }),
    })
    if (!res.ok) {
      throw new Error(res.status === 401 ? 'Invalid username or password' : `Login failed (HTTP ${res.status})`)
    }
    const data = await res.json()
    setAuth(data)
    localStorage.setItem(STORAGE_KEY, JSON.stringify(data))
    return data
  }, [])

  const logout = useCallback(() => {
    setAuth(null)
    localStorage.removeItem(STORAGE_KEY)
  }, [])

  // Wraps fetch() and attaches the Bearer token automatically, so pages
  // never have to remember to add the Authorization header themselves.
  const authFetch = useCallback((url, options = {}) => {
    const headers = { ...(options.headers || {}) }
    if (auth?.token) {
      headers.Authorization = `Bearer ${auth.token}`
    }
    return fetch(url, { ...options, headers })
  }, [auth])

  const value = useMemo(() => ({
    auth,
    isLoggedIn: !!auth?.token,
    isAdmin: !!auth?.roles?.includes('ADMIN'),
    login,
    logout,
    authFetch,
  }), [auth, login, logout, authFetch])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside an <AuthProvider>')
  return ctx
}
