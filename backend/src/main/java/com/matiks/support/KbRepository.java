package com.matiks.support;

import com.matiks.support.model.Records.KbArticle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Candidate retrieval over previously-solved tickets.
 *
 * Two arms in one query:
 *   - full-text (tsvector) catches conceptual overlap
 *   - trigram similarity catches typos and product names that stemming destroys
 * Weighted sum, best 8 returned. The LLM then reranks these for actual meaning.
 */
@Repository
public class KbRepository {

    private static final Logger log = LoggerFactory.getLogger(KbRepository.class);

    private final JdbcTemplate jdbc;

    public KbRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<KbArticle> MAPPER = (rs, i) -> new KbArticle(
            rs.getLong("id"),
            rs.getString("title"),
            rs.getString("problem_text"),
            rs.getString("resolution_text"),
            rs.getString("category"),
            rs.getDouble("score"));

    /*
     * websearch_to_tsquery, never to_tsquery: the input is a raw chat message and
     * to_tsquery throws a syntax error on unescaped punctuation. websearch_ never
     * raises - it just parses what it can.
     *
     * The trigram arm filters with % (index-backed) and orders by the combined
     * score. set_limit() is applied per-connection in search() below because the
     * 0.3 default is too strict for chat-length text.
     */
    private static final String SEARCH_SQL = """
        SELECT id, title, problem_text, resolution_text, category,
               (ts_rank_cd(search_vector, websearch_to_tsquery('english', ?)) * 2.0
                + GREATEST(similarity(title, ?), similarity(problem_text, ?))) AS score
        FROM kb_articles
        WHERE search_vector @@ websearch_to_tsquery('english', ?)
           OR title % ?
           OR problem_text % ?
        ORDER BY score DESC
        LIMIT ?
        """;

    public List<KbArticle> search(String query, int limit) {
        if (query == null || query.isBlank()) return List.of();
        try {
            // Lower the trigram threshold for this connection; chat text is long
            // and the 0.3 default rejects genuine near-matches.
            jdbc.queryForObject("SELECT set_limit(0.2)", Float.class);
            return jdbc.query(SEARCH_SQL, MAPPER,
                    query, query, query, query, query, query, limit);
        } catch (Exception e) {
            log.error("KB search failed: {}", e.getMessage());
            return List.of();
        }
    }

    public KbArticle findById(long id) {
        try {
            return jdbc.queryForObject("""
                SELECT id, title, problem_text, resolution_text, category, 1.0 AS score
                FROM kb_articles WHERE id = ?
                """, MAPPER, id);
        } catch (Exception e) {
            log.warn("KB article {} not found", id);
            return null;
        }
    }
}
