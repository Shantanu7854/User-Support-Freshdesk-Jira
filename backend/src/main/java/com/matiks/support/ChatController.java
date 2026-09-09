package com.matiks.support;

import com.matiks.support.model.Records.ChatRequest;
import com.matiks.support.model.Records.ChatResponse;
import com.matiks.support.model.Records.KbArticle;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final TriageService triage;
    private final KbRepository kb;
    private final JdbcTemplate jdbc;

    public ChatController(TriageService triage, KbRepository kb, JdbcTemplate jdbc) {
        this.triage = triage;
        this.kb = kb;
        this.jdbc = jdbc;
    }

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        return triage.handle(request);
    }

    /** Phase 1 checkpoint endpoint - also handy for demoing that retrieval is lexical. */
    @GetMapping("/kb/search")
    public List<KbArticle> search(@RequestParam String q,
                                  @RequestParam(defaultValue = "8") int limit) {
        return kb.search(q, limit);
    }

    /** Falls back to the known demo personas if the database is unreachable,
     *  so the picker is never empty during a demo. */
    @GetMapping("/personas")
    public List<Map<String, Object>> personas() {
        try {
            return jdbc.queryForList("""
                SELECT email, full_name, plan, subscription_status
                FROM app_users ORDER BY email
                """);
        } catch (Exception e) {
            return List.of(
                Map.of("email", "alice@demo.com", "full_name", "Alice Chen",
                       "plan", "pro", "subscription_status", "active"),
                Map.of("email", "bob@demo.com", "full_name", "Bob Martinez",
                       "plan", "pro", "subscription_status", "past_due"),
                Map.of("email", "carol@demo.com", "full_name", "Carol Okafor",
                       "plan", "enterprise", "subscription_status", "active"));
        }
    }

    /**
     * Health check that deliberately touches Postgres, so the keep-alive cron
     * keeps BOTH the Render service warm and the Supabase project unpaused.
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        String db;
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            db = "up";
        } catch (Exception e) {
            db = "down: " + e.getMessage();
        }
        return Map.of("status", "ok", "database", db);
    }
}
