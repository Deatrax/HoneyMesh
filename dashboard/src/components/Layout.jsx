import { useEffect, useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext.jsx'

export default function Layout() {
  const [gatewayStatus, setGatewayStatus] = useState('checking...')
  const { auth, isLoggedIn, logout } = useAuth()
  const navigate = useNavigate()

  useEffect(() => {
    fetch('/api/decoy/ping')
      .then((res) => res.text())
      .then(() => setGatewayStatus('online'))
      .catch(() => setGatewayStatus('unreachable'))
  }, [])

  const navLinkStyle = ({ isActive }) => ({
    marginRight: '1.5rem',
    textDecoration: 'none',
    fontWeight: isActive ? 700 : 400,
    color: isActive ? '#0c447c' : '#333',
  })

  function handleLogout() {
    logout()
    navigate('/login')
  }

  return (
    <div style={{ fontFamily: 'sans-serif' }}>
      <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '1rem 2rem', borderBottom: '1px solid #ddd' }}>
        <nav>
          <NavLink to="/decoys" style={navLinkStyle}>Decoys</NavLink>
          <NavLink to="/threats" style={navLinkStyle}>Threats</NavLink>
          <NavLink to="/incidents" style={navLinkStyle}>Incidents</NavLink>
        </nav>
        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
          <span style={{ fontSize: '0.85rem', color: '#666' }}>Gateway: {gatewayStatus}</span>
          {isLoggedIn ? (
            <span style={{ fontSize: '0.85rem', color: '#333' }}>
              {auth.username} ({auth.roles.join(', ')}){' '}
              <button onClick={handleLogout} style={{ marginLeft: '0.5rem', border: 'none', background: 'none', color: '#0c447c', cursor: 'pointer', textDecoration: 'underline', fontSize: '0.85rem' }}>
                Log out
              </button>
            </span>
          ) : (
            <NavLink to="/login" style={{ fontSize: '0.85rem', color: '#0c447c' }}>Analyst login</NavLink>
          )}
        </div>
      </header>
      <main style={{ padding: '2rem' }}>
        <Outlet />
      </main>
    </div>
  )
}
