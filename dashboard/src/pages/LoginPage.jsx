import { useState } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext.jsx'

export default function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState(null)
  const [submitting, setSubmitting] = useState(false)

  const redirectTo = location.state?.from || '/incidents'

  async function handleSubmit(e) {
    e.preventDefault()
    setSubmitting(true)
    setError(null)
    try {
      await login(username, password)
      navigate(redirectTo, { replace: true })
    } catch (err) {
      setError(err.message)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div style={{ maxWidth: '360px', margin: '4rem auto', fontFamily: 'sans-serif' }}>
      <h1 style={{ fontSize: '1.4rem', marginBottom: '1.5rem' }}>Analyst Login</h1>
      <form onSubmit={handleSubmit}>
        <div style={{ marginBottom: '1rem' }}>
          <label style={{ display: 'block', fontSize: '0.85rem', color: '#666', marginBottom: '0.3rem' }}>
            Username
          </label>
          <input
            type="text"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            required
            autoFocus
            style={{ width: '100%', padding: '0.5rem', border: '1px solid #ccc', borderRadius: '4px', boxSizing: 'border-box' }}
          />
        </div>
        <div style={{ marginBottom: '1.25rem' }}>
          <label style={{ display: 'block', fontSize: '0.85rem', color: '#666', marginBottom: '0.3rem' }}>
            Password
          </label>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            style={{ width: '100%', padding: '0.5rem', border: '1px solid #ccc', borderRadius: '4px', boxSizing: 'border-box' }}
          />
        </div>

        {error && (
          <div style={{ color: '#a32d2d', backgroundColor: '#fdeded', border: '1px solid #f5b7b1', borderRadius: '4px', padding: '0.6rem', marginBottom: '1rem', fontSize: '0.85rem' }}>
            {error}
          </div>
        )}

        <button
          type="submit"
          disabled={submitting}
          style={{ width: '100%', padding: '0.6rem', borderRadius: '4px', border: 'none', backgroundColor: '#0c447c', color: '#fff', cursor: submitting ? 'default' : 'pointer', opacity: submitting ? 0.7 : 1 }}
        >
          {submitting ? 'Signing in...' : 'Sign in'}
        </button>
      </form>

      <p style={{ marginTop: '1.5rem', fontSize: '0.8rem', color: '#888' }}>
        Demo accounts — admin / admin123 (full access) or analyst / analyst123 (view, notes, status).
      </p>
    </div>
  )
}
