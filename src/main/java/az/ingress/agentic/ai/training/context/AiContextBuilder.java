package az.ingress.agentic.ai.training.context;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AiContextBuilder {

    private final VectorStore vectorStore;
    private final ContextPlanner contextPlanner;

    public AiContext build(
            String message,
            String conversationId,
            String language,
            String channel) {

        ContextPlan plan = contextPlanner.plan(message);

        String knowledgeContext = "";

        if (plan.useRag()) {

            List<Document> documents = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(message)
                            .topK(3)
                            .similarityThreshold(0.75)
                            .build()
            );

            knowledgeContext = documents.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining("\n\n"));
        }

        return new AiContext(
                conversationId,
                language,
                channel,
                knowledgeContext,
                plan
        );
    }
}