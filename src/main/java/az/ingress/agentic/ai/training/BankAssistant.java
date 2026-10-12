package az.ingress.agentic.ai.training;

import az.ingress.agentic.ai.training.context.AiContext;
import az.ingress.agentic.ai.training.context.AiContextBuilder;
import az.ingress.agentic.ai.training.dto.SupportResponse;
import az.ingress.agentic.ai.training.services.HybridSearchService;
import az.ingress.agentic.ai.training.tools.BankingTools;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class BankAssistant {
    private final ChatClient chatClient;
    private final BankingTools bankingTools;
    private final AiContextBuilder contextBuilder;
    private final HybridSearchService hybridSearchService;

    @Value("classpath:/prompts/support-user.st")
    private Resource userPrompt;

    @GetMapping("/bank")
    public SupportResponse chat(
            @RequestParam("message") String message,
            @RequestParam("conversationId") String conversationId,
            @RequestParam(value = "ingestionId", required = false) String ingestionId,
            @RequestParam(value = "language", defaultValue = "English") String language,
            @RequestParam(value = "channel", defaultValue = "Web") String channel) {
        if (message.isBlank() || conversationId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "message and conversationId must not be blank.");
        }

        AiContext context = contextBuilder.build(message, conversationId, language, channel);
        var retrieval = hybridSearchService.search(
                new HybridSearchService.HybridInput(message, ingestionId, 3, 20));

        // Preserve existing non-retrieval context. If contextBuilder already performs
        // vector retrieval, remove that old retrieval there to avoid two pipelines.
        String existingContext = context.knowledgeContext() == null ? "" : context.knowledgeContext();
        String evidence = formatEvidence(retrieval.matches());
        String combinedContext = "Existing application context:\n" + existingContext
                + "\n\nRetrieved document evidence for this request:\n" + evidence;

        return chatClient.prompt()
                .user(user -> user.text(userPrompt)
                        .param("message", message)
                        .param("language", context.language())
                        .param("channel", context.channel())
                        .param("context", combinedContext))
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, context.conversationId()))
                .tools(bankingTools)
                .call()
                .entity(SupportResponse.class);
    }

    private static String formatEvidence(List<HybridSearchService.HybridHit> hits) {
        if (hits.isEmpty()) return "No document passages were retrieved for this request.";
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < hits.size(); i++) {
            var hit = hits.get(i);
            Map<String, Object> metadata = hit.metadata() == null ? Map.of() : hit.metadata();
            result.append("[S").append(i + 1).append("]\n")
                    .append("Chunk ID: ").append(hit.id()).append('\n')
                    .append("Source: ").append(metadata.getOrDefault("source_file", "Unknown")).append('\n')
                    .append("Pages: ").append(metadata.getOrDefault("page_numbers", List.of())).append('\n')
                    .append("Headings: ").append(metadata.getOrDefault("headings", List.of())).append('\n')
                    .append("Passage:\n").append(hit.text()).append("\n\n");
        }
        return result.toString();
    }
}
