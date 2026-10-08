package az.ingress.agentic.ai.training;

import az.ingress.agentic.ai.training.context.AiContext;
import az.ingress.agentic.ai.training.context.AiContextBuilder;
import az.ingress.agentic.ai.training.dto.SupportResponse;
import az.ingress.agentic.ai.training.tools.BankingTools;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class BankAssistant {

    private final ChatClient chatClient;
    private final BankingTools bankingTools;
    private final AiContextBuilder contextBuilder;

    @Value("classpath:/prompts/support-user.st")
    private Resource userPrompt;

    @GetMapping("/bank")
    public SupportResponse chat(
            @RequestParam String message,
            @RequestParam String conversationId,
            @RequestParam(defaultValue = "English") String language,
            @RequestParam(defaultValue = "Web") String channel) {

        AiContext context = contextBuilder.build(
                message,
                conversationId,
                language,
                channel
        );

        return chatClient.prompt()
                .user(user -> user
                        .text(userPrompt)
                        .param("message", message)
                        .param("language", context.language())
                        .param("channel", context.channel())
                        .param("context", context.knowledgeContext()))
                .advisors(advisor -> advisor
                        .param(
                                ChatMemory.CONVERSATION_ID,
                                context.conversationId()
                        ))
                .tools(bankingTools)
                .call()
                .entity(SupportResponse.class);
    }
}