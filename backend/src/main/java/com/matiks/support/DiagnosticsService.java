package com.matiks.support;

import com.matiks.support.model.Records.DiagnosticResult;

import java.util.List;

/**
 * Read-only diagnostics over a user's own account data.
 *
 * Two implementations: LocalDiagnostics (direct JDBC) and McpDiagnostics (over
 * the MCP wire). Both expose the SAME three tools with the same shapes, so the
 * model-facing behaviour is identical and MCP can be switched off without
 * changing anything else. That seam is what keeps a demo from being blocked by
 * a transport problem.
 */
public interface DiagnosticsService {

    /** Which implementation answered, surfaced in the UI. */
    String mode();

    /** Runs the diagnostic tools relevant to this account. */
    List<DiagnosticResult> diagnose(String userEmail);
}
