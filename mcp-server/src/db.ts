import pg from 'pg'

/**
 * Postgres pool for the diagnostic tools.
 *
 * Read-only is enforced in depth:
 *  1. Ideally connect as a role with only SELECT on app_* (the real control).
 *  2. Every session is set read-only, so a write fails at the database.
 *  3. statement_timeout stops a pathological query holding a connection.
 *  4. Every query below is parameterized - the model supplies values, never SQL.
 *
 * Pool stays small: Supabase's free tier has a modest connection ceiling that
 * this shares with the Spring Boot app.
 */
const { Pool } = pg

export const pool = new Pool({
  connectionString: process.env.DATABASE_URL,
  max: 3,
  idleTimeoutMillis: 30_000,
  connectionTimeoutMillis: 10_000,
  ssl: { rejectUnauthorized: false }
})

/**
 * pool.on('connect', ...) cannot be awaited by the pool itself - it is a
 * fire-and-forget hook, so a query issued via pool.query() right after
 * checkout can race a session-setup statement still in flight on the same
 * connection. Instead, check out the client explicitly, set session defaults
 * and AWAIT them, then run the actual query, before releasing.
 */
export async function query<T = Record<string, unknown>>(
  sql: string,
  params: unknown[] = []
): Promise<T[]> {
  const client = await pool.connect()
  try {
    await client.query('SET default_transaction_read_only = on; SET statement_timeout = 5000')
    const result = await client.query(sql, params)
    return result.rows as T[]
  } finally {
    client.release()
  }
}
