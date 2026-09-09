# AI Support Triage

A support chat that triages itself — and hands developers a ticket that already
contains the evidence.

A user describes a problem in plain language. The system does one of four things:

| Outcome | What happens |
|---|---|
| **Answered from history** | Searches previously-solved tickets, finds a genuine match, adapts that resolution |
| **Explained** | No past ticket matches, but it's a misunderstanding — answers directly |
| **Diagnosed from live data** | Queries the live database through an **MCP server** to show what's actually wrong |
| **Escalated** | Creates a **Freshdesk** ticket and a linked **Jira** issue, *with the diagnostic data attached* |

## How the "AI" works

There is **no vector database and no embeddings**. Two stages:

1. **Postgres searches** — full-text (`tsvector`) plus fuzzy trigram matching
   returns the 8 most lexically similar solved tickets.
2. **The LLM reranks** — one call judges whether any candidate *genuinely*
   solves the problem, rather than merely sharing keywords.

Stage 2 is the point. Keyword search alone would confidently return
*"payment declined"* when the user said *"charged twice"* — same vocabulary,
opposite problem. The model catches that.

It also returns a confidence score, and **Java rejects any match below 0.70**,
demoting to a general explanation. That guard lives in code, not the prompt:
being confidently wrong is the worst thing this system can do.

## Stack

React + Vite · Spring Boot 3 / Java 21 · Supabase Postgres · Gemini + Groq ·
MCP server (Node) · Freshdesk + Jira · one Docker container

The React build is bundled **into the Spring Boot jar**, so there is one
deployable, one URL, and no CORS configuration anywhere.

---

## Setup

### 1. Accounts (~20 min, nothing costs money, no credit card)

| Service | What you need |
|---|---|
| [Google AI Studio](https://aistudio.google.com) | Sign in → Get API key |
| [Groq](https://console.groq.com) | Sign up → API Keys (fallback provider) |
| [Supabase](https://supabase.com) | New project — **save the DB password, shown once** |
| [Jira Cloud](https://www.atlassian.com/software/jira) | Free site; create a project, note its key (e.g. `SUP`) |
| [Freshdesk](https://freshdesk.com) | 14-day trial — the permanent free plan has **zero API access** |

### 2. Database

In the Supabase SQL editor, run in order:

1. `db/01_schema.sql`
2. `db/02_seed.sql`

### 3. Configure

```bash
cp .env.example .env
```

Fill in `.env`. **Only the datasource is required** — every other value left
blank degrades to a fallback instead of failing.

> **The connection string is the one detail that will waste your time.**
> Use Project Settings → Database → **Session pooler**, port **5432**.
> Not 6543 (transaction mode breaks prepared statements), and not the direct
> `db.<ref>.supabase.co` host (IPv6-only; won't connect from most machines or
> from Render). Username takes the dotted form `postgres.<project-ref>`.

### 4. Run

```bash
docker compose up --build          # http://localhost:8080
```

Or without Docker:

```bash
cd frontend && npm install && npm run build
cp -r dist/* ../backend/src/main/resources/static/
cd ../backend && mvn spring-boot:run

cd mcp-server && npm install && npm run dev    # separate terminal
```

---

## Deploy to Render (free, no credit card)

1. Push to GitHub.
2. Render → **New Web Service** → connect the repo → runtime **Docker**.
3. Paste the environment variables from your `.env`.
4. Deploy. You get a public HTTPS URL.

### Keep the demo alive — do this before sharing the link

Render's free tier sleeps after 15 minutes idle (~50s cold start), and Supabase
pauses a free project after 7 days idle, requiring a **manual** restore.

`.github/workflows/keepalive.yml` fixes both with one request: set the
`APP_URL` repository variable (Settings → Secrets and variables → Actions →
Variables) to your Render URL. `/api/health` runs a real query, so the ping
keeps the service warm *and* the database active.

---

## Demo script (~2 minutes)

Switch personas with the dropdown; each drives a different branch.

1. **"How do I export my data to CSV?"** *(alice)* → answered from a past ticket, with citation.
   > *"It found this in previously-solved tickets — nobody wrote this answer for the bot."*

2. **"I was charged twice for my subscription"** *(alice)* → the KB contains a
   *payment declined* article. Watch it **refuse** to use it.
   > *"Keyword search would have returned the declined-payment article. It rejected it
   > because that resolution wouldn't actually solve this."* ← the moment that shows judgment

3. **"Why did my payment fail?"** *(bob)* → queries live data via MCP, finds `card_expired`.
   > *"That came from the live database through a read-only MCP server, at the model's request."*

4. **"My data sync has been stuck for two days"** *(carol)* → escalates.
   Open the Jira issue and show the diagnostic data already inside.
   > *"The developer opens this and the failing rows are already attached."*

---

## Design notes

**Read-only work is model-driven; mutating work is code-driven.** The model
picks the branch and drafts ticket content. Java decides whether to escalate and
performs every external write, so escalation stays auditable and a model mistake
can't create spurious Jira issues.

**Every integration degrades.** Gemini fails → Groq → keyword-only search.
Missing Freshdesk/Jira credentials → realistic simulated IDs. MCP unreachable →
direct JDBC diagnostics. The UI renders identically. An expired trial or revoked
token cannot break a live demo.

**The MCP server is a separate process, never in React** — it holds database
credentials, and anything in a browser bundle is public. Read-only is enforced
by a `SELECT`-only role, `default_transaction_read_only`, parameterized queries,
and row limits.

**Not in scope:** authentication (a persona dropdown stands in), an agent
dashboard, Jira status sync-back, KB auto-writeback, tests.
