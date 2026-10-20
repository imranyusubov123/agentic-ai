package az.ingress.agentic.ai.training.services;

import az.ingress.agentic.ai.config.dto.BankingIntent;
import az.ingress.agentic.ai.config.dto.IntentResult;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class IntentDetector {

    private final ChatClient intentChatClient;
    private final ObservationRegistry observationRegistry;

    public IntentDetector(
            @Qualifier("intentChatClient") ChatClient intentChatClient,
            ObservationRegistry observationRegistry) {

        this.intentChatClient = intentChatClient;
        this.observationRegistry = observationRegistry;
    }

    public IntentResult detect(String message) {

        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException(
                    "Customer message must not be blank"
            );
        }

        Observation observation = Observation.createNotStarted(
                        "banking.intent.detect",
                        observationRegistry
                )
                .lowCardinalityKeyValue("model.provider", "ollama")
                .lowCardinalityKeyValue("operation", "intent_detection");

        return observation.observe(() -> {

            IntentResult result = classify(message);

            observation.highCardinalityKeyValue(
                    "banking.detected.intents",
                    result.intents().toString()
            );

            observation.highCardinalityKeyValue(
                    "banking.requires.clarification",
                    String.valueOf(result.requiresClarification())
            );

            return result;
        });
    }

    private IntentResult classify(String message) {

        IntentResult result = intentChatClient.prompt()
                .system("""
                        You are an intent classifier for a banking assistant.
                        
                        Classify the customer request into banking domains.
                        
                        DOMAINS:
                        
                        ACCOUNTS:
                        Account balances, account details, debits,
                        credits, account statements and ledger entries.
                        
                        CARDS:
                        Card status, lost or stolen cards,
                        card blocking and card management.
                        
                        PAYMENTS:
                        Bank transfers, payment processing,
                        transaction status, settlement,
                        failed payments and reversals.
                        
                        FEES:
                        Banking charges, commissions,
                        transaction fees, pricing policies
                        and disputed fees.
                        
                        UNKNOWN:
                        Unsupported or unclear banking requests.
                        
                        RULES:
                        - Identify ALL relevant banking domains.
                        - Multiple domains may be returned.
                        - If the customer requests verification
                          of an account debit, include ACCOUNTS.
                        - If the customer asks about a transfer
                          or payment status, include PAYMENTS.
                        - If the customer disputes a fee
                          or commission, include FEES.
                        - Use UNKNOWN for unsupported requests.
                        - Set requiresClarification=true
                          when the request is ambiguous.
                        - Do not answer the customer.
                        - Do not invent additional domains.
                        - Return only the structured classification.
                        """)
                .user(message)
                .call()
                .entity(IntentResult.class);

        if (result == null
                || result.intents() == null
                || result.intents().isEmpty()) {

            log.warn("INTENT_DETECTION_INVALID_RESULT");

            return fallback();
        }

        List<BankingIntent> intents = result.intents()
                .stream()
                .filter(intent -> intent != null)
                .distinct()
                .toList();

        if (intents.isEmpty() || intents.contains(BankingIntent.UNKNOWN)
                && intents.size() > 1) {

            log.warn("INTENT_DETECTION_AMBIGUOUS_RESULT");

            return fallback();
        }

        IntentResult validatedResult = new IntentResult(
                intents,
                result.requiresClarification()
        );

        log.info(
                "INTENT_DETECTED intents={} clarification={}",
                validatedResult.intents(),
                validatedResult.requiresClarification()
        );

        return validatedResult;
    }

    private IntentResult fallback() {
        return new IntentResult(
                List.of(BankingIntent.UNKNOWN),
                true
        );
    }
}