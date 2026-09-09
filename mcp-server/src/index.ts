import express from 'express'
import { randomUUID } from 'node:crypto'
import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js'
import { StreamableHTTPServerTransport } from '@modelcontextprotocol/sdk/server/streamableHttp.js'
import { z } from 'zod'
import { query } from './db.js'

/**
 * Read-only diagnostic MCP server.
 *
 * Runs as its own process rather than inside the React app, because it holds
 * database credentials - anything in a browser bundle is public. The Spring
 * Boot backend is the MCP client.
 *
 * Three narrow tools rather than one general "run SQL" tool: narrow tools with
 * clear descriptions are selected far more accurately, and a general one would
 * hand the model an injection surface.
 */

const PORT = Number(process.env.MCP_PORT ?? 8090)

function buildServer(): McpServer {
  const server = new McpServer({
    name: 'support-diagnostics',
    version: '1.0.0'
  })

  server.registerTool(
    'get_user_status',
    {
      title: 'Get account status',
      description:
        'Look up a user account: plan, subscription status (active/past_due/cancelled) ' +
        'and region. Call this first when a problem might relate to the account itself, ' +
        'such as billing, access or entitlement questions.',
      inputSchema: { email: z.string().describe('The account email address') }
    },
    async ({ email }) => {
      const rows = await query(
        `SELECT email, full_name, plan, subscription_status, region
         FROM app_users WHERE email = $1 LIMIT 1`,
        [email]
      )
      return { content: [{ type: 'text', text: JSON.stringify(rows, null, 2) }] }
    }
  )

  server.registerTool(
    'get_recent_orders',
    {
      title: 'Get recent orders',
      description:
        'Return this account\'s five most recent orders with status (paid/failed/refunded) ' +
        'and, for failures, the failure_code such as card_expired or insufficient_funds. ' +
        'Call this for any question about payments, charges, billing failures or refunds.',
      inputSchema: { email: z.string().describe('The account email address') }
    },
    async ({ email }) => {
      const rows = await query(
        `SELECT id, amount_cents, status, failure_code, created_at
         FROM app_orders WHERE user_email = $1
         ORDER BY created_at DESC LIMIT 5`,
        [email]
      )
      return { content: [{ type: 'text', text: JSON.stringify(rows, null, 2) }] }
    }
  )

  server.registerTool(
    'get_failed_jobs',
    {
      title: 'Get failed background jobs',
      description:
        'Return recent FAILED background jobs for this account (data_sync, export, report) ' +
        'with their error messages. Call this when something is stuck, not completing, ' +
        'not updating, or missing data.',
      inputSchema: { email: z.string().describe('The account email address') }
    },
    async ({ email }) => {
      const rows = await query(
        `SELECT id, job_type, status, error_message, created_at
         FROM app_jobs WHERE user_email = $1 AND status = 'failed'
         ORDER BY created_at DESC LIMIT 5`,
        [email]
      )
      return { content: [{ type: 'text', text: JSON.stringify(rows, null, 2) }] }
    }
  )

  return server
}

const app = express()
app.use(express.json())

app.get('/health', (_req, res) => {
  res.json({ status: 'ok', service: 'support-diagnostics-mcp' })
})

/**
 * Streamable HTTP transport. Chosen over stdio because the client is a separate
 * container: stdio would require the Java process to spawn `node` as a child,
 * putting Node inside the Java image.
 *
 * Stateless mode - a fresh server and transport per request. These tools hold
 * no session state, and it avoids session-management bugs entirely.
 */
app.post('/mcp', async (req, res) => {
  try {
    const server = buildServer()
    const transport = new StreamableHTTPServerTransport({
      sessionIdGenerator: undefined
    })
    res.on('close', () => {
      transport.close()
      server.close()
    })
    await server.connect(transport)
    await transport.handleRequest(req, res, req.body)
  } catch (err) {
    console.error('MCP request failed:', err)
    if (!res.headersSent) {
      res.status(500).json({
        jsonrpc: '2.0',
        error: { code: -32603, message: 'Internal server error' },
        id: null
      })
    }
  }
})

app.listen(PORT, () => {
  console.log(`MCP diagnostics server listening on :${PORT}/mcp`)
})
