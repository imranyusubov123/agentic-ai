package az.ingress.agentic.ai.training.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class HybridSearchService {
    private static final int RRF_CONSTANT = 60;
    private static final TypeReference<Map<String, Object>> METADATA_TYPE = new TypeReference<>() {
    };
    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;
    private final JsonMapper jsonMapper;

    public HybridSearchService(VectorStore vectorStore, JdbcTemplate jdbcTemplate, JsonMapper jsonMapper) {
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
        this.jsonMapper = jsonMapper;
    }

    public HybridResponse search(HybridInput input) {
        if (input == null || input.question() == null || input.question().isBlank()) {
            throw badRequest("question is required.");
        }
        String question = input.question().strip();
        String ingestionId = input.ingestionId() == null || input.ingestionId().isBlank()
                ? null : input.ingestionId().strip();
        int topK = input.topK() == null ? 3 : input.topK();
        int candidateK = input.candidateK() == null ? 20 : input.candidateK();
        if (topK < 1 || topK > 20) throw badRequest("topK must be between 1 and 20.");
        if (candidateK < topK || candidateK > 100) {
            throw badRequest("candidateK must be >= topK and <= 100.");
        }

        long started = System.nanoTime();
        List<Document> vectors = vectorSearch(question, ingestionId, candidateK);
        List<KeywordHit> keywords = keywordSearch(question, ingestionId, candidateK);
        Map<String, Candidate> candidates = new LinkedHashMap<>();
        for (int i = 0; i < vectors.size(); i++) {
            Document document = vectors.get(i);
            Candidate candidate = candidates.computeIfAbsent(document.getId(),
                    id -> new Candidate(id, document.getText(), document.getMetadata()));
            candidate.vectorRank = i + 1;
            candidate.vectorSimilarity = document.getScore();
        }
        for (int i = 0; i < keywords.size(); i++) {
            KeywordHit hit = keywords.get(i);
            Candidate candidate = candidates.computeIfAbsent(hit.id(),
                    id -> new Candidate(id, hit.text(), hit.metadata()));
            candidate.keywordRank = i + 1;
            candidate.keywordScore = hit.keywordScore();
        }
        List<HybridHit> matches = candidates.values().stream()
                .map(c -> new HybridHit(c.id, rrf(c.vectorRank) + rrf(c.keywordRank),
                        c.vectorRank, c.keywordRank, c.vectorSimilarity, c.keywordScore, c.text, c.metadata))
                .sorted(Comparator.comparingDouble(HybridHit::hybridScore).reversed()
                        .thenComparing(HybridHit::keywordRank, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(HybridHit::id))
                .limit(topK)
                .toList();
        log.info("Hybrid retrieval completed vectorCandidates={} keywordCandidates={} uniqueCandidates={} returned={} durationMs={}",
                vectors.size(), keywords.size(), candidates.size(), matches.size(),
                (System.nanoTime() - started) / 1_000_000);
        return new HybridResponse(question, ingestionId, topK, candidateK, vectors.size(), keywords.size(), matches);
    }

    private List<Document> vectorSearch(String question, String ingestionId, int candidateK) {
        var builder = SearchRequest.builder().query(question).topK(candidateK).similarityThreshold(0.0);
        if (ingestionId != null) {
            FilterExpressionBuilder filter = new FilterExpressionBuilder();
            builder.filterExpression(filter.eq("ingestion_id", ingestionId).build());
        }
        List<Document> results = vectorStore.similaritySearch(builder.build());
        return results == null ? List.of() : results;
    }

    private List<KeywordHit> keywordSearch(String question, String ingestionId, int candidateK) {
        String scope = ingestionId == null ? "" : " AND v.metadata ->> 'ingestion_id' = ? ";
        String sql = """
                SELECT v.id, v.content, v.metadata::text AS metadata_json,
                    ts_rank_cd(
                        to_tsvector('simple', COALESCE(v.content, '')),
                        plainto_tsquery('simple', ?)
                    ) AS keyword_score
                FROM public.vector_store v
                WHERE to_tsvector('simple', COALESCE(v.content, ''))
                    @@ plainto_tsquery('simple', ?)
                """ + scope + " ORDER BY keyword_score DESC, v.id LIMIT ?";
        List<Object> parameters = new ArrayList<>();
        parameters.add(question);
        parameters.add(question);
        if (ingestionId != null) parameters.add(ingestionId);
        parameters.add(candidateK);
        return jdbcTemplate.query(sql, (rs, row) -> new KeywordHit(
                rs.getString("id"), rs.getString("content"),
                readMetadata(rs.getString("metadata_json")), rs.getDouble("keyword_score")
        ), parameters.toArray());
    }

    private Map<String, Object> readMetadata(String json) {
        if (json == null || json.isBlank()) return Map.of();
        Map<String, Object> metadata = jsonMapper.readValue(json, METADATA_TYPE);
        return metadata == null ? Map.of() : metadata;
    }

    private static double rrf(Integer rank) {
        return rank == null ? 0.0 : 1.0 / (RRF_CONSTANT + rank);
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static final class Candidate {
        final String id;
        final String text;
        final Map<String, Object> metadata;
        Integer vectorRank;
        Integer keywordRank;
        Double vectorSimilarity;
        Double keywordScore;

        Candidate(String id, String text, Map<String, Object> metadata) {
            this.id = id;
            this.text = text;
            this.metadata = metadata;
        }
    }

    private record KeywordHit(String id, String text, Map<String, Object> metadata, double keywordScore) {
    }

    public record HybridInput(String question, String ingestionId, Integer topK, Integer candidateK) {
    }

    public record HybridHit(String id, double hybridScore, Integer vectorRank, Integer keywordRank,
                            Double vectorSimilarity, Double keywordScore, String text,
                            Map<String, Object> metadata) {
    }

    public record HybridResponse(String question, String ingestionId, int topK, int candidateK,
                                 int vectorCandidateCount, int keywordCandidateCount, List<HybridHit> matches) {
    }
}
