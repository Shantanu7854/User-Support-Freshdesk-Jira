import type { DiagnosticResult } from '../types'

const TOOL_LABELS: Record<string, string> = {
  get_user_status: 'Account status',
  get_recent_orders: 'Recent orders',
  get_failed_jobs: 'Failed jobs'
}

/** Renders read-only MCP diagnostic output as tables. This is the visible
 *  proof that the answer came from live data rather than from the model. */
export default function DataPanel({ results }: { results: DiagnosticResult[] }) {
  const withRows = results.filter(r => r.rows && r.rows.length > 0)
  if (withRows.length === 0) return null

  return (
    <div className="data-panel">
      <div className="data-panel-header">Live account data (read-only)</div>
      {withRows.map(result => {
        const columns = Object.keys(result.rows[0])
        return (
          <div key={result.tool} className="data-table-wrap">
            <div className="data-tool-name">{TOOL_LABELS[result.tool] ?? result.tool}</div>
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>{columns.map(c => <th key={c}>{c.replace(/_/g, ' ')}</th>)}</tr>
                </thead>
                <tbody>
                  {result.rows.map((row, i) => (
                    <tr key={i}>
                      {columns.map(c => {
                        const v = row[c]
                        const s = v === null || v === undefined ? '-' : String(v)
                        const flag = c === 'status' && (s === 'failed' || s === 'past_due')
                        return <td key={c} className={flag ? 'cell-bad' : ''}>{s}</td>
                      })}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )
      })}
    </div>
  )
}
