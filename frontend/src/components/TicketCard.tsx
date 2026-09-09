import type { TicketRef } from '../types'

export default function TicketCard({ ticket }: { ticket: TicketRef }) {
  return (
    <div className="ticket-card">
      <div className="ticket-card-header">
        Ticket created
        {ticket.simulated && <span className="sim-tag">simulated</span>}
      </div>
      <div className="ticket-row">
        <span className="ticket-label">Freshdesk</span>
        <span className="ticket-value">{ticket.freshdeskTicketId ?? '-'}</span>
      </div>
      <div className="ticket-row">
        <span className="ticket-label">Jira</span>
        <span className="ticket-value">
          {ticket.jiraUrl
            ? <a href={ticket.jiraUrl} target="_blank" rel="noreferrer">{ticket.jiraIssueKey}</a>
            : (ticket.jiraIssueKey ?? '-')}
        </span>
      </div>
      <div className="ticket-note">
        Diagnostic data was attached to this issue for the assigned developer.
      </div>
    </div>
  )
}
