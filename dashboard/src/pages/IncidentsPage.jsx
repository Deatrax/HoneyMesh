import { useCallback, useEffect, useRef, useState } from 'react'
import { useAuth } from '../auth/AuthContext.jsx'

const LEVEL_STYLES = {
  INFORMATIONAL: { color: '#0f6e56', bg: '#e6f4ea', border: '#a8dab5' },
  SUSPICIOUS: { color: '#854f0b', bg: '#fef7e0', border: '#f1d69b' },
  HIGH: { color: '#993c1d', bg: '#fce8e6', border: '#f5b7b1' },
  CRITICAL: { color: '#791f1f', bg: '#fdeded', border: '#f5a3a3' },
}

const STATUS_STYLES = {
  OPEN: { color: '#854f0b', bg: '#fef7e0', border: '#f1d69b' },
  INVESTIGATING: { color: '#0c447c', bg: '#e8f0fe', border: '#a9c6f5' },
  CONTAINED: { color: '#5b3e96', bg: '#f1eaff', border: '#cbb6f5' },
  RESOLVED: { color: '#0f6e56', bg: '#e6f4ea', border: '#a8dab5' },
}

// Mirrors ALLOWED_TRANSITIONS in IncidentService.java — keep these two in
// sync if the state machine ever changes.
const NEXT_STATUSES = {
  OPEN: ['INVESTIGATING'],
  INVESTIGATING: ['CONTAINED', 'RESOLVED'],
  CONTAINED: ['RESOLVED'],
  RESOLVED: [],
}

