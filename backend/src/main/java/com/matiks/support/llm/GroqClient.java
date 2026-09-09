package com.matiks.support.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Groq - fallback provider. Free tier, no credit card, OpenAI-compatible.
 *
 * Uses json_schema with strict:true (constrained decoding). Its binding limit
 * is 8K tokens/minute rather than request count, which is why candidate
 * problem text is truncated before it reaches here.
 */
@Component
public class GroqClient implements LlmClient {

    private final RestClient http;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String apiKey;
    private final String model;
    private final String baseUrl;

    public GroqClient(
            @Value("${llm.groq.api-key:}") String apiKey,
            @Value("${llm.groq.model}") String model,
            @Value("${llm.groq.base-url}") String baseUrl) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.baseUrl = baseUrl;
        this.http = RestClient.builder()
                .requestFactory(ClientFactory.timeouts(Duration.ofSeconds(45)))
                .build();
    }

    @Override public String name() { return "groq"; }
    @Override public boolean isAvailable() { return !apiKey.isBlank(); }

    @Override
    public String completeJson(String systemPrompt, String userPrompt, Map<String, Object> schema) {
        Map<String, Object> responseFormat = Map.of(
            "type", "json_schema",
            "json_schema", Map.of(
                "name", "response",
                "strict", true,
                "schema", schema)
        );
        return call(systemPrompt, userPrompt, responseFormat, 0.1);
    }

    @Override
    public String completeText(String systemPrompt, String userPrompt) {
        return call(systemPrompt, userPrompt, null, 0.4);
    }

    private String call(String systemPrompt, String userPrompt,
                        Map<String, Object> responseFormat, double temperature) {
        var body = new java.util.HashMap<String, Object>();
        body.put("model", model);
        body.put("temperature", temperature);
        body.put("messages", List.of(
            Map.of("role", "system", "content", systemPrompt),
            Map.of("role", "user", "content", userPrompt)));
        if (responseFormat != null) {
            body.put("response_format", responseFormat);
        }

        String raw = http.post()
                .uri(baseUrl)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(body)
                .retrieve()
                .body(String.class);

        try {
            JsonNode root = mapper.readTree(raw);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode()) {
                throw new IllegalStateException("Groq returned no content");
            }
            return content.asText();
        } catch (Exception e) {
            throw new IllegalStateException("Groq response parse failed: " + e.getMessage(), e);
        }
    }
}
