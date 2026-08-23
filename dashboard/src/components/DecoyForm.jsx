import { useState } from 'react'

const RISK_LEVELS = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']

export default function DecoyForm({ onCreated }) {
  const [name, setName] = useState('')
  const [endpointPath, setEndpointPath] = useState('')
  const [riskLevel, setRiskLevel] = useState('LOW')
  const [orgId, setOrgId] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState(null)

  async function handleSubmit(e) {
    e.preventDefault()
    setSubmitting(true)
    setError(null)

    try {
      const res = await fetch('/api/decoy/admin', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          name,
          endpointPath,
          riskLevel,
          orgId: orgId || null,
        }),
      })

      if (!res.ok) {
        const body = await res.text()
        throw new Error(
          res.status === 409
            ? 'A decoy already exists at that path'
            : `HTTP ${res.status}: ${body}`
        )
      }

      setName('')
      setEndpointPath('')
      setRiskLevel('LOW')
      setOrgId('')
      onCreated()
    } catch (err) {
      setError(err.message)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form
      onSubmit={handleSubmit}
      style={{ marginBottom: '2rem', display: 'flex', gap: '0.5rem', flexWrap: 'wrap', alignItems: 'center' }}
    >
      <input type="text" placeholder="Name" value={name} onChange={(e) => setName(e.target.value)} required />
      <input
        type="text"
        placeholder="/api/admin/db-backup"
        value={endpointPath}
        onChange={(e) => setEndpointPath(e.target.value)}
        required
        style={{ fontFamily: 'monospace', minWidth: '220px' }}
      />
      <select value={riskLevel} onChange={(e) => setRiskLevel(e.target.value)}>
        {RISK_LEVELS.map((level) => (
          <option key={level} value={level}>{level}</option>
        ))}
      </select>
      <input type="text" placeholder="Org (optional)" value={orgId} onChange={(e) => setOrgId(e.target.value)} />
      <button type="submit" disabled={submitting}>
        {submitting ? 'Creating...' : 'Create decoy'}
      </button>
      {error && <span style={{ color: '#a32d2d' }}>{error}</span>}
    </form>
  )
}