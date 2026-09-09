-- =====================================================================
--  AI Support Triage — schema
--  Run this in the Supabase SQL editor (SQL Editor -> New query -> Run)
--  Safe to re-run: drops and recreates everything.
-- =====================================================================

drop table if exists messages        cascade;
drop table if exists escalations     cascade;
drop table if exists conversations   cascade;
drop table if exists kb_articles     cascade;
drop table if exists app_orders      cascade;
drop table if exists app_jobs        cascade;
drop table if exists app_users       cascade;

create extension if not exists pg_trgm;

-- ---------------------------------------------------------------------
-- Previously-solved tickets. This is what the "AI model" analyzes.
-- search_vector is GENERATED, so it always stays in sync with the text
-- columns; there is no trigger to maintain and no way to forget one.
-- Title is weighted 'A' (highest) and the problem statement 'B'.
-- The resolution text is deliberately NOT indexed: we match on what the
-- user is experiencing, not on the fix they haven't seen yet.
-- ---------------------------------------------------------------------
create table kb_articles (
  id              bigint generated always as identity primary key,
  title           text not null,
  problem_text    text not null,
  resolution_text text not null,
  category        text,
  created_at      timestamptz not null default now(),
  search_vector   tsvector generated always as (
    setweight(to_tsvector('english', coalesce(title, '')),        'A') ||
    setweight(to_tsvector('english', coalesce(problem_text, '')), 'B')
  ) stored
);

create index kb_articles_fts_idx     on kb_articles using gin (search_vector);
create index kb_articles_title_trgm  on kb_articles using gin (title        gin_trgm_ops);
create index kb_articles_problem_trgm on kb_articles using gin (problem_text gin_trgm_ops);

-- ---------------------------------------------------------------------
-- Chat
-- ---------------------------------------------------------------------
create table conversations (
  id         uuid primary key default gen_random_uuid(),
  user_email text,
  created_at timestamptz not null default now()
);

create table messages (
  id              bigint generated always as identity primary key,
  conversation_id uuid not null references conversations(id) on delete cascade,
  role            text not null check (role in ('USER', 'ASSISTANT')),
  content         text not null,
  outcome         text,          -- ANSWERED_FROM_KB | EXPLANATION | NEEDS_DATA | ESCALATE
  reasoning       text,          -- why the model chose that branch (shown in the UI)
  matched_kb_id   bigint references kb_articles(id),
  confidence      numeric(3,2),
  diagnostics     jsonb,         -- snapshot of MCP tool output, if any
  ticket_ref      jsonb,         -- {freshdeskId, jiraKey, simulated}
  created_at      timestamptz not null default now()
);

create index messages_conversation_idx on messages (conversation_id, created_at);

-- ---------------------------------------------------------------------
-- Escalations: the link between a conversation and Freshdesk + Jira
-- ---------------------------------------------------------------------
create table escalations (
  id                  bigint generated always as identity primary key,
  conversation_id     uuid references conversations(id) on delete cascade,
  subject             text not null,
  body                text,
  severity            text,
  freshdesk_ticket_id text,
  jira_issue_key      text,
  diagnostics         jsonb,     -- what the developer sees attached to the ticket
  simulated           boolean not null default false,
  created_at          timestamptz not null default now()
);

create index escalations_conversation_idx on escalations (conversation_id);

-- =====================================================================
--  Demo business data. The MCP server reads these — and ONLY these.
--  Everything below is what a real product's operational tables would
--  look like; the diagnostic tools answer questions about them.
-- =====================================================================
create table app_users (
  id                  uuid primary key default gen_random_uuid(),
  email               text unique not null,
  full_name           text,
  plan                text,      -- free | pro | enterprise
  subscription_status text,      -- active | past_due | cancelled
  region              text,
  created_at          timestamptz not null default now()
);

create table app_orders (
  id           bigint generated always as identity primary key,
  user_email   text not null references app_users(email) on delete cascade,
  amount_cents integer not null,
  status       text not null,    -- paid | failed | refunded | pending
  failure_code text,             -- card_expired | insufficient_funds | ...
  created_at   timestamptz not null default now()
);

create table app_jobs (
  id            bigint generated always as identity primary key,
  user_email    text not null references app_users(email) on delete cascade,
  job_type      text not null,   -- data_sync | export | report
  status        text not null,   -- success | failed | running
  error_message text,
  created_at    timestamptz not null default now()
);

create index app_orders_user_idx on app_orders (user_email, created_at desc);
create index app_jobs_user_idx   on app_jobs   (user_email, created_at desc);
