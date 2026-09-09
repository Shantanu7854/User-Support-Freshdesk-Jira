export type Outcome = 'ANSWERED_FROM_KB' | 'EXPLANATION' | 'NEEDS_DATA' | 'ESCALATE'

export interface DiagnosticResult {
  tool: string
  rows: Record<string, unknown>[]
  error: string | null
}

export interface TicketRef {
  freshdeskTicketId: string | null
  jiraIssueKey: string | null
  jiraUrl: string | null
  simulated: boolean
}

export interface ChatResponse {
  conversationId: string
  reply: string
  outcome: Outcome
  reasoning: string | null
  confidence: number | null
  citation: { id: number; title: string } | null
  diagnostics: DiagnosticResult[] | null
  ticket: TicketRef | null
  llmProvider: string
}

export interface Persona {
  email: string
  full_name: string
  plan: string
  subscription_status: string
}

export interface ChatMessage {
  role: 'user' | 'assistant'
  text: string
  response?: ChatResponse
}
