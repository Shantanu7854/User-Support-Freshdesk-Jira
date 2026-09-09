package com.matiks.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.matiks.support.model.Records.DiagnosticResult;
import com.matiks.support.model.Records.EscalationDraft;
import com.matiks.support.model.Records.TicketRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Creates the Freshdesk ticket and the linked Jira issue.
 *
 * Both integrations simulate when credentials are absent, and the UI renders
 * simulated and real results identically. That is what keeps the project
 * demoable after the Freshdesk trial expires, and stops a revoked token from
 * breaking a live demo.
 *
 * Note we create the Jira issue ourselves rather than relying on Freshdesk's
 * "Jira Plus" app: that app is UI-only with no API trigger, and is unavailable
 * below their Growth plan.
 */
@Service
public class TicketService {

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();
    private final RestClient http = RestClient.create();
    private final AtomicInteger simulatedCounter = new AtomicInteger(100);

    private final String freshdeskDomain, freshdeskKey;
    private final String jiraBaseUrl, jiraEmail, jiraToken, jiraProjectKey, jiraAccountId;

    public TicketService(
            JdbcTemplate jdbc,
            @Value("${freshdesk.domain:}") String freshdeskDomain,
            @Value("${freshdesk.api-key:}") String freshdeskKey,
            @Value("${jira.base-url:}") String jiraBaseUrl,
            @Value("${jira.email:}") String jiraEmail,
            @Value("${jira.api-token:}") String jiraToken,
            @Value("${jira.project-key:SUP}") String jiraProjectKey,
            @Value("${jira.account-id:}") String jiraAccountId) {
        this.jdbc = jdbc;
        this.freshdeskDomain = trim(freshdeskDomain);
        this.freshdeskKey = trim(freshdeskKey);
        this.jiraBaseUrl = trim(jiraBaseUrl).replaceAll("/$", "");
        this.jiraEmail = trim(jiraEmail);
        this.jiraToken = trim(jiraToken);
        this.jiraProjectKey = trim(jiraProjectKey);
        this.jiraAccountId = trim(jiraAccountId);

        log.info("Freshdesk: {} | Jira: {}",
                freshdeskEnabled() ? "REAL" : "simulated",
                jiraEnabled() ? "REAL" : "simulated");
    }

    private static String trim(String s) { return s == null ? "" : s.trim(); }
    private boolean freshdeskEnabled() { return !freshdeskDomain.isBlank() && !freshdeskKey.isBlank(); }
    private boolean jiraEnabled() { return !jiraBaseUrl.isBlank() && !jiraEmail.isBlank() && !jiraToken.isBlank(); }

    /**
     * Full escalation chain: Freshdesk ticket -> Jira issue (carrying the
     * diagnostic snapshot) -> Jira key written back onto the Freshdesk ticket
     * as a private note -> persisted locally.
     */
    /** Outcome of one create call: the id/key AND whether it was genuinely
     *  created via the real API, as opposed to a simulated fallback id. This
     *  is tracked separately from "are credentials configured", because a
     *  configured-but-failing call (bad project key, revoked token, a
     *  transient 5xx) must also report as simulated - reporting success with
     *  a fabricated link is worse than admitting the fallback ran. */
    private record CreateResult(String id, boolean real) {}

    public TicketRef escalate(String conversationId, String userEmail,
                              EscalationDraft draft, List<DiagnosticResult> diagnostics) {

        String diagnosticText = formatDiagnostics(diagnostics);
        String fullBody = draft.body()
                + "\n\n--- Reported by ---\n" + userEmail
                + "\n\n--- Diagnostic data captured at triage ---\n" + diagnosticText;

        CreateResult freshdesk = createFreshdeskTicket(draft, userEmail, fullBody);
        CreateResult jira = createJiraIssue(draft, fullBody);

        if (freshdesk.real() && jira.real()) {
            addFreshdeskNote(freshdesk.id(), "Linked Jira issue: " + jira.id());
        }

        boolean simulated = !freshdesk.real() || !jira.real();
        String jiraUrl = jira.real() ? jiraBaseUrl + "/browse/" + jira.id() : null;

        persist(conversationId, draft, freshdesk.id(), jira.id(), diagnostics, simulated);
        return new TicketRef(freshdesk.id(), jira.id(), jiraUrl, simulated);
    }

    // ------------------------------------------------------------------
    // Freshdesk
    // ------------------------------------------------------------------
    private CreateResult createFreshdeskTicket(EscalationDraft draft, String userEmail, String body) {
        if (!freshdeskEnabled()) {
            return new CreateResult("FD-" + simulatedCounter.incrementAndGet(), false);
        }
        try {
            // Basic auth: API key as username, literal "X" as password.
            String auth = Base64.getEncoder().encodeToString(
                    (freshdeskKey + ":X").getBytes(StandardCharsets.UTF_8));

            String response = http.post()
                    .uri("https://%s.freshdesk.com/api/v2/tickets".formatted(freshdeskDomain))
                    .header("Authorization", "Basic " + auth)
                    .header("Content-Type", "application/json")
                    .body(Map.of(
                        "subject", draft.subject(),
                        "description", body.replace("\n", "<br/>"),
                        "email", userEmail,
                        "priority", severityToFreshdeskPriority(draft.severity()),
                        "status", 2))          // 2 = Open
                    .retrieve()
                    .body(String.class);

            JsonNode node = mapper.readTree(response);
            String id = node.path("id").asText(null);
            if (id == null) throw new IllegalStateException("no id in Freshdesk response");
            return new CreateResult(id, true);
        } catch (Exception e) {
            log.warn("Freshdesk create failed, simulating: {}", e.getMessage());
            return new CreateResult("FD-" + simulatedCounter.incrementAndGet(), false);
        }
    }

