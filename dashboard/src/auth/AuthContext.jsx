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
