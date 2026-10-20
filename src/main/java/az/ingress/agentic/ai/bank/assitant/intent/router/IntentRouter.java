package az.ingress.agentic.ai.bank.assitant.intent.router;

import az.ingress.agentic.ai.bank.assitant.intent.router.dto.RoutingDecision;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class IntentRouter {

    private final ChatClient chatClient;

    public IntentRouter(
            @Qualifier("intentChatClient") ChatClient chatClient) {
        this.chatClient = chatClient;
    }


    public RoutingDecision route(String message) {

        return chatClient.prompt()
                .system("""
                        You are an intent classifier for a banking assistant.
                        
                        Your responsibility is to classify customer requests.
                        
                        Available domains:
                        GENERAL, CARDS, ACCOUNTS, PAYMENTS,
                        LOANS, DEPOSITS, SUPPORT,
                        MULTI_DOMAIN, UNKNOWN.
                        
                        Task types:
                        QUESTION, INVESTIGATION, OPERATION,
                        MIXED, UNKNOWN.
                        
                        Rules:
                        1. Identify the primary banking domain.
                        2. Identify the customer's intent.
                        3. Determine the task type.
                        4. Use MULTI_DOMAIN when multiple banking
                           domains are required.
                        5. Use UNKNOWN when the request is unclear.
                        6. Never execute operations.
                        7. Never answer the customer's question.
                        8. Return only the structured classification.
                        
                        Confidence must be between 0 and 1.
                        """)
                .user(message)
                .call()
                .entity(RoutingDecision.class);
    }
}