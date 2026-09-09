package com.matiks.support.model;

import java.util.List;
import java.util.Map;

/** All DTOs for the app. Kept in one file - they are small and always read together. */
public final class Records {
    private Records() {}

    /** A previously-solved ticket. */
    public record KbArticle(
            long id,
            String title,
            String problemText,
            String resolutionText,
            String category,
            double score) {

        /** Compact form sent to the reranking model.
         *  Problem text is truncated and resolution omitted entirely: the decision
         *  is made on the PROBLEM, and the full resolution is fetched afterwards by
         *  id. Roughly halves prompt size, which matters against Groq's 8K TPM cap. */
        public String toCandidateLine() {
            String p = problemText.length() > 200
                    ? problemText.substring(0, 200) + "..."
                    : problemText;
            return "[id=%d] %s | %s".formatted(id, title, p);
        }
    }

    /** Structured output of the triage/rerank call. */
    public record TriageDecision(
            String outcome,        // ANSWERED_FROM_KB | EXPLANATION | NEEDS_DATA | ESCALATE
            Long matchedKbId,
            Double confidence,
            String reasoning,
            String severity) {

        public static TriageDecision fallback(String outcome, String reasoning) {
            return new TriageDecision(outcome, null, 0.0, reasoning, "MEDIUM");
        }
    }

    /** Structured output of the escalation-draft call. */
    public record EscalationDraft(
            String subject,
            String body,
            String severity,
            String userFacingMessage) {}

    /** One diagnostic tool result. */
    public record DiagnosticResult(
            String tool,
            List<Map<String, Object>> rows,
            String error) {

        public static DiagnosticResult of(String tool, List<Map<String, Object>> rows) {
            return new DiagnosticResult(tool, rows, null);
        }
        public static DiagnosticResult failed(String tool, String error) {
            return new DiagnosticResult(tool, List.of(), error);
        }
    }

    /** Created ticket references. */
    public record TicketRef(
            String freshdeskTicketId,
            String jiraIssueKey,
            String jiraUrl,
            boolean simulated) {}

    // ---- API contract ----

    public record ChatRequest(String conversationId, String userEmail, String message) {}

    public record ChatResponse(
            String conversationId,
            String reply,
            String outcome,
            String reasoning,
            Double confidence,
            KbCitation citation,
            List<DiagnosticResult> diagnostics,
            TicketRef ticket,
            String llmProvider) {}

    public record KbCitation(long id, String title) {}
}
