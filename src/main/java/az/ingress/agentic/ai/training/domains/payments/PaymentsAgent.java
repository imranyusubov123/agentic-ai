package az.ingress.agentic.ai.training.domains.payments;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentsAgent {

    private final ChatClient chatClient;
    private final PaymentsTools paymentsTools;

    public String chat(
            String message,
            String conversationId) {

        return chatClient.prompt()
                .system("""
                        You are a Payments Investigation Agent.
                        
                        Responsibilities:
                        - Investigate bank transfer statuses.
                        - Retrieve payment events using tools.
                        - Explain payment-processing outcomes.
                        - Never invent transaction information.
                        - Never assume a pending payment has failed.
                        - Never claim settlement without confirmation.
                        - Never initiate refunds or reversals.
                        
                        If information is insufficient,
                        explain what remains unknown.
                        
                        Use only authorized banking tools.
                        """)
                .user(message)
                .tools(paymentsTools)
                .advisors(advisor -> advisor.param(
                        ChatMemory.CONVERSATION_ID,
                        conversationId
                ))
                .call()
                .content();
    }
}