    private void addFreshdeskNote(String ticketId, String note) {
        try {
            String auth = Base64.getEncoder().encodeToString(
                    (freshdeskKey + ":X").getBytes(StandardCharsets.UTF_8));
            http.post()
                .uri("https://%s.freshdesk.com/api/v2/tickets/%s/notes"
                        .formatted(freshdeskDomain, ticketId))
                .header("Authorization", "Basic " + auth)
                .header("Content-Type", "application/json")
                .body(Map.of("body", note, "private", true))
                .retrieve()
                .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Freshdesk note failed (ticket still created): {}", e.getMessage());
        }
    }

    private int severityToFreshdeskPriority(String severity) {
        return switch (severity == null ? "" : severity.toUpperCase()) {
            case "HIGH" -> 3;
            case "LOW"  -> 1;
            default     -> 2;
        };
    }

    // ------------------------------------------------------------------
    // Jira
    // ------------------------------------------------------------------
    private CreateResult createJiraIssue(EscalationDraft draft, String body) {
        if (!jiraEnabled()) {
            return new CreateResult(jiraProjectKey + "-" + simulatedCounter.incrementAndGet(), false);
        }
        try {
            String auth = Base64.getEncoder().encodeToString(
                    (jiraEmail + ":" + jiraToken).getBytes(StandardCharsets.UTF_8));

            var fields = new java.util.HashMap<String, Object>();
            fields.put("project", Map.of("key", jiraProjectKey));
            fields.put("issuetype", Map.of("name", "Task"));
            fields.put("summary", draft.subject());
            fields.put("description", adf(body));
            // accountId only - username/name were removed post-GDPR.
            if (!jiraAccountId.isBlank()) {
                fields.put("assignee", Map.of("id", jiraAccountId));
            }

            String response = http.post()
                    .uri(jiraBaseUrl + "/rest/api/3/issue")
                    .header("Authorization", "Basic " + auth)
                    .header("Content-Type", "application/json")
                    .body(Map.of("fields", fields))
                    .retrieve()
                    .body(String.class);

            JsonNode node = mapper.readTree(response);
            String key = node.path("key").asText(null);
            if (key == null) throw new IllegalStateException("no key in Jira response");
            return new CreateResult(key, true);
        } catch (Exception e) {
            log.warn("Jira create failed, simulating: {}", e.getMessage());
            return new CreateResult(jiraProjectKey + "-" + simulatedCounter.incrementAndGet(), false);
        }
    }

    /**
     * Atlassian Document Format. Jira REST v3 rejects a plain string description
     * with a 400 - this is the single most common first-call failure.
     */
    private Map<String, Object> adf(String text) {
        List<Map<String, Object>> paragraphs = text.lines()
                .map(line -> line.isBlank()
                    ? Map.<String, Object>of("type", "paragraph")
                    : Map.<String, Object>of(
                        "type", "paragraph",
                        "content", List.of(Map.of("type", "text", "text", line))))
                .toList();
        return Map.of("version", 1, "type", "doc", "content", paragraphs);
    }

    // ------------------------------------------------------------------
    private String formatDiagnostics(List<DiagnosticResult> diagnostics) {
        if (diagnostics == null || diagnostics.isEmpty()) {
            return "(no diagnostics captured)";
        }
        StringBuilder sb = new StringBuilder();
        for (DiagnosticResult d : diagnostics) {
            sb.append("\n[").append(d.tool()).append("]\n");
            if (d.error() != null) {
                sb.append("  error: ").append(d.error()).append('\n');
            } else if (d.rows().isEmpty()) {
                sb.append("  (no rows)\n");
            } else {
                for (Map<String, Object> row : d.rows()) {
                    sb.append("  ").append(row).append('\n');
                }
            }
        }
        return sb.toString();
    }

    private void persist(String conversationId, EscalationDraft draft, String freshdeskId,
                         String jiraKey, List<DiagnosticResult> diagnostics, boolean simulated) {
        try {
            String diagJson = mapper.writeValueAsString(diagnostics);
            jdbc.update("""
                INSERT INTO escalations
                  (conversation_id, subject, body, severity,
                   freshdesk_ticket_id, jira_issue_key, diagnostics, simulated)
                VALUES (?::uuid, ?, ?, ?, ?, ?, ?::jsonb, ?)
                """,
                conversationId, draft.subject(), draft.body(), draft.severity(),
                freshdeskId, jiraKey, diagJson, simulated);
        } catch (Exception e) {
            log.warn("Could not persist escalation: {}", e.getMessage());
        }
    }
}
