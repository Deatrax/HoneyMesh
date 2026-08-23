import { useEffect, useState } from 'react'
import { NavLink, Outlet } from 'react-router-dom'

export default function Layout() {
  const [gatewayStatus, setGatewayStatus] = useState('checking...')

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

  return (
    <div style={{ fontFamily: 'sans-serif' }}>
      <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '1rem 2rem', borderBottom: '1px solid #ddd' }}>
        <nav>
          <NavLink to="/decoys" style={navLinkStyle}>Decoys</NavLink>
          <NavLink to="/threats" style={navLinkStyle}>Threats</NavLink>
          <NavLink to="/incidents" style={navLinkStyle}>Incidents</NavLink>
        </nav>
        <span style={{ fontSize: '0.85rem', color: '#666' }}>Gateway: {gatewayStatus}</span>
      </header>
      <main style={{ padding: '2rem' }}>
        <Outlet />
      </main>
    </div>
  )
}