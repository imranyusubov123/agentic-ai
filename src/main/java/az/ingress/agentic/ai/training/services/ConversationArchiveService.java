package az.ingress.agentic.ai.training.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationArchiveService {

    private static final int RECENT_EXCHANGES = 4;
    private static final int CANDIDATE_LIMIT = 10;
    private static final int SELECTED_LIMIT = 3;
    private static final int MAX_HISTORY_CHARACTERS = 6000;

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingModel embeddingModel;

    public void save(
            UUID exchangeId,
            String conversationId,
            String question,
            String answer
    ) {
        // Save the original text first, even if embedding later fails.
        log.info("Saving conversation {}", conversationId);
        int inserted = jdbcTemplate.update(
                """
                        INSERT INTO conversation_exchange (
                            id, conversation_id, question, answer
                        )
                        VALUES (?, ?, ?, ?)
                        ON CONFLICT (id) DO NOTHING
                        """,
                exchangeId,
                conversationId,
                question,
                answer
        );

        if (inserted == 0) {
            return;
        }

        try {
            String text = """
                    Customer question:
                    %s
                    
                    Assistant answer:
                    %s
                    """.formatted(question, answer);

            String vector = toVector(embeddingModel.embed(text));

            jdbcTemplate.update(
                    """
                            UPDATE conversation_exchange
                            SET embedding = CAST(? AS vector)
                            WHERE id = ?
                            """,
                    vector,
                    exchangeId
            );
        } catch (RuntimeException exception) {
            // The text remains archived but is not semantically searchable
            // until an embedding retry/backfill succeeds.
            log.warn(
                    "HISTORY_EMBEDDING_FAILED exchangeId={} exceptionType={}",
                    exchangeId,
                    exception.getClass().getSimpleName()
            );
        }
    }

    public HistoryResult searchOlder(
            String conversationId,
            String question,
            double minimumScore
    ) {
        // Avoid an embedding request when there is no older history.
        Long count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM conversation_exchange
                        WHERE conversation_id = ?
                        """,
                Long.class,
                conversationId
        );

        if (count == null || count <= RECENT_EXCHANGES) {
            return new HistoryResult(0, List.of(), "");
        }

        String queryVector = toVector(embeddingModel.embed(question));

        List<HistoryMatch> candidates = jdbcTemplate.query(
                """
                        WITH recent AS (
                            SELECT id
                            FROM conversation_exchange
                            WHERE conversation_id = ?
                            ORDER BY created_at DESC, id DESC
                            LIMIT ?
                        )
                        SELECT
                            e.id,
                            e.question,
                            e.answer,
                            e.created_at,
                            1 - (
                                e.embedding <=> CAST(? AS vector)
                            ) AS similarity
                        FROM conversation_exchange e
                        WHERE e.conversation_id = ?
                          AND e.embedding IS NOT NULL
                          AND NOT EXISTS (
                              SELECT 1
                              FROM recent r
                              WHERE r.id = e.id
                          )
                        ORDER BY
                            e.embedding <=> CAST(? AS vector),
                            e.created_at DESC,
                            e.id
                        LIMIT ?
                        """,
                (rs, rowNum) -> new HistoryMatch(
                        rs.getObject("id", UUID.class),
                        rs.getString("question"),
                        rs.getString("answer"),
                        rs.getObject(
                                "created_at",
                                OffsetDateTime.class
                        ),
                        rs.getDouble("similarity")
                ),
                conversationId,
                RECENT_EXCHANGES,
                queryVector,
                conversationId,
                queryVector,
                CANDIDATE_LIMIT
        );

        List<HistoryMatch> selected = new ArrayList<>();
        int characters = 0;

        // Candidates arrive in relevance order.
        for (HistoryMatch candidate : candidates) {
            if (candidate.similarity() < minimumScore) {
                continue;
            }

            String formatted = formatExchange(candidate);
            int size = formatted.codePointCount(
                    0,
                    formatted.length()
            );

            // Keep complete exchanges rather than cutting a sentence in half.
            if (characters + size > MAX_HISTORY_CHARACTERS) {
                continue;
            }

            selected.add(candidate);
            characters += size;

            if (selected.size() == SELECTED_LIMIT) {
                break;
            }
        }

        // Present the selected exchanges in chronological order.
        selected.sort(
                Comparator.comparing(HistoryMatch::createdAt)
                        .thenComparing(HistoryMatch::id)
        );

        StringBuilder context = new StringBuilder();

        for (HistoryMatch match : selected) {
            context.append(formatExchange(match));
        }

        return new HistoryResult(
                candidates.size(),
                List.copyOf(selected),
                context.toString()
        );
    }

    private static String formatExchange(HistoryMatch match) {
        return """
                [Historical exchange]
                Exchange ID: %s
                Timestamp: %s
                Customer question: %s
                Previous assistant answer: %s
                
                """.formatted(
                match.id(),
                match.createdAt(),
                match.question(),
                match.answer()
        );
    }

    private static String toVector(float[] values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException(
                    "Embedding model returned an empty vector."
            );
        }

        StringBuilder result = new StringBuilder("[");

        for (int i = 0; i < values.length; i++) {
            if (!Float.isFinite(values[i])) {
                throw new IllegalArgumentException(
                        "Embedding contains an invalid value."
                );
            }

            if (i > 0) {
                result.append(',');
            }

            result.append(values[i]);
        }

        return result.append(']').toString();
    }

    public record HistoryMatch(
            UUID id,
            String question,
            String answer,
            OffsetDateTime createdAt,
            double similarity
    ) {
    }

    public record HistoryResult(
            int candidateCount,
            List<HistoryMatch> matches,
            String context
    ) {
    }
}
