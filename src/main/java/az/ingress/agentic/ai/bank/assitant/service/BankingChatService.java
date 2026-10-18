package az.ingress.agentic.ai.bank.assitant.service;

import az.ingress.agentic.ai.training.context.AiContext;
import az.ingress.agentic.ai.training.dto.SupportResponse;
import az.ingress.agentic.ai.training.tools.BankingTools;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BankingChatService {

    private final ChatClient chatClient;
    private final BankingTools bankingTools;

    @Value("classpath:/prompts/support-user.st")
    private Resource userPrompt;

    public ChatExecution chat(
            String message,
            AiContext context,
            String combinedContext) {

        var result = chatClient.prompt()
                .user(user -> user
                        .text(userPrompt)
                        .param("message", message)
                        .param("language", context.language())
                        .param("channel", context.channel())
                        .param("context", combinedContext))
                .advisors(advisor -> advisor.param(
                        ChatMemory.CONVERSATION_ID,
                        context.conversationId()
                ))
                .tools(bankingTools)
                .call()
                .responseEntity(SupportResponse.class);

        return new ChatExecution(
                result.getEntity(),
                result.getResponse()
        );
    }

    public record ChatExecution(
            SupportResponse answer,
            ChatResponse response
    ) {}
}
