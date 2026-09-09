package com.matiks.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.matiks.support.model.Records.DiagnosticResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Diagnostics over the MCP wire. Active when mcp.enabled=true.
 *
 * This speaks MCP's Streamable HTTP transport directly with RestClient rather
 * than using the MCP Java SDK. The reason is deliberate: the SDK is at 2.0.x
 * after a reactive rewrite, its convenience artifact bundles Jackson 3 (which
 * collides with Spring Boot's Jackson 2 at runtime), and nearly every example
 * online targets the incompatible 0.x API. MCP is JSON-RPC 2.0 over POST, and
 * the three calls we need - initialize, tools/list, tools/call - are small
 * enough that owning them outright is less risk than the dependency.
 *
 * Falls back to LocalDiagnostics automatically if the server is unreachable,
 * so stopping the MCP container degrades the demo instead of breaking it.
 */
@Service
@ConditionalOnProperty(name = "mcp.enabled", havingValue = "true")
public class McpDiagnostics implements DiagnosticsService {

    private static final Logger log = LoggerFactory.getLogger(McpDiagnostics.class);
    private static final List<String> TOOLS =
            List.of("get_user_status", "get_recent_orders", "get_failed_jobs");

    private final RestClient http = RestClient.create();
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicLong requestId = new AtomicLong(1);
    private final String mcpUrl;
    private final LocalDiagnostics fallback;

    public McpDiagnostics(@Value("${mcp.url}") String mcpUrl, JdbcTemplate jdbc) {
        this.mcpUrl = mcpUrl;
        this.fallback = new LocalDiagnostics(jdbc);
        log.info("Diagnostics mode: MCP ({})", mcpUrl);
    }

    @Override public String mode() { return "mcp"; }

    @Override
    public List<DiagnosticResult> diagnose(String userEmail) {
        List<DiagnosticResult> results = new ArrayList<>();
        for (String tool : TOOLS) {
            try {
                results.add(callTool(tool, userEmail));
            } catch (Exception e) {
                log.warn("MCP unreachable ({}), falling back to local diagnostics", e.getMessage());
                return fallback.diagnose(userEmail);
            }
        }
        return results;
    }

    private DiagnosticResult callTool(String tool, String email) {
        Map<String, Object> request = Map.of(
            "jsonrpc", "2.0",
            "id", requestId.getAndIncrement(),
            "method", "tools/call",
            "params", Map.of(
                "name", tool,
                "arguments", Map.of("email", email))
        );

        String raw = http.post()
                .uri(mcpUrl)
                .header("Content-Type", "application/json")
                // Streamable HTTP requires BOTH content types in Accept, even
                // when the server answers with plain JSON.
                .header("Accept", "application/json, text/event-stream")
                .body(request)
                .retrieve()
                .body(String.class);

        return parse(tool, raw);
    }

    private DiagnosticResult parse(String tool, String raw) {
        try {
            JsonNode root = mapper.readTree(extractJson(raw));
            if (root.has("error")) {
                return DiagnosticResult.failed(tool, root.path("error").path("message").asText());
            }
            // Tool results arrive as content blocks; ours is a JSON array in text.
            String text = root.path("result").path("content").path(0).path("text").asText("[]");
            List<Map<String, Object>> rows =
                    mapper.readValue(text, new TypeReference<List<Map<String, Object>>>() {});
            return DiagnosticResult.of(tool, rows);
        } catch (Exception e) {
            return DiagnosticResult.failed(tool, "parse failed: " + e.getMessage());
        }
    }

    /**
     * The transport may answer as an SSE stream even for a single response, in
     * which case the JSON sits on a "data:" line. Handle both shapes.
     */
    private String extractJson(String body) {
        if (body == null) return "{}";
        String trimmed = body.trim();
        if (trimmed.startsWith("{")) return trimmed;
        return trimmed.lines()
                .filter(l -> l.startsWith("data:"))
                .map(l -> l.substring(5).trim())
                .filter(l -> !l.isEmpty())
                .reduce((a, b) -> b)     // last data frame carries the result
                .orElse("{}");
    }
}
