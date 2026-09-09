import type { ChatResponse, Persona } from './types'

export async function sendChat(
  message: string,
  userEmail: string,
  conversationId: string | null
): Promise<ChatResponse> {
  const res = await fetch('/api/chat', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ message, userEmail, conversationId })
  })
  if (!res.ok) throw new Error(`Request failed: ${res.status}`)
  return res.json()
}

export async function fetchPersonas(): Promise<Persona[]> {
  const res = await fetch('/api/personas')
  if (!res.ok) return []
  return res.json()
}
