import type { Outcome } from '../types'

interface Category {
  outcome: Outcome
  label: string
  description: string
  /** Example question keyed by persona - the right example depends on whose
   *  data is behind it. bob has a failed payment, carol has a broken sync;
   *  asking bob's question as carol wouldn't demonstrate the same thing. */
  examples: Record<string, string>
}

const CATEGORIES: Category[] = [
  {
    outcome: 'ANSWERED_FROM_KB',
    label: 'Something already solved before',
    description: 'Matched against real past support tickets and answered from the actual resolution.',
    examples: {
      'alice@demo.com': 'How do I export my data to CSV?',
      'bob@demo.com': 'How do I export my data to CSV?',
      'carol@demo.com': 'How do I export my data to CSV?'
    }
  },
  {
    outcome: 'EXPLANATION',
    label: 'A general question',
    description: "No past ticket applies, but it's answerable directly - or it says so rather than guessing.",
    examples: {
      'alice@demo.com': 'What happens if I miss a monthly payment?',
      'bob@demo.com': 'What happens if I miss a monthly payment?',
      'carol@demo.com': 'What happens if I miss a monthly payment?'
    }
  },
  {
    outcome: 'NEEDS_DATA',
    label: 'Something about your own account',
    description: 'Looked up live in your account data instead of guessing - subscription status, orders, jobs.',
    examples: {
      'alice@demo.com': 'Is my account in good standing?',
      'bob@demo.com': 'Why did my payment fail?',
      'carol@demo.com': 'Is my account in good standing?'
    }
  },
  {
    outcome: 'ESCALATE',
    label: 'A real bug',
    description: 'Raised as an actual ticket for a developer, with your account data attached to it.',
    examples: {
      'alice@demo.com': 'The app crashes with a white screen every time I open Reports',
      'bob@demo.com': 'The app crashes with a white screen every time I open Reports',
      'carol@demo.com': 'The app crashes with a white screen every time I open Reports'
    }
  }
]

export default function ScopePanel({
  userEmail,
  onPick
}: {
  userEmail: string
  onPick: (question: string) => void
}) {
  return (
    <div className="scope-panel">
      <div className="scope-intro">
        <h2>What can you ask?</h2>
        <p>
          This isn&apos;t a general chatbot - it&apos;s a support triage system with four
          possible outcomes. Whatever you type gets routed to one of these:
        </p>
      </div>

      <div className="scope-grid">
        {CATEGORIES.map(cat => (
          <button
            key={cat.outcome}
            className={`scope-card scope-card-${cat.outcome}`}
            onClick={() => onPick(cat.examples[userEmail] ?? Object.values(cat.examples)[0])}
          >
            <span className={`scope-dot scope-dot-${cat.outcome}`} />
            <span className="scope-label">{cat.label}</span>
            <span className="scope-description">{cat.description}</span>
            <span className="scope-example">
              &ldquo;{cat.examples[userEmail] ?? Object.values(cat.examples)[0]}&rdquo;
            </span>
          </button>
        ))}
      </div>

      <p className="scope-footnote">
        Click any card to try it, or type your own question below - the system
        decides which of the four applies.
      </p>
    </div>
  )
}
