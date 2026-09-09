import { useEffect, useRef, useState } from 'react'
import { fetchPersonas, sendChat } from './api'
import type { ChatMessage, Persona } from './types'
import OutcomeBadge from './components/OutcomeBadge'
import DataPanel from './components/DataPanel'
import TicketCard from './components/TicketCard'
import ScopePanel from './components/ScopePanel'
import WorkflowPanel from './components/WorkflowPanel'

/** Staged progress text. A silent spinner for 10-20s reads as broken; naming
 *  each step makes the wait feel like work happening. */
const STAGES = [
  'Searching previously-solved tickets...',
  'Analyzing which one actually matches...',
  'Checking your account data...',
  'Preparing the response...'
]

export default function App() {
  const [personas, setPersonas] = useState<Persona[]>([])
  const [userEmail, setUserEmail] = useState('alice@demo.com')
  const [conversationId, setConversationId] = useState<string | null>(null)
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [stage, setStage] = useState(0)
  const endRef = useRef<HTMLDivElement>(null)

  useEffect(() => { fetchPersonas().then(setPersonas).catch(() => {}) }, [])

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, loading])

  useEffect(() => {
    if (!loading) return
    setStage(0)
    const timer = setInterval(() => setStage(s => Math.min(s + 1, STAGES.length - 1)), 2500)
    return () => clearInterval(timer)
  }, [loading])

  async function submit(text: string) {
    const message = text.trim()
    if (!message || loading) return

    setMessages(m => [...m, { role: 'user', text: message }])
    setInput('')
    setLoading(true)

    try {
      const response = await sendChat(message, userEmail, conversationId)
      setConversationId(response.conversationId)
      setMessages(m => [...m, { role: 'assistant', text: response.reply, response }])
    } catch (err) {
      setMessages(m => [...m, {
        role: 'assistant',
        text: 'Something went wrong reaching the server. Please try again.'
      }])
    } finally {
      setLoading(false)
    }
  }

  function switchPersona(email: string) {
    setUserEmail(email)
    setConversationId(null)   // a new persona starts a fresh conversation
    setMessages([])
  }

  return (
    <div className="app">
      <header className="header">
        <div>
          <h1>AI Support Triage</h1>
          <p className="subtitle">
            Answers from past tickets, diagnoses from live data, escalates real bugs
          </p>
        </div>
        <div className="persona-picker">
          <label htmlFor="persona">Signed in as</label>
          <select
            id="persona"
            value={userEmail}
            onChange={e => switchPersona(e.target.value)}
          >
            {personas.length === 0 && <option value="alice@demo.com">alice@demo.com</option>}
            {personas.map(p => (
              <option key={p.email} value={p.email}>
                {p.email} ({p.subscription_status})
              </option>
            ))}
          </select>
        </div>
      </header>

      <WorkflowPanel />

      <main className="chat">
        {messages.length === 0 && (
          <ScopePanel userEmail={userEmail} onPick={submit} />
        )}

        {messages.map((m, i) => (
          <div key={i} className={`message message-${m.role}`}>
            {m.role === 'assistant' && m.response && (
              <div className="message-meta">
                <OutcomeBadge outcome={m.response.outcome} />
                {m.response.llmProvider && m.response.llmProvider !== 'none' && (
                  <span className="provider">via {m.response.llmProvider}</span>
                )}
              </div>
            )}

            <div className="bubble">{m.text}</div>

            {/* The reasoning is the product. Hiding it makes this look like a
                plain chatbot rather than a system making a judgement. */}
            {m.response?.reasoning && (
              <div className="reasoning">
                <span className="reasoning-label">Why:</span> {m.response.reasoning}
                {m.response.confidence != null && m.response.confidence > 0 && (
                  <span className="confidence">
                    confidence {(m.response.confidence * 100).toFixed(0)}%
                  </span>
                )}
              </div>
            )}

            {m.response?.citation && (
              <div className="citation">
                Source: previously-solved ticket #{m.response.citation.id} - {m.response.citation.title}
              </div>
            )}

            {m.response?.diagnostics && <DataPanel results={m.response.diagnostics} />}
            {m.response?.ticket && <TicketCard ticket={m.response.ticket} />}
          </div>
        ))}

        {loading && (
          <div className="message message-assistant">
            <div className="bubble loading">{STAGES[stage]}</div>
          </div>
        )}
        <div ref={endRef} />
      </main>

      <form
        className="composer"
        onSubmit={e => { e.preventDefault(); submit(input) }}
      >
        <input
          value={input}
          onChange={e => setInput(e.target.value)}
          placeholder="Describe your problem..."
          disabled={loading}
        />
        <button type="submit" disabled={loading || !input.trim()}>Send</button>
      </form>
    </div>
  )
}
