package az.ingress.agentic.ai.training.demo.service;

import az.ingress.agentic.ai.config.dto.IntentResult;
import az.ingress.agentic.ai.training.services.AgentRouter;
import az.ingress.agentic.ai.training.services.IntentDetector;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BankingConversationOrchestrator {

    private final IntentDetector intentDetector;
    private final AgentRouter agentRouter;
    private final TransferInvestigationService investigationService;
    private final TransferSelectionResolver selectionResolver;

    public String chat(String message, String conversationId) {

        // 1. Resume an existing investigation before classifying
        // the customer's message as a new request.
        var pending = investigationService
                .findPending(conversationId);

        if (pending.isPresent()) {

            var investigation = pending.get();

            var transactionId = selectionResolver.resolve(
                    message,
                    investigation.candidates()
            );

            if (transactionId.isEmpty()) {
                return """
                        I couldn't identify which transfer you mean.
                        Please select a transaction from the list:
                        
                        %s
                        """.formatted(
                        selectionResolver.formatCandidates(
                                investigation.candidates()
                        )
                );
            }

            var response = investigationService.select(
                    investigation.investigationId(),
                    conversationId,
                    transactionId.get()
            );

            return response.message();
        }

        // 2. Classify a new customer request.
        IntentResult intent = intentDetector.detect(message, conversationId);

        // 3. Start the specialized fee investigation when appropriate.
        if (isTransferFeeInvestigation(intent, message)) {

            var response = investigationService.start(
                    conversationId,
                    message
            );

            return response.message() + "\n\n"
                    + selectionResolver.formatCandidates(
                    response.candidates()
            );
        }

        // 4. Route ordinary requests through the existing architecture.
        return agentRouter.route(
                intent,
                message,
                conversationId
        );
    }

    private boolean isTransferFeeInvestigation(
            IntentResult intent,
            String message) {

        if (intent == null || intent.intents() == null) {
            return false;
        }

        String normalized = message.toLowerCase(
                java.util.Locale.ROOT
        );

        return intent.intents().contains(
                az.ingress.agentic.ai.config.dto.BankingIntent.FEES
        ) && (
                normalized.contains("transfer")
                        || normalized.contains("köçürmə")
        );
    }
}