import { useCallback, useEffect, useRef, useState } from 'react'
import { useAuth } from '../auth/AuthContext.jsx'

const LEVEL_STYLES = {
  INFORMATIONAL: { color: '#0f6e56', bg: '#e6f4ea', border: '#a8dab5' },
  SUSPICIOUS: { color: '#854f0b', bg: '#fef7e0', border: '#f1d69b' },
  HIGH: { color: '#993c1d', bg: '#fce8e6', border: '#f5b7b1' },
  CRITICAL: { color: '#791f1f', bg: '#fdeded', border: '#f5a3a3' },
}

export default function ThreatsPage() {
  const { authFetch } = useAuth()
  const [assessments, setAssessments] = useState([])
  const [selectedAssessment, setSelectedAssessment] = useState(null)
  const [sourceCorrelation, setSourceCorrelation] = useState(null)
  const [blockStatus, setBlockStatus] = useState(null)

  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [detailLoading, setDetailLoading] = useState(false)

  // Ref to prevent overlapping polling requests
  const isFetchingRef = useRef(false)

  const fetchRecentAssessments = useCallback(async (isInitial = false) => {
    if (isFetchingRef.current) return
    isFetchingRef.current = true
    if (isInitial) setLoading(true)

    try {
      const res = await authFetch('/api/threat/recent-assessments')
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      const data = await res.json()
      setAssessments(data)
      setError(null)
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
      isFetchingRef.current = false
    }
  }, [authFetch])

  // Auto-refresh every 5 seconds + cleanup timer on component unmount
  useEffect(() => {
    fetchRecentAssessments(true)

    const intervalId = setInterval(() => {
      fetchRecentAssessments(false)
    }, 5000)

    // Cleanup interval timer on component unmount
    return () => clearInterval(intervalId)
  }, [fetchRecentAssessments])

  // Fetch correlation and block status when an assessment is selected
  useEffect(() => {
    if (!selectedAssessment?.sourceIp) {
      setSourceCorrelation(null)
      setBlockStatus(null)
      return
    }

    let isMounted = true
    setDetailLoading(true)

    const ip = encodeURIComponent(selectedAssessment.sourceIp)

    Promise.allSettled([
      authFetch(`/api/threat/source/${ip}`).then((res) => (res.ok ? res.json() : null)),
      authFetch(`/api/threat/block/${ip}`).then((res) => (res.ok ? res.json() : null)),
    ]).then(([srcRes, blockRes]) => {
      if (!isMounted) return
      setDetailLoading(false)

      if (srcRes.status === 'fulfilled' && srcRes.value) {
        setSourceCorrelation(srcRes.value)
      } else {
        setSourceCorrelation(null)
      }

      if (blockRes.status === 'fulfilled' && blockRes.value) {
        setBlockStatus(blockRes.value)
      } else {
        setBlockStatus(null)
      }
    })

    return () => {
      isMounted = false
    }
  }, [selectedAssessment, authFetch])

  // Summary card metrics derived strictly from loaded data
  const totalCount = assessments.length
  const criticalCount = assessments.filter((a) => a.level === 'CRITICAL').length
  const blockedCount = assessments.filter((a) => a.blocked).length

  function formatTimestamp(ts) {
    if (!ts) return 'N/A'
    try {
      return new Date(ts).toLocaleString()
    } catch {
      return String(ts)
    }
  }

  function renderLevelBadge(level) {
    const style = LEVEL_STYLES[level] || { color: '#333', bg: '#eee', border: '#ccc' }
    return (
      <span
        style={{
          display: 'inline-block',
          padding: '0.2rem 0.5rem',
          borderRadius: '4px',
          fontSize: '0.85rem',
          fontWeight: 600,
          color: style.color,
          backgroundColor: style.bg,
          border: `1px solid ${style.border}`,
        }}
      >
        {level}
      </span>
    )
  }

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
        <h1 style={{ margin: 0 }}>Threat Monitoring</h1>
        <button
          onClick={() => fetchRecentAssessments(true)}
          style={{
            padding: '0.5rem 1rem',
            cursor: 'pointer',
            borderRadius: '4px',
            border: '1px solid #ccc',
            backgroundColor: '#f8f9fa',
          }}
        >
          Refresh
        </button>
      </div>

      {/* Top Summary Cards */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '1rem', marginBottom: '2rem' }}>
        <div style={{ padding: '1rem', border: '1px solid #e0e0e0', borderRadius: '6px', backgroundColor: '#fafafa' }}>
          <div style={{ fontSize: '0.85rem', color: '#666', marginBottom: '0.25rem' }}>Recent Assessments</div>
          <div style={{ fontSize: '1.5rem', fontWeight: 'bold' }}>{totalCount}</div>
        </div>
        <div style={{ padding: '1rem', border: '1px solid #e0e0e0', borderRadius: '6px', backgroundColor: '#fafafa' }}>
          <div style={{ fontSize: '0.85rem', color: '#666', marginBottom: '0.25rem' }}>Critical Assessments</div>
          <div style={{ fontSize: '1.5rem', fontWeight: 'bold', color: criticalCount > 0 ? '#993c1d' : '#333' }}>
            {criticalCount}
          </div>
        </div>
        <div style={{ padding: '1rem', border: '1px solid #e0e0e0', borderRadius: '6px', backgroundColor: '#fafafa' }}>
          <div style={{ fontSize: '0.85rem', color: '#666', marginBottom: '0.25rem' }}>Currently Blocked Sources</div>
          <div style={{ fontSize: '1.5rem', fontWeight: 'bold', color: blockedCount > 0 ? '#791f1f' : '#333' }}>
            {blockedCount}
          </div>
        </div>
      </div>

      {/* Main Content Area */}
      {loading ? (
        <p>Loading threat assessments...</p>
      ) : error ? (
        <div style={{ color: '#a32d2d', padding: '1rem', border: '1px solid #f5b7b1', borderRadius: '6px', backgroundColor: '#fdeded' }}>
          <p style={{ margin: 0, fontWeight: 'bold' }}>Unable to load threat assessments.</p>
          <p style={{ margin: '0.5rem 0 0 0', fontSize: '0.9rem' }}>Error details: {error}</p>
          <button
            onClick={() => fetchRecentAssessments(true)}
            style={{ marginTop: '0.75rem', padding: '0.4rem 0.8rem', cursor: 'pointer' }}
          >
            Retry
          </button>
        </div>
      ) : assessments.length === 0 ? (
        <div style={{ padding: '2rem', textAlign: 'center', border: '1px border-dashed #ccc', borderRadius: '6px', backgroundColor: '#fdfdfd' }}>
          <p style={{ margin: 0, color: '#666' }}>No threat assessments yet.</p>
          <p style={{ margin: '0.5rem 0 0 0', fontSize: '0.9rem', color: '#888' }}>
            Interact with a configured decoy to generate telemetry.
          </p>
        </div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: selectedAssessment ? '1fr 1fr' : '1fr', gap: '1.5rem' }}>
          {/* Assessments List Table */}
          <div>
            <h2 style={{ fontSize: '1.1rem', marginBottom: '0.75rem' }}>Recent Threat Feed (Max 50)</h2>
            <div style={{ overflowX: 'auto', border: '1px solid #e0e0e0', borderRadius: '6px' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.9rem' }}>
                <thead>
                  <tr style={{ textAlign: 'left', backgroundColor: '#f5f5f5', borderBottom: '1px solid #e0e0e0' }}>
                    <th style={{ padding: '0.6rem' }}>Source IP</th>
                    <th style={{ padding: '0.6rem' }}>Score</th>
                    <th style={{ padding: '0.6rem' }}>Threat Level</th>
                    <th style={{ padding: '0.6rem' }}>Hits</th>
                    <th style={{ padding: '0.6rem' }}>Decoys</th>
                    <th style={{ padding: '0.6rem' }}>Blocked</th>
                    <th style={{ padding: '0.6rem' }}>Assessed At</th>
                  </tr>
                </thead>
                <tbody>
                  {assessments.map((item) => {
                    const isSelected = selectedAssessment?.assessmentId === item.assessmentId
                    return (
                      <tr
                        key={item.assessmentId || `${item.sourceIp}-${item.assessedAt}`}
                        onClick={() => setSelectedAssessment(item)}
                        style={{
                          borderBottom: '1px solid #eee',
                          cursor: 'pointer',
                          backgroundColor: isSelected ? '#e8f0fe' : 'transparent',
                        }}
                      >
                        <td style={{ padding: '0.6rem', fontFamily: 'monospace', fontWeight: 600 }}>{item.sourceIp}</td>
                        <td style={{ padding: '0.6rem', fontWeight: 'bold' }}>{item.score}</td>
                        <td style={{ padding: '0.6rem' }}>{renderLevelBadge(item.level)}</td>
                        <td style={{ padding: '0.6rem' }}>{item.recentHitCount}</td>
                        <td style={{ padding: '0.6rem' }}>{item.distinctDecoyCount}</td>
                        <td style={{ padding: '0.6rem', color: item.blocked ? '#791f1f' : '#666', fontWeight: item.blocked ? 'bold' : 'normal' }}>
                          {item.blocked ? 'Yes' : 'No'}
                        </td>
                        <td style={{ padding: '0.6rem', fontSize: '0.8rem', color: '#666' }}>
                          {formatTimestamp(item.assessedAt)}
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          </div>

          {/* Selected Assessment Detailed View Panel */}
          {selectedAssessment && (
            <div style={{ border: '1px solid #e0e0e0', borderRadius: '6px', padding: '1.25rem', backgroundColor: '#fafafa' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
                <h2 style={{ fontSize: '1.15rem', margin: 0 }}>Assessment Details</h2>
                <button
                  onClick={() => setSelectedAssessment(null)}
                  style={{ border: 'none', background: 'none', cursor: 'pointer', fontSize: '1.1rem', color: '#666' }}
                >
                  ✕
                </button>
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.75rem', marginBottom: '1.25rem' }}>
                <div>
                  <span style={{ fontSize: '0.8rem', color: '#666', display: 'block' }}>Source IP</span>
                  <span style={{ fontFamily: 'monospace', fontWeight: 'bold' }}>{selectedAssessment.sourceIp}</span>
                </div>
                <div>
                  <span style={{ fontSize: '0.8rem', color: '#666', display: 'block' }}>Threat Level</span>
                  {renderLevelBadge(selectedAssessment.level)}
                </div>
                <div>
                  <span style={{ fontSize: '0.8rem', color: '#666', display: 'block' }}>Score</span>
                  <span style={{ fontSize: '1.2rem', fontWeight: 'bold' }}>{selectedAssessment.score} / 100</span>
                </div>
                <div>
                  <span style={{ fontSize: '0.8rem', color: '#666', display: 'block' }}>Blocked Status</span>
                  <span style={{ fontWeight: 'bold', color: selectedAssessment.blocked ? '#791f1f' : '#28a745' }}>
                    {selectedAssessment.blocked ? 'Yes' : 'No'}
                  </span>
                </div>
                <div>
                  <span style={{ fontSize: '0.8rem', color: '#666', display: 'block' }}>Triggering Decoy ID</span>
                  <span style={{ fontFamily: 'monospace' }}>{selectedAssessment.triggeringDecoyId || 'N/A'}</span>
                </div>
                <div>
                  <span style={{ fontSize: '0.8rem', color: '#666', display: 'block' }}>Triggering Endpoint</span>
                  <span style={{ fontFamily: 'monospace' }}>{selectedAssessment.triggeringEndpoint || 'N/A'}</span>
                </div>
                <div>
                  <span style={{ fontSize: '0.8rem', color: '#666', display: 'block' }}>Recent Hits (5m)</span>
                  <span>{selectedAssessment.recentHitCount}</span>
                </div>
                <div>
                  <span style={{ fontSize: '0.8rem', color: '#666', display: 'block' }}>Distinct Decoys</span>
                  <span>{selectedAssessment.distinctDecoyCount}</span>
                </div>
                <div style={{ gridColumn: 'span 2' }}>
                  <span style={{ fontSize: '0.8rem', color: '#666', display: 'block' }}>Assessed At</span>
                  <span>{formatTimestamp(selectedAssessment.assessedAt)}</span>
                </div>
              </div>

              {/* Backend-provided Explainable Reasons */}
              <div style={{ marginBottom: '1.25rem', paddingTop: '1rem', borderTop: '1px solid #e0e0e0' }}>
                <h3 style={{ fontSize: '0.95rem', margin: '0 0 0.5rem 0', color: '#333' }}>Explainable Reasons</h3>
                {selectedAssessment.reasons && selectedAssessment.reasons.length > 0 ? (
                  <ul style={{ margin: 0, paddingLeft: '1.2rem', fontSize: '0.88rem', color: '#444' }}>
                    {selectedAssessment.reasons.map((reason, idx) => (
                      <li key={idx} style={{ marginBottom: '0.3rem' }}>
                        {reason}
                      </li>
                    ))}
                  </ul>
                ) : (
                  <p style={{ margin: 0, fontSize: '0.85rem', color: '#888' }}>No specific reasons recorded.</p>
                )}
              </div>

              {/* Live Source Correlation Details (from /api/threat/source/{sourceIp}) */}
              <div style={{ marginBottom: '1.25rem', paddingTop: '1rem', borderTop: '1px solid #e0e0e0' }}>
                <h3 style={{ fontSize: '0.95rem', margin: '0 0 0.5rem 0', color: '#333' }}>Source Correlation State</h3>
                {detailLoading ? (
                  <p style={{ fontSize: '0.85rem', color: '#666' }}>Fetching live correlation details...</p>
                ) : sourceCorrelation ? (
                  <div style={{ fontSize: '0.85rem', display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.5rem' }}>
                    <div>Window Hits: <strong>{sourceCorrelation.recentHitCount}</strong></div>
                    <div>Distinct Decoys: <strong>{sourceCorrelation.distinctDecoyCount}</strong></div>
                    <div>Highest Risk Level: <strong>{sourceCorrelation.highestRiskLevel}</strong></div>
                    <div>Correlation Blocked: <strong>{sourceCorrelation.blocked ? 'Yes' : 'No'}</strong></div>
                  </div>
                ) : (
                  <p style={{ fontSize: '0.85rem', color: '#888' }}>No live correlation data available.</p>
                )}
              </div>

              {/* Block Status & TTL (from /api/threat/block/{sourceIp}) */}
              <div style={{ paddingTop: '1rem', borderTop: '1px solid #e0e0e0' }}>
                <h3 style={{ fontSize: '0.95rem', margin: '0 0 0.5rem 0', color: '#333' }}>Block Status</h3>
                {blockStatus ? (
                  <div style={{ fontSize: '0.88rem' }}>
                    <div>
                      Blocked: <strong>{blockStatus.blocked ? 'Yes' : 'No'}</strong>
                    </div>
                    {blockStatus.blocked && blockStatus.remainingTtlSeconds > 0 && (
                      <div style={{ marginTop: '0.3rem', color: '#791f1f', fontWeight: 'bold' }}>
                        Remaining block time: {blockStatus.remainingTtlSeconds} seconds
                      </div>
                    )}
                  </div>
                ) : (
                  <p style={{ fontSize: '0.85rem', color: '#888' }}>No block status available.</p>
                )}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  )
}