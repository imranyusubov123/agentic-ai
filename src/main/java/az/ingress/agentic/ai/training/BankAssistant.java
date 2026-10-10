package az.ingress.agentic.ai.training;

import az.ingress.agentic.ai.training.context.AiContext;
import az.ingress.agentic.ai.training.context.AiContextBuilder;
import az.ingress.agentic.ai.training.dto.SupportResponse;
import az.ingress.agentic.ai.training.knowledge_management.KnowledgeRetrievalService;
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

@RestController
@RequiredArgsConstructor
public class BankAssistant {

    private final ChatClient chatClient;
    private final BankingTools bankingTools;
    private final AiContextBuilder contextBuilder;
    private final KnowledgeRetrievalService knowledgeRetrievalService;

    @Value("classpath:/prompts/support-user.st")
    private Resource userPrompt;

    @GetMapping("/bank")
    public SupportResponse chat(
            @RequestParam("message") String message,
            @RequestParam("conversationId") String conversationId,
            @RequestParam(value = "ingestionId", required = false)
            String ingestionId,
            @RequestParam(value = "language", defaultValue = "English")
            String language,
            @RequestParam(value = "channel", defaultValue = "Web")
            String channel
    ) {
        requireNonBlank(message, "message");
        requireNonBlank(conversationId, "conversationId");

        AiContext context = contextBuilder.build(
                message,
                conversationId,
                language,
                channel
        );

        String retrievedPassages = knowledgeRetrievalService.retrieve(
                message.strip(),
                ingestionId
        );

        String existingContext = context.knowledgeContext() == null
                ? ""
                : context.knowledgeContext();

        String combinedContext = """
                EXISTING APPLICATION CONTEXT:
                %s

                RETRIEVED DOCUMENT PASSAGES:
                %s
                """.formatted(existingContext, retrievedPassages);

        return chatClient.prompt()
                .user(user -> user
                        .text(userPrompt)
                        .param("message", message)
                        .param("language", context.language())
                        .param("channel", context.channel())
                        .param("context", combinedContext))
                .advisors(advisor -> advisor
                        .param(
                                ChatMemory.CONVERSATION_ID,
                                context.conversationId()
                        ))
                .tools(bankingTools)
                .call()
                .entity(SupportResponse.class);
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    name + " is required."
            );
        }
    }
}