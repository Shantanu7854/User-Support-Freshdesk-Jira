package com.matiks.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.matiks.support.llm.JsonSchemas;
import com.matiks.support.llm.LlmRouter;
import com.matiks.support.llm.Prompts;
import com.matiks.support.model.Records.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The orchestrator. Deterministic Java control flow around two LLM calls.
 *
 * Design rule: READ-ONLY work is model-driven, MUTATING work is code-driven.
 * The model decides the branch and drafts content; Java decides whether to
 * create a ticket and performs every external write. That keeps escalation
 * auditable and stops a model mistake from creating spurious Jira issues.
 */
@Service
public class TriageService {

    private static final Logger log = LoggerFactory.getLogger(TriageService.class);
    private static final int CANDIDATE_LIMIT = 8;

    private final KbRepository kb;
    private final LlmRouter llm;
    private final DiagnosticsService diagnostics;
    private final TicketService tickets;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();
    private final double confidenceFloor;

    public TriageService(KbRepository kb, LlmRouter llm, DiagnosticsService diagnostics,
                         TicketService tickets, JdbcTemplate jdbc,
                         @Value("${llm.match-confidence-floor:0.70}") double confidenceFloor) {
        this.kb = kb;
        this.llm = llm;
        this.diagnostics = diagnostics;
        this.tickets = tickets;
        this.jdbc = jdbc;
        this.confidenceFloor = confidenceFloor;
    }

    public ChatResponse handle(ChatRequest request) {
        String conversationId = ensureConversation(request);
        String userEmail = request.userEmail() == null ? "alice@demo.com" : request.userEmail();
        String message = request.message();

        saveMessage(conversationId, "USER", message, null, null, null, null);

        // 1. Retrieve candidates by lexical similarity.
        List<KbArticle> candidates = kb.search(message, CANDIDATE_LIMIT);
        log.info("Retrieved {} candidates for: {}", candidates.size(), truncate(message));

        // 2. Decide. The reranking call - the core of the product.
        TriageDecision decision = decide(message, candidates);

        // 3. Act on the decision.
        ChatResponse response = switch (decision.outcome()) {
            case "ANSWERED_FROM_KB" -> answerFromKb(conversationId, message, decision);
            case "NEEDS_DATA"       -> answerFromData(conversationId, userEmail, message, decision);
            case "ESCALATE"         -> escalate(conversationId, userEmail, message, decision, null);
            default                 -> explain(conversationId, message, decision);
        };

        saveMessage(conversationId, "ASSISTANT", response.reply(), response.outcome(),
                response.reasoning(), response.confidence(), response.citation());
        return response;
    }

    // ------------------------------------------------------------------
    // Step 2: decide
    // ------------------------------------------------------------------
    private TriageDecision decide(String message, List<KbArticle> candidates) {
        if (!llm.anyAvailable()) {
            return keywordOnlyDecision(candidates);
        }

        String candidateBlock = candidates.isEmpty()
                ? "(no candidates retrieved)"
                : candidates.stream().map(KbArticle::toCandidateLine)
                            .collect(Collectors.joining("\n"));

        String prompt = """
            USER MESSAGE:
            %s

            RETRIEVED CANDIDATES (by keyword overlap - judge them, do not trust the order):
            %s
            """.formatted(message, candidateBlock);

        TriageDecision decision = llm.completeJson(
                Prompts.TRIAGE_SYSTEM, prompt, JsonSchemas.triageDecision(), TriageDecision.class);

        if (decision == null) {
            log.warn("Triage call failed on every provider - degrading to keyword-only");
            return keywordOnlyDecision(candidates);
        }

        // The confidence floor is enforced HERE, not in the prompt. Being
        // confidently wrong is the worst failure this system can produce, so
        // the guard lives in code where it cannot be talked out of.
        if ("ANSWERED_FROM_KB".equals(decision.outcome())) {
            boolean noMatch = decision.matchedKbId() == null;
            boolean lowConfidence = decision.confidence() == null
                    || decision.confidence() < confidenceFloor;
            if (noMatch || lowConfidence) {
                log.info("Rejected KB match (id={}, confidence={}) - below floor {}",
                        decision.matchedKbId(), decision.confidence(), confidenceFloor);
                return new TriageDecision("EXPLANATION", null, decision.confidence(),
                        "No previously-solved ticket confidently matched this, so answering directly.",
                        decision.severity());
            }
        }
        return decision;
    }

    /** No LLM available: take a strong lexical hit, otherwise escalate. */
    private TriageDecision keywordOnlyDecision(List<KbArticle> candidates) {
        if (!candidates.isEmpty() && candidates.get(0).score() > 0.35) {
            KbArticle top = candidates.get(0);
            return new TriageDecision("ANSWERED_FROM_KB", top.id(), 0.75,
                    "Matched a previously-solved ticket by keyword search (AI unavailable).", "LOW");
        }
        return new TriageDecision("ESCALATE", null, 0.0,
                "Could not match this to a known issue, so raising it with the team.", "MEDIUM");
    }

