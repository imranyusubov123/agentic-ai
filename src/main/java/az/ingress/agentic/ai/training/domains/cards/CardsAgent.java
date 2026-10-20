package az.ingress.agentic.ai.training.domains.cards;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CardsAgent {

    private final ChatClient chatClient;
    private final CardsTools cardsTools;

    public String chat(String message, String conversationId) {

        return chatClient.prompt()
                .system("""
                        You are a Card Management Banking Agent.
                        
                        Responsibilities:
                        - Answer card-related questions.
                        - Retrieve card status using tools.
                        - Never invent card information.
                        - Never claim a card has been blocked
                          unless the banking API confirms it.
                        - If the customer wants to block a card,
                          explain that confirmation is required.
                        """)
                .user(message)
                .tools(cardsTools)
                .advisors(advisor -> advisor.param(
                        ChatMemory.CONVERSATION_ID,
                        conversationId
                ))
                .call()
                .content();
    }
}