export default function IncidentsPage() {
  const { authFetch, isAdmin, auth } = useAuth()

  const [incidents, setIncidents] = useState([])
  const [selectedId, setSelectedId] = useState(null)
  const [activity, setActivity] = useState([])
  const [forensics, setForensics] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [actionError, setActionError] = useState(null)
  const [noteText, setNoteText] = useState('')
  const [wsStatus, setWsStatus] = useState('connecting')

  const isFetchingRef = useRef(false)
  const selectedIdRef = useRef(null)
  selectedIdRef.current = selectedId

  const fetchIncidents = useCallback(async (isInitial = false) => {
    if (isFetchingRef.current) return
    isFetchingRef.current = true
    if (isInitial) setLoading(true)
    try {
      const res = await authFetch('/api/incidents')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      const data = await res.json()
      setIncidents(data)
      setError(null)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
      isFetchingRef.current = false
    }
  }, [authFetch])

  const fetchActivity = useCallback(async (id) => {
    try {
      const res = await authFetch(`/api/incidents/${id}/activity`)
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      setActivity(await res.json())
    } catch {
      setActivity([])
    }
  }, [authFetch])

  // Forensics lives on decoy-service, not incident-service — a separate
  // fetch by sourceIp rather than a field on the incident itself. Missing
  // (404) is expected once 30 minutes pass since the last hit; treat that
  // as "nothing to show," not an error.
  const fetchForensics = useCallback(async (sourceIp) => {
    try {
      const res = await authFetch(`/api/decoy/admin/hit-detail/${sourceIp}`)
      if (!res.ok) {
        setForensics(null)
        return
      }
      setForensics(await res.json())
    } catch {
      setForensics(null)
    }
  }, [authFetch])

  // Initial load + 5s poll — same pattern ThreatsPage.jsx uses, so the
  // dashboard keeps working even if the WebSocket connection below drops.
  useEffect(() => {
    fetchIncidents(true)
    const intervalId = setInterval(() => fetchIncidents(false), 5000)
    return () => clearInterval(intervalId)
  }, [fetchIncidents])

  useEffect(() => {
    if (!selectedId) {
      setActivity([])
      setForensics(null)
      return
    }
    fetchActivity(selectedId)
    const incident = incidents.find((i) => i.id === selectedId)
    if (incident) fetchForensics(incident.sourceIp)
  }, [selectedId, fetchActivity, fetchForensics, incidents])

  // Live updates: IncidentBroadcaster.java pushes
  // {"type":"incident.updated", "incident": {...}} over /ws/alerts every
  // time IncidentService creates or changes an incident. We don't bother
  // merging that payload into state by hand — just re-fetch, which is
  // simple and always ends up exactly consistent with the database.
  useEffect(() => {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const socket = new WebSocket(`${protocol}//${window.location.host}/ws/alerts`)

    socket.onopen = () => setWsStatus('connected')
    socket.onclose = () => setWsStatus('disconnected')
    socket.onerror = () => setWsStatus('error')
    socket.onmessage = (event) => {
      try {
        const msg = JSON.parse(event.data)
        if (msg.type === 'incident.updated') {
          fetchIncidents(false)
          if (selectedIdRef.current === msg.incident?.id) {
            fetchActivity(selectedIdRef.current)
          }
        }
      } catch {
        // heartbeat or non-JSON message — ignore
      }
    }

    return () => socket.close()
  }, [fetchIncidents, fetchActivity])

  const selected = incidents.find((i) => i.id === selectedId) || null

  async function runAction(fn) {
    setActionError(null)
    try {
      await fn()
      await fetchIncidents(false)
      if (selectedId) await fetchActivity(selectedId)
    } catch (err) {
      setActionError(err.message)
    }
  }

  async function readError(res) {
    const body = await res.json().catch(() => null)
    // With spring.mvc.problemdetails.enabled=true, ResponseStatusException
    // messages arrive in body.detail — e.g. "Cannot move an incident from
    // OPEN to RESOLVED" or a 409 optimistic-lock conflict message.
    return body?.detail || body?.message || `HTTP ${res.status}`
  }

  async function changeStatus(incident, newStatus) {
    await runAction(async () => {
      const res = await authFetch(`/api/incidents/${incident.id}/status`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ status: newStatus, version: incident.version }),
      })
      if (!res.ok) throw new Error(await readError(res))
    })
  }

  async function assignToMe(incident) {
    await runAction(async () => {
      const res = await authFetch(`/api/incidents/${incident.id}/assign`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ analyst: auth.username, version: incident.version }),
      })
      if (!res.ok) throw new Error(await readError(res))
    })
  }

  async function unblock(incident) {
    await runAction(async () => {
      const res = await authFetch(`/api/incidents/${incident.id}/unblock`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ version: incident.version }),
      })
      if (!res.ok) throw new Error(await readError(res))
    })
  }

  async function permaBlock(incident) {
    await runAction(async () => {
      const res = await authFetch(`/api/incidents/${incident.id}/perma-block`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ version: incident.version }),
      })
      if (!res.ok) throw new Error(await readError(res))
    })
  }

  async function submitNote(incident) {
    if (!noteText.trim()) return
    await runAction(async () => {
      const res = await authFetch(`/api/incidents/${incident.id}/notes`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message: noteText.trim() }),
      })
      if (!res.ok) throw new Error(await readError(res))
      setNoteText('')
    })
  }

  function formatTimestamp(ts) {
    if (!ts) return 'N/A'
    try {
      return new Date(ts).toLocaleString()
    } catch {
      return String(ts)
    }
  }

  function renderBadge(value, styles) {
    const style = styles[value] || { color: '#333', bg: '#eee', border: '#ccc' }
    return (
      <span style={{ display: 'inline-block', padding: '0.2rem 0.5rem', borderRadius: '4px', fontSize: '0.85rem', fontWeight: 600, color: style.color, backgroundColor: style.bg, border: `1px solid ${style.border}` }}>
        {value}
      </span>
    )
  }

  const openCount = incidents.filter((i) => i.status !== 'RESOLVED').length
  const criticalCount = incidents.filter((i) => i.level === 'CRITICAL' && i.status !== 'RESOLVED').length

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
        <h1 style={{ margin: 0 }}>Incidents</h1>
        <span style={{ fontSize: '0.8rem', color: wsStatus === 'connected' ? '#0f6e56' : '#a32d2d' }}>
          Live updates: {wsStatus}
        </span>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '1rem', marginBottom: '2rem' }}>
        <div style={{ padding: '1rem', border: '1px solid #e0e0e0', borderRadius: '6px', backgroundColor: '#fafafa' }}>
          <div style={{ fontSize: '0.85rem', color: '#666', marginBottom: '0.25rem' }}>Open Incidents</div>
          <div style={{ fontSize: '1.5rem', fontWeight: 'bold' }}>{openCount}</div>
        </div>
        <div style={{ padding: '1rem', border: '1px solid #e0e0e0', borderRadius: '6px', backgroundColor: '#fafafa' }}>
          <div style={{ fontSize: '0.85rem', color: '#666', marginBottom: '0.25rem' }}>Critical (Unresolved)</div>
          <div style={{ fontSize: '1.5rem', fontWeight: 'bold', color: criticalCount > 0 ? '#993c1d' : '#333' }}>{criticalCount}</div>
        </div>
      </div>

      {loading ? (
        <p>Loading incidents...</p>
      ) : error ? (
        <div style={{ color: '#a32d2d', padding: '1rem', border: '1px solid #f5b7b1', borderRadius: '6px', backgroundColor: '#fdeded' }}>
          Unable to load incidents: {error}
        </div>
      ) : incidents.length === 0 ? (
        <div style={{ padding: '2rem', textAlign: 'center', border: '1px dashed #ccc', borderRadius: '6px' }}>
          <p style={{ margin: 0, color: '#666' }}>No incidents yet.</p>
          <p style={{ margin: '0.5rem 0 0 0', fontSize: '0.9rem', color: '#888' }}>
            Incidents are created automatically once a source IP's threat score reaches HIGH or CRITICAL.
          </p>
        </div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: selected ? '1fr 1fr' : '1fr', gap: '1.5rem' }}>
          <div>
            <div style={{ overflowX: 'auto', border: '1px solid #e0e0e0', borderRadius: '6px' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.9rem' }}>
                <thead>
                  <tr style={{ textAlign: 'left', backgroundColor: '#f5f5f5', borderBottom: '1px solid #e0e0e0' }}>
                    <th style={{ padding: '0.6rem' }}>Source IP</th>
                    <th style={{ padding: '0.6rem' }}>Level</th>
                    <th style={{ padding: '0.6rem' }}>Status</th>
                    <th style={{ padding: '0.6rem' }}>Assigned</th>
                    <th style={{ padding: '0.6rem' }}>Created</th>
                  </tr>
                </thead>
                <tbody>
                  {incidents.map((incident) => (
                    <tr
                      key={incident.id}
                      onClick={() => setSelectedId(incident.id)}
                      style={{ borderBottom: '1px solid #eee', cursor: 'pointer', backgroundColor: selectedId === incident.id ? '#e8f0fe' : 'transparent' }}
                    >
                      <td style={{ padding: '0.6rem', fontFamily: 'monospace', fontWeight: 600 }}>{incident.sourceIp}</td>
                      <td style={{ padding: '0.6rem' }}>{renderBadge(incident.level, LEVEL_STYLES)}</td>
                      <td style={{ padding: '0.6rem' }}>{renderBadge(incident.status, STATUS_STYLES)}</td>
                      <td style={{ padding: '0.6rem' }}>{incident.assignedAnalyst || '—'}</td>
                      <td style={{ padding: '0.6rem', fontSize: '0.8rem', color: '#666' }}>{formatTimestamp(incident.createdAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          {selected && (
            <div style={{ border: '1px solid #e0e0e0', borderRadius: '6px', padding: '1.25rem', backgroundColor: '#fafafa' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
                <h2 style={{ fontSize: '1.15rem', margin: 0 }}>Incident #{selected.id}</h2>
                <button onClick={() => setSelectedId(null)} style={{ border: 'none', background: 'none', cursor: 'pointer', fontSize: '1.1rem', color: '#666' }}>✕</button>
              </div>

              {actionError && (
                <div style={{ color: '#a32d2d', backgroundColor: '#fdeded', border: '1px solid #f5b7b1', borderRadius: '4px', padding: '0.6rem', marginBottom: '1rem', fontSize: '0.85rem' }}>
                  {actionError}
                </div>
              )}

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.75rem', marginBottom: '1.25rem' }}>
                <div><span style={{ fontSize: '0.8rem', color: '#666', display: 'block' }}>Source IP</span><span style={{ fontFamily: 'monospace', fontWeight: 'bold' }}>{selected.sourceIp}</span></div>
                <div><span style={{ fontSize: '0.8rem', color: '#666', display: 'block' }}>Level</span>{renderBadge(selected.level, LEVEL_STYLES)}</div>
                <div><span style={{ fontSize: '0.8rem', color: '#666', display: 'block' }}>Score</span><span style={{ fontSize: '1.2rem', fontWeight: 'bold' }}>{selected.score} / 100</span></div>
                <div><span style={{ fontSize: '0.8rem', color: '#666', display: 'block' }}>Status</span>{renderBadge(selected.status, STATUS_STYLES)}</div>
                <div><span style={{ fontSize: '0.8rem', color: '#666', display: 'block' }}>Assigned Analyst</span><span>{selected.assignedAnalyst || 'Unassigned'}</span></div>
                <div><span style={{ fontSize: '0.8rem', color: '#666', display: 'block' }}>Blocked</span>
                  <span style={{ fontWeight: 'bold', color: selected.blocked ? '#791f1f' : '#28a745' }}>
                    {!selected.blocked ? 'No' : selected.blockExpiresAt ? `Yes — until ${new Date(selected.blockExpiresAt).toLocaleTimeString()}` : 'Yes — permanent'}
                  </span>
                </div>
              </div>

              <div style={{ marginBottom: '1.25rem', paddingTop: '1rem', borderTop: '1px solid #e0e0e0' }}>
                <h3 style={{ fontSize: '0.95rem', margin: '0 0 0.5rem 0' }}>Reasons</h3>
                {selected.reasons?.length > 0 ? (
                  <ul style={{ margin: 0, paddingLeft: '1.2rem', fontSize: '0.88rem', color: '#444' }}>
                    {selected.reasons.map((reason, idx) => <li key={idx} style={{ marginBottom: '0.3rem' }}>{reason}</li>)}
                  </ul>
                ) : <p style={{ margin: 0, fontSize: '0.85rem', color: '#888' }}>No specific reasons recorded.</p>}
              </div>

              <div style={{ marginBottom: '1.25rem', paddingTop: '1rem', borderTop: '1px solid #e0e0e0' }}>
                <h3 style={{ fontSize: '0.95rem', margin: '0 0 0.5rem 0' }}>Request forensics</h3>
                {forensics ? (
                  <div style={{ fontSize: '0.85rem' }}>
                    <div style={{ marginBottom: '0.5rem' }}>
                      <span style={{ fontFamily: 'monospace', backgroundColor: '#e8f0fe', color: '#0c447c', padding: '0.1rem 0.4rem', borderRadius: '3px', marginRight: '0.5rem' }}>
                        {forensics.method}
                      </span>
                      <span style={{ fontFamily: 'monospace' }}>{forensics.uri}</span>
                    </div>
                    <div style={{ color: '#666', marginBottom: '0.4rem' }}>
                      Captured {new Date(forensics.capturedAt).toLocaleString()}
                    </div>
                    <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.82rem' }}>
                      <tbody>
                        {Object.entries(forensics.headers).map(([name, value]) => (
                          <tr key={name} style={{ borderTop: '1px solid #eee' }}>
                            <td style={{ padding: '0.3rem 0.5rem 0.3rem 0', color: '#666', fontFamily: 'monospace', whiteSpace: 'nowrap', verticalAlign: 'top' }}>{name}</td>
                            <td style={{ padding: '0.3rem 0', fontFamily: 'monospace', wordBreak: 'break-all' }}>{value}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                ) : (
                  <p style={{ margin: 0, fontSize: '0.85rem', color: '#888' }}>
                    No captured request on file for this source (older than 30 minutes, or never hit a live decoy path).
                  </p>
                )}
              </div>

              <div style={{ marginBottom: '1.25rem', paddingTop: '1rem', borderTop: '1px solid #e0e0e0' }}>
                <h3 style={{ fontSize: '0.95rem', margin: '0 0 0.5rem 0' }}>Actions</h3>
                <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
                  {NEXT_STATUSES[selected.status].map((next) => (
                    <button
                      key={next}
                      onClick={() => changeStatus(selected, next)}
                      style={{ padding: '0.4rem 0.8rem', borderRadius: '4px', border: '1px solid #0c447c', backgroundColor: '#fff', color: '#0c447c', cursor: 'pointer', fontSize: '0.85rem' }}
                    >
                      Move to {next}
                    </button>
                  ))}
                  {isAdmin && (
                    <button
                      onClick={() => assignToMe(selected)}
                      style={{ padding: '0.4rem 0.8rem', borderRadius: '4px', border: '1px solid #5b3e96', backgroundColor: '#fff', color: '#5b3e96', cursor: 'pointer', fontSize: '0.85rem' }}
                    >
                      Assign to me
                    </button>
                  )}
                  {isAdmin && !(selected.blocked && !selected.blockExpiresAt) && (
                    <button
                      onClick={() => permaBlock(selected)}
                      style={{ padding: '0.4rem 0.8rem', borderRadius: '4px', border: '1px solid #791f1f', backgroundColor: '#fff', color: '#791f1f', cursor: 'pointer', fontSize: '0.85rem' }}
                    >
                      Perma-ban {selected.sourceIp}
                    </button>
                  )}
                  {isAdmin && selected.blocked && (
                    <button
                      onClick={() => unblock(selected)}
                      style={{ padding: '0.4rem 0.8rem', borderRadius: '4px', border: '1px solid #993c1d', backgroundColor: '#fff', color: '#993c1d', cursor: 'pointer', fontSize: '0.85rem' }}
                    >
                      Unblock {selected.sourceIp}
                    </button>
                  )}
                </div>
              </div>

              <div style={{ paddingTop: '1rem', borderTop: '1px solid #e0e0e0' }}>
                <h3 style={{ fontSize: '0.95rem', margin: '0 0 0.5rem 0' }}>Activity &amp; Notes</h3>
                <div style={{ maxHeight: '220px', overflowY: 'auto', marginBottom: '0.75rem' }}>
                  {activity.length === 0 ? (
                    <p style={{ fontSize: '0.85rem', color: '#888' }}>No activity yet.</p>
                  ) : activity.map((entry) => (
                    <div key={entry.id} style={{ fontSize: '0.85rem', marginBottom: '0.5rem', paddingBottom: '0.5rem', borderBottom: '1px solid #eee' }}>
                      <div style={{ color: '#666', fontSize: '0.75rem' }}>{entry.author} · {formatTimestamp(entry.createdAt)}</div>
                      <div>{entry.message}</div>
                    </div>
                  ))}
                </div>
                <div style={{ display: 'flex', gap: '0.5rem' }}>
                  <input
                    type="text"
                    value={noteText}
                    onChange={(e) => setNoteText(e.target.value)}
                    placeholder="Add a note..."
                    style={{ flex: 1, padding: '0.4rem', border: '1px solid #ccc', borderRadius: '4px' }}
                    onKeyDown={(e) => { if (e.key === 'Enter') submitNote(selected) }}
                  />
                  <button
                    onClick={() => submitNote(selected)}
                    style={{ padding: '0.4rem 0.8rem', borderRadius: '4px', border: 'none', backgroundColor: '#0c447c', color: '#fff', cursor: 'pointer', fontSize: '0.85rem' }}
                  >
                    Add
                  </button>
                </div>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
