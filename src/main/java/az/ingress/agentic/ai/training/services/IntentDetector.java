package az.ingress.agentic.ai.training.services;

import az.ingress.agentic.ai.config.dto.IntentResult;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import static reactor.netty.http.HttpConnectionLiveness.log;

@Service
public class IntentDetector {

    private final ChatClient intentChatClient;

    public IntentDetector(
            @Qualifier("intentChatClient")
            ChatClient intentChatClient) {
        this.intentChatClient = intentChatClient;
    }

    public IntentResult detect(String message) {

        IntentResult result = intentChatClient.prompt()
                .system("""
                        Classify banking customer requests.
                        
                        Domains:
                        ACCOUNTS: Balances and account information.
                        CARDS: Card status, blocking, lost cards.
                        PAYMENTS: Transfers and payment status.
                        FEES: Banking fees and charges.
                        UNKNOWN: Unsupported or unclear requests.
                        
                        Rules:
                        - Identify all relevant domains.
                        - Use UNKNOWN for unsupported requests.
                        - Set requiresClarification=true when ambiguous.
                        - Do not answer the customer.
                        """)
                .user(message)
                .call()
                .entity(IntentResult.class);

        log.info(
                "INTENT_DETECTED intents={} clarification={}",
                result == null ? null : result.intents(),
                result == null ? null : result.requiresClarification()
        );

        return result;
    }
}
