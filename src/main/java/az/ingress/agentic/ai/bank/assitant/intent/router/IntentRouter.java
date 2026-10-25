package az.ingress.agentic.ai.bank.assitant.intent.router;

import az.ingress.agentic.ai.bank.assitant.intent.router.dto.RoutingDecision;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class IntentRouter {

    private static final String SYSTEM_PROMPT = """
            You are an intent router for an AI banking assistant.

            Your responsibility is to classify customer requests
            and recommend the appropriate processing route.

            AVAILABLE DOMAINS:
            GENERAL, CARDS, ACCOUNTS, PAYMENTS,
            LOANS, DEPOSITS, SUPPORT,
            MULTI_DOMAIN, UNKNOWN.

            TASK TYPES:
            QUESTION, INVESTIGATION, OPERATION,
            MIXED, UNKNOWN.

            CLASSIFICATION RULES:
            1. Identify the customer's intent.
            2. Identify the primary banking domain.
            3. Identify all relevant domains when supported
               by the RoutingDecision schema.
            4. Use MULTI_DOMAIN when the request requires
               coordination across multiple banking domains.
            5. Use UNKNOWN when the request is unclear
               or cannot be classified.
            6. Use QUESTION for information requests.
            7. Use INVESTIGATION when the customer reports
               a problem requiring verification.
            8. Use OPERATION when the customer requests
               a banking action.
            9. Use MIXED when the request combines
               investigation, questions, or operations.
            10. Confidence must be between 0 and 1.

            BANKING EXAMPLES:

            "What is my account balance?"
            Domain: ACCOUNTS
            Task type: QUESTION

            "My debit card was stolen."
            Domain: CARDS
            Task type: OPERATION

            "Why is my transfer pending?"
            Domain: PAYMENTS
            Task type: INVESTIGATION

            "My transfer failed and I was charged a fee."
            Domain: MULTI_DOMAIN
            Task type: INVESTIGATION

            "My transfer failed. Investigate and refund me."
            Domain: MULTI_DOMAIN
            Task type: MIXED

            IMPORTANT:
            - Do not answer the customer.
            - Do not execute banking operations.
            - Do not invent banking facts.
            - Return only structured classification.
            """;

    private final ChatClient chatClient;
    private final ObservationRegistry observationRegistry;

    public IntentRouter(
            @Qualifier("intentChatClient") ChatClient chatClient,
            ObservationRegistry observationRegistry) {

        this.chatClient = chatClient;
        this.observationRegistry = observationRegistry;
    }

    public RoutingDecision route(String message) {

        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException(
                    "Customer message must not be blank"
            );
        }

        Observation observation = Observation.createNotStarted(
                        "banking.intent.route",
                        observationRegistry
                )
                .lowCardinalityKeyValue(
                        "model.provider", "ollama"
                )
                .lowCardinalityKeyValue(
                        "operation", "intent_routing"
                );

        return observation.observe(() -> {

            RoutingDecision decision = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(message)
                    .call()
                    .entity(RoutingDecision.class);

            if (decision == null) {
                throw new IllegalStateException(
                        "Intent router returned no decision"
                );
            }

            observation.highCardinalityKeyValue(
                    "banking.routing.decision",
                    decision.toString()
            );

            log.info(
                    "INTENT_ROUTED decision={}",
                    decision
            );

            return decision;
        });
    }
}