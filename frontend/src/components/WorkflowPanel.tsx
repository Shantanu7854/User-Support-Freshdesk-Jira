import { useState } from 'react'

/**
 * A visual walkthrough of the actual pipeline, not a marketing diagram - every
 * box here corresponds to a real step in TriageService.handle(). Collapsed by
 * default so it doesn't compete with the chat; expands for anyone curious how
 * an answer was actually produced.
 */
export default function WorkflowPanel() {
  const [open, setOpen] = useState(false)

  return (
    <div className="workflow-panel">
      <button
        className="workflow-toggle"
        onClick={() => setOpen(o => !o)}
        aria-expanded={open}
      >
        <span className={`workflow-chevron ${open ? 'workflow-chevron-open' : ''}`}>&#9656;</span>
        How this actually works
      </button>

      {open && (
        <div className="workflow-body">
          <div className="workflow-step">
            <div className="workflow-step-num">1</div>
            <div className="workflow-step-content">
              <div className="workflow-step-title">Search past tickets</div>
              <div className="workflow-step-detail">
                Your message is matched against real previously-solved tickets in
                Postgres, using text search plus fuzzy matching - not an AI call,
                just a database query. This returns up to 8 candidates.
              </div>
            </div>
          </div>

          <div className="workflow-arrow">&#8595;</div>

          <div className="workflow-step">
            <div className="workflow-step-num">2</div>
            <div className="workflow-step-content">
              <div className="workflow-step-title">An LLM judges the candidates</div>
              <div className="workflow-step-detail">
                The candidates were retrieved by keyword overlap, which isn't the
                same as being right - "payment declined" and "charged twice" share
                every word and mean opposite things. One AI call reads the actual
                problem and the candidates, and decides which of four paths applies.
                A match below 70% confidence is rejected rather than trusted.
              </div>
            </div>
          </div>

          <div className="workflow-arrow">&#8595;</div>

          <div className="workflow-branches">
            <div className="workflow-branch workflow-branch-ANSWERED_FROM_KB">
              <span className="workflow-branch-dot" />
              <div className="workflow-branch-title">Genuine match</div>
              <div className="workflow-branch-detail">Answered from that ticket's real resolution</div>
            </div>
            <div className="workflow-branch workflow-branch-EXPLANATION">
              <span className="workflow-branch-dot" />
              <div className="workflow-branch-title">General question</div>
              <div className="workflow-branch-detail">Answered directly, or admits it doesn't know</div>
            </div>
            <div className="workflow-branch workflow-branch-NEEDS_DATA">
              <span className="workflow-branch-dot" />
              <div className="workflow-branch-title">Needs your data</div>
              <div className="workflow-branch-detail">Looks up your real account via a read-only MCP server</div>
            </div>
            <div className="workflow-branch workflow-branch-ESCALATE">
              <span className="workflow-branch-dot" />
              <div className="workflow-branch-title">Real bug</div>
              <div className="workflow-branch-detail">Opens a Freshdesk ticket and a linked Jira issue</div>
            </div>
          </div>

          <div className="workflow-arrow">&#8595;</div>

          <div className="workflow-step">
            <div className="workflow-step-num">3</div>
            <div className="workflow-step-content">
              <div className="workflow-step-title">The developer gets evidence, not a guess</div>
              <div className="workflow-step-detail">
                When something escalates, whatever data was already looked up -
                account status, failed orders, error messages - is attached to the
                Jira issue automatically. The developer opens it already knowing
                what's actually wrong, instead of starting from "user says it's broken."
              </div>
            </div>
          </div>

          <p className="workflow-note">
            Every step above is a real system running right now: the search is a
            live Postgres query, the AI calls go to Gemini or Groq depending on
            which is available, and the tickets created are real Freshdesk and
            Jira records.
          </p>
        </div>
      )}
    </div>
  )
}
