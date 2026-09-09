package com.matiks.support.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Google Gemini via AI Studio - primary provider. Free tier, no credit card.
 *
 * Structured output uses responseSchema with responseMimeType=application/json,
 * which is constrained decoding: the model cannot emit non-conforming output.
 */
@Component
public class GeminiClient implements LlmClient {

    private final RestClient http;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String apiKey;
    private final String model;
    private final String baseUrl;

    public GeminiClient(
            @Value("${llm.gemini.api-key:}") String apiKey,
            @Value("${llm.gemini.model}") String model,
            @Value("${llm.gemini.base-url}") String baseUrl) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.baseUrl = baseUrl;
        this.http = RestClient.builder()
                .requestFactory(ClientFactory.timeouts(Duration.ofSeconds(45)))
                .build();
    }

    @Override public String name() { return "gemini"; }
    @Override public boolean isAvailable() { return !apiKey.isBlank(); }

    @Override
    public String completeJson(String systemPrompt, String userPrompt, Map<String, Object> schema) {
        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.put("responseSchema", toGeminiSchema(schema));
        // 0.0, not just low: this is a classification decision, not creative
        // writing, and sampling noise here directly costs demo consistency.
        generationConfig.put("temperature", 0.0);
        return call(systemPrompt, userPrompt, generationConfig);
    }

    @Override
    public String completeText(String systemPrompt, String userPrompt) {
        return call(systemPrompt, userPrompt, Map.of("temperature", 0.4));
    }

    private String call(String systemPrompt, String userPrompt, Map<String, Object> generationConfig) {
        Map<String, Object> body = Map.of(
            "systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))),
            "contents", List.of(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", userPrompt)))),
            "generationConfig", generationConfig
        );

        String url = "%s/%s:generateContent?key=%s".formatted(baseUrl, model, apiKey);
        String raw = http.post()
                .uri(url)
                .header("Content-Type", "application/json")
                .body(body)
                .retrieve()
                .body(String.class);

        try {
            JsonNode root = mapper.readTree(raw);
            JsonNode text = root.path("candidates").path(0)
                                .path("content").path("parts").path(0).path("text");
            if (text.isMissingNode()) {
                // A blocked or empty response is a real failure - let the router fall through.
                throw new IllegalStateException("Gemini returned no text: " + truncate(raw));
            }
            return text.asText();
        } catch (Exception e) {
            throw new IllegalStateException("Gemini response parse failed: " + e.getMessage(), e);
        }
    }

    /**
     * Gemini rejects JSON Schema's union types (["integer","null"]) and the
     * additionalProperties keyword. Convert a nullable union to its first
     * concrete type and drop unsupported keys.
     */
    @SuppressWarnings("unchecked")
    private Object toGeminiSchema(Object node) {
        if (node instanceof Map<?, ?> m) {
            Map<String, Object> out = new HashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                String key = String.valueOf(e.getKey());
                if (key.equals("additionalProperties")) continue;
                Object value = e.getValue();
                if (key.equals("type") && value instanceof List<?> types) {
                    Object concrete = types.stream()
                            .map(t -> (Object) t)
                            .filter(t -> !"null".equals(String.valueOf(t)))
                            .findFirst().orElse("string");
                    out.put("type", String.valueOf(concrete).toUpperCase());
                    out.put("nullable", true);
                } else if (key.equals("type")) {
                    out.put("type", String.valueOf(value).toUpperCase());
                } else {
                    out.put(key, toGeminiSchema(value));
                }
            }
            return out;
        }
        if (node instanceof List<?> l) {
            return l.stream().map(this::toGeminiSchema).toList();
        }
        return node;
    }

    private static String truncate(String s) {
        return s == null ? "null" : (s.length() > 300 ? s.substring(0, 300) : s);
    }
}
