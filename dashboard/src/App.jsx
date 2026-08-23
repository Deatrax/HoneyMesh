import { useEffect, useState } from 'react'

function App() {
  const [status, setStatus] = useState('checking...')

  useEffect(() => {
    fetch('/api/decoy/ping')
      .then((res) => res.text())
      .then(setStatus)
      .catch(() => setStatus('proxy not reaching the gateway — is docker compose up?'))
  }, [])

  return (
    <div style={{ fontFamily: 'sans-serif', padding: '2rem' }}>
      <h1>HoneyMesh dashboard</h1>
      <p>Gateway proxy check: <strong>{status}</strong></p>
    </div>
  )
}

export default App