package az.ingress.agentic.ai.training.domains.cards;

import az.ingress.agentic.ai.training.services.langfuse.LangfuseAgentAdvisor;
import az.ingress.agentic.ai.training.services.langfuse.ReloadableSystemPrompt;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class CardsAgent {

    private final ChatClient chatClient;
    private final CardsTools cardsTools;
    private final ReloadableSystemPrompt systemPrompt;

    public CardsAgent(
            @Qualifier("bankingChatClient") ChatClient chatClient,
            CardsTools cardsTools,
            ReloadableSystemPrompt systemPrompt) {

        this.chatClient = chatClient;
        this.cardsTools = cardsTools;
        this.systemPrompt = systemPrompt;
    }

    public String chat(String message, String conversationId) {
        return chat(message, conversationId, conversationId);
    }

    public String chat(
            String message,
            String memoryConversationId,
            String sessionId) {

        String instructions = systemPrompt.get() + """

                
                CARD SUPPORT:
                - Answer card-related questions.
                - Retrieve card status using authorized tools.
                - Never invent card information.
                - Never claim a card has been blocked
                  unless the banking API confirms it.
                - If the customer wants to block a card,
                  explain that confirmation is required.
                - Do not execute card blocking without
                  the required customer confirmation.

                Return the final customer-facing answer in natural language.
                Do not output planning notes or action objects.
                """;

        return chatClient.prompt()
                .system(instructions)
                .user(message)
                .tools(cardsTools)
                .advisors(a -> a
                        .param(
                                ChatMemory.CONVERSATION_ID,
                                memoryConversationId
                        )
                        .param(
                                LangfuseAgentAdvisor.AGENT_NAME,
                                "cards-agent"
                        )
                        .param(
                                LangfuseAgentAdvisor.SESSION_ID,
                                sessionId
                        )
                )
                .call()
                .content();
    }
}