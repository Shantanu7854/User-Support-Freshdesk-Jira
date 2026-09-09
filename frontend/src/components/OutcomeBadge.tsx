import type { Outcome } from '../types'

const LABELS: Record<Outcome, string> = {
  ANSWERED_FROM_KB: 'Answered from past ticket',
  EXPLANATION: 'Explained',
  NEEDS_DATA: 'Diagnosed from live data',
  ESCALATE: 'Escalated to developer'
}

export default function OutcomeBadge({ outcome }: { outcome: Outcome }) {
  return <span className={`badge badge-${outcome}`}>{LABELS[outcome] ?? outcome}</span>
}
