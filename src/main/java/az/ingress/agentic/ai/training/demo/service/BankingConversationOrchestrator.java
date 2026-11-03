package az.ingress.agentic.ai.training.demo.service;

import az.ingress.agentic.ai.config.dto.BankingIntent;
import az.ingress.agentic.ai.config.dto.IntentResult;
import az.ingress.agentic.ai.training.services.AgentRouter;
import az.ingress.agentic.ai.training.services.IntentDetector;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class BankingConversationOrchestrator {

    private final IntentDetector intentDetector;
    private final AgentRouter agentRouter;
    private final TransferInvestigationService investigationService;
    private final TransferSelectionResolver selectionResolver;

    public Flux<String> chat(String message, String conversationId) {
        return Flux.<String>defer(() -> {
            validate(message, conversationId);

            // Resume an existing investigation.
            var pending = investigationService.findPending(conversationId);

            if (pending.isPresent()) {
                var investigation = pending.get();

                var transactionId = selectionResolver.resolve(
                        message,
                        investigation.candidates()
                );

                if (transactionId.isEmpty()) {
                    String clarification = """
                            I couldn't identify which transfer you mean.
                            Please select a transaction from the list:
                            
                            %s
                            """.formatted(
                            selectionResolver.formatCandidates(
                                    investigation.candidates()
                            )
                    );

                    return Flux.just(clarification);
                }

                var response = investigationService.select(
                        investigation.investigationId(),
                        conversationId,
                        transactionId.get()
                );

                return Flux.just(response.message());
            }

            // Classification remains synchronous on the worker thread.
            IntentResult intent = intentDetector.detect(
                    message,
                    conversationId
            );

            // Start a specialized transfer-fee investigation.
            if (isTransferFeeInvestigation(intent, message)) {
                var response = investigationService.start(
                        conversationId,
                        message
                );

                String answer = response.message() + "\n\n"
                        + selectionResolver.formatCandidates(
                        response.candidates()
                );

                return Flux.just(answer);
            }

            // Forward the agent's stream without collecting it.
            return agentRouter.route(
                    intent,
                    message,
                    conversationId
            );
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private void validate(String message, String conversationId) {
        if (message == null || message.isBlank()
                || conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException(
                    "Message and conversationId are required"
            );
        }
    }

    private boolean isTransferFeeInvestigation(
            IntentResult intent,
            String message) {

        if (intent == null || intent.intents() == null) {
            return false;
        }

        String normalized = message.toLowerCase(Locale.ROOT);

        return intent.intents().contains(BankingIntent.FEES)
                && (normalized.contains("transfer")
                || normalized.contains("köçürmə"));
    }
}