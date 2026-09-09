package com.matiks.support.llm;

import java.util.Map;

/**
 * One LLM provider. Both implementations guarantee schema-valid JSON via
 * constrained decoding, so callers never parse prose or defend against
 * malformed output.
 */
public interface LlmClient {

    /** Provider name, surfaced in the API response so the UI can show which one answered. */
    String name();

    /** False when no API key is configured - the router skips it silently. */
    boolean isAvailable();

    /**
     * Complete with a guaranteed-conforming JSON response.
     *
     * @param schema JSON Schema (as a Map) the response must satisfy
     * @return raw JSON text, always parseable against {@code schema}
     */
    String completeJson(String systemPrompt, String userPrompt, Map<String, Object> schema);

    /** Complete with a plain-text response. */
    String completeText(String systemPrompt, String userPrompt);
}
