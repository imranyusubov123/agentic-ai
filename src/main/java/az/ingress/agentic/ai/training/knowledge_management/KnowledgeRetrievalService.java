package az.ingress.agentic.ai.training.knowledge_management;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class KnowledgeRetrievalService {

    private final VectorStore vectorStore;

    public KnowledgeRetrievalService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public String retrieve(String question, String ingestionId) {
        var request = SearchRequest.builder()
                .query(question)
                .topK(3)
                .similarityThreshold(0.0);

        // Missing, empty, or whitespace-only ID means no ingestion filter.
        if (ingestionId != null && !ingestionId.isBlank()) {
            FilterExpressionBuilder filter = new FilterExpressionBuilder();

            request.filterExpression(
                    filter.eq(
                            "ingestion_id",
                            ingestionId.strip()
                    ).build()
            );
        }

        List<Document> documents =
                vectorStore.similaritySearch(request.build());

        if (documents == null || documents.isEmpty()) {
            return "No document passages were retrieved.";
        }

        StringBuilder context = new StringBuilder();
        int sourceNumber = 0;

        for (Document document : documents) {
            if (document.getText() == null || document.getText().isBlank()) {
                continue;
            }

            sourceNumber++;
            Map<String, Object> metadata = document.getMetadata();

            context.append("SOURCE [")
                    .append(sourceNumber)
                    .append("]\n")
                    .append("Filename: ")
                    .append(metadata.getOrDefault("source_file", "unknown"))
                    .append("\n")
                    .append("Pages: ")
                    .append(metadata.getOrDefault("page_numbers", List.of()))
                    .append("\n")
                    .append("Chunk index: ")
                    .append(metadata.getOrDefault("chunk_index", "unknown"))
                    .append("\n")
                    .append("Passage:\n")
                    .append(document.getText())
                    .append("\nEND SOURCE\n\n");
        }

        return sourceNumber == 0
                ? "No document passages were retrieved."
                : context.toString();
    }
}