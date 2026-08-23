import { useCallback, useEffect, useState } from 'react'

const RISK_COLORS = {
  LOW: '#0f6e56',
  MEDIUM: '#854f0b',
  HIGH: '#993c1d',
  CRITICAL: '#791f1f',
}

export default function DecoysPage() {
  const [decoys, setDecoys] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const fetchDecoys = useCallback(() => {
    setLoading(true)
    fetch('/api/decoy/admin')
      .then((res) => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
        return res.json()
      })
      .then((data) => {
        setDecoys(data)
        setError(null)
      })
      .catch((err) => setError(err.message))
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    fetchDecoys()
  }, [fetchDecoys])

  async function toggleEnabled(decoy) {
    const action = decoy.enabled ? 'disable' : 'enable'
    const res = await fetch(`/api/decoy/admin/${decoy.id}/${action}`, { method: 'PATCH' })
    if (res.ok) fetchDecoys()
  }

  async function deleteDecoy(decoy) {
    if (!window.confirm(`Delete decoy "${decoy.name}"?`)) return
    const res = await fetch(`/api/decoy/admin/${decoy.id}`, { method: 'DELETE' })
    if (res.ok) fetchDecoys()
  }

  if (loading) return <p>Loading decoys...</p>
  if (error) return <p style={{ color: '#a32d2d' }}>Couldn't load decoys: {error}</p>

  return (
    <div>
      <h1>Decoys</h1>

      {decoys.length === 0 ? (
        <p>No decoys yet. (Create form lands in step 4.)</p>
      ) : (
        <table style={{ width: '100%', borderCollapse: 'collapse' }}>
          <thead>
            <tr style={{ textAlign: 'left', borderBottom: '2px solid #ddd' }}>
              <th style={{ padding: '0.5rem' }}>Name</th>
              <th style={{ padding: '0.5rem' }}>Endpoint path</th>
              <th style={{ padding: '0.5rem' }}>Risk</th>
              <th style={{ padding: '0.5rem' }}>Status</th>
              <th style={{ padding: '0.5rem' }}>Actions</th>
            </tr>
          </thead>
          <tbody>
            {decoys.map((decoy) => (
              <tr key={decoy.id} style={{ borderBottom: '1px solid #eee' }}>
                <td style={{ padding: '0.5rem' }}>{decoy.name}</td>
                <td style={{ padding: '0.5rem', fontFamily: 'monospace' }}>{decoy.endpointPath}</td>
                <td style={{ padding: '0.5rem', color: RISK_COLORS[decoy.riskLevel] ?? '#333' }}>
                  {decoy.riskLevel}
                </td>
                <td style={{ padding: '0.5rem' }}>{decoy.enabled ? 'Enabled' : 'Disabled'}</td>
                <td style={{ padding: '0.5rem' }}>
                  <button onClick={() => toggleEnabled(decoy)}>
                    {decoy.enabled ? 'Disable' : 'Enable'}
                  </button>{' '}
                  <button onClick={() => deleteDecoy(decoy)}>Delete</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}