    // ------------------------------------------------------------------
    // Step 3: branches
    // ------------------------------------------------------------------
    private ChatResponse answerFromKb(String conversationId, String message, TriageDecision d) {
        KbArticle article = kb.findById(d.matchedKbId());
        if (article == null) {
            return explain(conversationId, message, d);
        }

        String prompt = """
            USER MESSAGE:
            %s

            PREVIOUSLY-SOLVED TICKET
            Title: %s
            Resolution: %s
            """.formatted(message, article.title(), article.resolutionText());

        String reply = llm.completeText(Prompts.KB_ANSWER_SYSTEM, prompt);
        if (reply == null) reply = article.resolutionText();   // degrade to the raw resolution

        return new ChatResponse(conversationId, reply, "ANSWERED_FROM_KB", d.reasoning(),
                d.confidence(), new KbCitation(article.id(), article.title()),
                null, null, llm.lastProvider());
    }

    private ChatResponse explain(String conversationId, String message, TriageDecision d) {
        String reply = llm.completeText(Prompts.EXPLANATION_SYSTEM, "USER MESSAGE:\n" + message);
        if (reply == null) {
            reply = "I could not find a previously-solved ticket matching this. "
                  + "Could you share a bit more detail, or would you like me to raise a ticket?";
        }
        return new ChatResponse(conversationId, reply, "EXPLANATION", d.reasoning(),
                d.confidence(), null, null, null, llm.lastProvider());
    }

    private ChatResponse answerFromData(String conversationId, String userEmail,
                                        String message, TriageDecision d) {
        List<DiagnosticResult> results = diagnostics.diagnose(userEmail);

        String prompt = """
            USER MESSAGE:
            %s

            READ-ONLY DIAGNOSTIC DATA FOR %s:
            %s
            """.formatted(message, userEmail, renderDiagnostics(results));

        String reply = llm.completeText(Prompts.DIAGNOSIS_SYSTEM, prompt);
        if (reply == null) {
            reply = "I pulled your account data below. Please review it, or ask me to raise a ticket.";
        }

        return new ChatResponse(conversationId, reply, "NEEDS_DATA", d.reasoning(),
                d.confidence(), null, results, null, llm.lastProvider());
    }

    private ChatResponse escalate(String conversationId, String userEmail, String message,
                                  TriageDecision d, List<DiagnosticResult> existing) {
        // Always attach diagnostics: the whole point is that the developer opening
        // the ticket sees the failing rows instead of a vague description.
        List<DiagnosticResult> results = existing != null ? existing : diagnostics.diagnose(userEmail);

        String prompt = """
            USER MESSAGE:
            %s

            TRIAGE REASONING:
            %s

            DIAGNOSTIC DATA:
            %s
            """.formatted(message, d.reasoning(), renderDiagnostics(results));

        EscalationDraft draft = llm.completeJson(
                Prompts.ESCALATION_SYSTEM, prompt,
                JsonSchemas.escalationDraft(), EscalationDraft.class);

        if (draft == null) {
            draft = new EscalationDraft(
                    truncate(message),
                    "Reported by " + userEmail + ":\n\n" + message,
                    d.severity() == null ? "MEDIUM" : d.severity(),
                    "I have raised a ticket for this and someone will look into it shortly.");
        }

        TicketRef ticket = tickets.escalate(conversationId, userEmail, draft, results);

        String reply = draft.userFacingMessage()
                + "\n\nTicket: " + ticket.freshdeskTicketId()
                + "  |  Issue: " + ticket.jiraIssueKey();

        return new ChatResponse(conversationId, reply, "ESCALATE", d.reasoning(),
                d.confidence(), null, results, ticket, llm.lastProvider());
    }

    // ------------------------------------------------------------------
    private String renderDiagnostics(List<DiagnosticResult> results) {
        StringBuilder sb = new StringBuilder();
        for (DiagnosticResult r : results) {
            sb.append('[').append(r.tool()).append("]\n");
            if (r.error() != null) {
                sb.append("  error: ").append(r.error()).append('\n');
            } else if (r.rows().isEmpty()) {
                sb.append("  (no rows)\n");
            } else {
                r.rows().forEach(row -> sb.append("  ").append(row).append('\n'));
            }
        }
        return sb.toString();
    }

    private String ensureConversation(ChatRequest request) {
        if (request.conversationId() != null && !request.conversationId().isBlank()) {
            return request.conversationId();
        }
        String id = UUID.randomUUID().toString();
        try {
            jdbc.update("INSERT INTO conversations (id, user_email) VALUES (?::uuid, ?)",
                    id, request.userEmail());
        } catch (Exception e) {
            log.warn("Could not create conversation row: {}", e.getMessage());
        }
        return id;
    }

    private void saveMessage(String conversationId, String role, String content, String outcome,
                             String reasoning, Double confidence, KbCitation citation) {
        try {
            jdbc.update("""
                INSERT INTO messages
                  (conversation_id, role, content, outcome, reasoning, confidence, matched_kb_id)
                VALUES (?::uuid, ?, ?, ?, ?, ?, ?)
                """,
                conversationId, role, content, outcome, reasoning, confidence,
                citation == null ? null : citation.id());
        } catch (Exception e) {
            log.warn("Could not save message: {}", e.getMessage());
        }
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() > 88 ? s.substring(0, 88) : s;
    }
}
