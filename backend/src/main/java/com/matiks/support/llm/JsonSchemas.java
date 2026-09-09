package com.matiks.support.llm;

import java.util.List;
import java.util.Map;

/**
 * JSON Schemas for the structured calls. Written once here, then wrapped in
 * each provider's own envelope - Gemini wants `responseSchema`, Groq wants
 * `json_schema.schema`, but the schema object itself is identical.
 */
public final class JsonSchemas {
    private JsonSchemas() {}

    private static Map<String, Object> str(String description) {
        return Map.of("type", "string", "description", description);
    }

    public static Map<String, Object> triageDecision() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "outcome", Map.of(
                    "type", "string",
                    "enum", List.of("ANSWERED_FROM_KB", "EXPLANATION", "NEEDS_DATA", "ESCALATE"),
                    "description", "Which branch resolves this message"),
                "matchedKbId", Map.of(
                    "type", List.of("integer", "null"),
                    "description", "id of the genuinely matching article, or null"),
                "confidence", Map.of(
                    "type", "number",
                    "description", "0.0-1.0 confidence that matchedKbId truly solves the problem"),
                "reasoning", str("One or two sentences explaining the choice. Shown to the user."),
                "severity", Map.of(
                    "type", "string",
                    "enum", List.of("LOW", "MEDIUM", "HIGH"),
                    "description", "Severity when escalating; LOW otherwise")
            ),
            "required", List.of("outcome", "matchedKbId", "confidence", "reasoning", "severity"),
            "additionalProperties", false
        );
    }

    public static Map<String, Object> escalationDraft() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "subject", str("Ticket subject, under 90 characters, no ID prefix"),
                "body", str("Problem description, observed vs expected behaviour"),
                "severity", Map.of("type", "string", "enum", List.of("LOW", "MEDIUM", "HIGH")),
                "userFacingMessage", str("Short reassuring message shown to the user in chat")
            ),
            "required", List.of("subject", "body", "severity", "userFacingMessage"),
            "additionalProperties", false
        );
    }
}
