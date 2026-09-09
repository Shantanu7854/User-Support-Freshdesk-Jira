package com.matiks.support;

import com.matiks.support.model.Records.DiagnosticResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Diagnostics run directly against Postgres. Active when mcp.enabled=false.
 *
 * Deliberately mirrors the three MCP tools exactly - same names, same queries,
 * same row shapes - so switching transports changes nothing the model sees.
 */
@Service
@ConditionalOnProperty(name = "mcp.enabled", havingValue = "false", matchIfMissing = true)
public class LocalDiagnostics implements DiagnosticsService {

    private static final Logger log = LoggerFactory.getLogger(LocalDiagnostics.class);

    private final JdbcTemplate jdbc;

    public LocalDiagnostics(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        log.info("Diagnostics mode: LOCAL (direct JDBC)");
    }

    @Override public String mode() { return "local"; }

    @Override
    public List<DiagnosticResult> diagnose(String userEmail) {
        List<DiagnosticResult> results = new ArrayList<>();
        results.add(run("get_user_status", """
            SELECT email, full_name, plan, subscription_status, region
            FROM app_users WHERE email = ? LIMIT 1
            """, userEmail));
        results.add(run("get_recent_orders", """
            SELECT id, amount_cents, status, failure_code, created_at
            FROM app_orders WHERE user_email = ?
            ORDER BY created_at DESC LIMIT 5
            """, userEmail));
        results.add(run("get_failed_jobs", """
            SELECT id, job_type, status, error_message, created_at
            FROM app_jobs WHERE user_email = ? AND status = 'failed'
            ORDER BY created_at DESC LIMIT 5
            """, userEmail));
        return results;
    }

    private DiagnosticResult run(String tool, String sql, Object... args) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
            return DiagnosticResult.of(tool, rows);
        } catch (Exception e) {
            log.warn("Diagnostic '{}' failed: {}", tool, e.getMessage());
            return DiagnosticResult.failed(tool, e.getMessage());
        }
    }
}
