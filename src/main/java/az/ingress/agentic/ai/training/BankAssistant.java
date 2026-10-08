package az.ingress.agentic.ai.training;

import az.ingress.agentic.ai.training.dto.SupportResponse;
import az.ingress.agentic.ai.training.tools.BankingTools;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class BankAssistant {

    private final ChatClient chatClient;
    private final BankingTools bankingTools;
    private final VectorStore vectorStore;

    @Value("classpath:/prompts/support-user.st")
    private Resource userPrompt;

    @GetMapping("/bank")
    public SupportResponse chat(
            @RequestParam String message,
            @RequestParam String conversationId,
            @RequestParam(defaultValue = "English") String language,
            @RequestParam(defaultValue = "Web") String channel) {

        // 1. Retrieve relevant enterprise knowledge
        List<Document> documents = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(message)
                        .topK(3)
                        .build()
        );

        // 2. Build RAG context
        String context = documents.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));

        // 3. Send retrieved knowledge to the LLM
        return chatClient.prompt()
                .user(user -> user
                        .text(userPrompt)
                        .param("message", message)
                        .param("language", language)
                        .param("channel", channel)
                        .param("context", context))
                .advisors(advisor -> advisor
                        .param(
                                ChatMemory.CONVERSATION_ID,
                                conversationId
                        ))
                .tools(bankingTools)
                .call()
                .entity(SupportResponse.class);
    }
}