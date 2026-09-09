package com.matiks.support.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Tries each provider in order and returns the first success.
 *
 * The point is demo resilience: a quota reset, a retired model id, or a
 * transient 5xx on one free tier must not be visible to the person watching.
 * When every provider fails, callers fall back to keyword-only search rather
 * than showing an error - see TriageService.
 */
@Service
public class LlmRouter {

    private static final Logger log = LoggerFactory.getLogger(LlmRouter.class);

    private final List<LlmClient> providers;
    private final ObjectMapper mapper = new ObjectMapper();

    /** Provider that answered the most recent call, for display in the UI. */
    private volatile String lastProvider = "none";

    public LlmRouter(GeminiClient gemini, GroqClient groq) {
        // Order is the failover order: Gemini primary, Groq fallback.
        this.providers = List.of(gemini, groq);
    }

    public boolean anyAvailable() {
        return providers.stream().anyMatch(LlmClient::isAvailable);
    }

    public String lastProvider() {
        return lastProvider;
    }

    /**
     * @return parsed JSON conforming to {@code schema}, or null if every provider failed.
     */
    public <T> T completeJson(String systemPrompt, String userPrompt,
                              Map<String, Object> schema, Class<T> type) {
        for (LlmClient p : providers) {
            if (!p.isAvailable()) continue;
            try {
                String json = p.completeJson(systemPrompt, userPrompt, schema);
                T parsed = mapper.readValue(json, type);
                lastProvider = p.name();
                return parsed;
            } catch (Exception e) {
                log.warn("LLM provider '{}' failed ({}), trying next", p.name(), e.getMessage());
            }
        }
        log.warn("All LLM providers failed or unavailable - caller must degrade");
        lastProvider = "none";
        return null;
    }

    /** @return text, or null if every provider failed. */
    public String completeText(String systemPrompt, String userPrompt) {
        for (LlmClient p : providers) {
            if (!p.isAvailable()) continue;
            try {
                String text = p.completeText(systemPrompt, userPrompt);
                lastProvider = p.name();
                return text;
            } catch (Exception e) {
                log.warn("LLM provider '{}' failed ({}), trying next", p.name(), e.getMessage());
            }
        }
        lastProvider = "none";
        return null;
    }
}
