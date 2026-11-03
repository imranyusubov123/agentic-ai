package az.ingress.agentic.ai.training.services.supervisor;

import az.ingress.agentic.ai.config.dto.BankingIntent;
import az.ingress.agentic.ai.training.domains.account.AccountsAgent;
import az.ingress.agentic.ai.training.domains.cards.CardsAgent;
import az.ingress.agentic.ai.training.domains.fees.FeesAgent;
import az.ingress.agentic.ai.training.domains.payments.PaymentsAgent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class BankingWorkflowNodes {

    private final AccountsAgent accountsAgent;
    private final CardsAgent cardsAgent;
    private final PaymentsAgent paymentsAgent;
    private final FeesAgent feesAgent;

    private final WorkflowObservationService observationService;

    /**
     * Executes an investigation agent.
     * Only findings from declared dependencies are supplied.
     */
    public Map<String, Object> investigate(
            BankingIntent agent,
            BankingWorkflowState state,
            Map<String, String> dependencyFindings) {

        validateInvestigationAgent(agent);

        return observationService.observe(
                agent.name().toLowerCase(Locale.ROOT),
                () -> {
                    String prompt = buildPrompt(
                            agent,
                            state.question(),
                            dependencyFindings
                    );

                    String agentConversationId =
                            state.conversationId() + ":" + agent.name();

                    Flux<String> response = switch (agent) {
                        case ACCOUNTS -> accountsAgent.chat(
                                prompt,
                                agentConversationId
                        );

                        case CARDS -> cardsAgent.chat(
                                prompt,
                                agentConversationId
                        );

                        case PAYMENTS -> paymentsAgent.chat(
                                prompt,
                                agentConversationId
                        );

                        case FEES -> feesAgent.chat(
                                prompt,
                                agentConversationId
                        );

                        case GENERAL, UNKNOWN -> throw unsupportedAgent(agent);
                    };

                    String result = response
                            .collect(
                                    StringBuilder::new,
                                    (builder, chunk) -> builder.append(chunk)
                            )
                            .map(StringBuilder::toString)
                            .block();

                    if (result == null || result.isBlank()) {
                        throw new IllegalStateException(
                                "Empty investigation response from " + agent
                        );
                    }

                    return Map.<String, Object>of(
                            resultKey(agent),
                            result
                    );
                }
        );
    }

    private String buildPrompt(
            BankingIntent agent,
            String question,
            Map<String, String> dependencyFindings) {

        String instructions = switch (agent) {
            case ACCOUNTS -> """
                    Investigate account debits, credits,
                    balances, and relevant ledger entries.
                    
                    Report only verified information.
                    Never infer payment settlement from a debit.
                    """;

            case CARDS -> """
                    Investigate card status and relevant card events.
                    Do not block or modify cards.
                    Report only verified information.
                    """;

            case PAYMENTS -> """
                    Investigate transfer status and processing events.
                    
                    Distinguish PENDING, SETTLED, FAILED, and UNKNOWN.
                    Never assume a timeout means payment failure.
                    """;

            case FEES -> """
                    Investigate banking fees and commissions.
                    
                    Verify applicable fee policies.
                    Do not assume that a disputed fee is incorrect.
                    Do not initiate refunds.
                    """;

            case GENERAL, UNKNOWN -> throw unsupportedAgent(agent);
        };

        String findings = dependencyFindings.entrySet()
                .stream()
                .map(entry ->
                        entry.getKey() + ":\n" + entry.getValue())
                .collect(Collectors.joining("\n\n"));

        return """
                %s
                
                Customer request:
                %s
                
                Findings from completed dependencies:
                %s
                """.formatted(
                instructions,
                question,
                findings.isBlank()
                        ? "No dependency findings."
                        : findings
        );
    }

    public String resultKey(BankingIntent agent) {
        validateInvestigationAgent(agent);

        return switch (agent) {
            case ACCOUNTS -> BankingWorkflowState.ACCOUNT_RESULT;
            case CARDS -> BankingWorkflowState.CARD_RESULT;
            case PAYMENTS -> BankingWorkflowState.PAYMENT_RESULT;
            case FEES -> BankingWorkflowState.FEES_RESULT;

            case GENERAL, UNKNOWN -> throw unsupportedAgent(agent);
        };
    }

    /**
     * Aggregates findings from the agents that executed.
     */
    public Map<String, Object> aggregate(BankingWorkflowState state) {
        return observationService.observe(
                "aggregate",
                () -> {
                    StringBuilder response =
                            new StringBuilder("Banking investigation:\n\n");

                    state.accountResult().ifPresent(result ->
                            response.append("Account findings:\n")
                                    .append(result)
                                    .append("\n\n"));

                    state.paymentResult().ifPresent(result ->
                            response.append("Payment findings:\n")
                                    .append(result)
                                    .append("\n\n"));

                    state.feesResult().ifPresent(result ->
                            response.append("Fee findings:\n")
                                    .append(result)
                                    .append("\n\n"));

                    state.cardResult().ifPresent(result ->
                            response.append("Card findings:\n")
                                    .append(result)
                                    .append("\n\n"));

                    response.append(
                            "These are investigation findings."
                    );

                    return Map.<String, Object>of(
                            BankingWorkflowState.FINAL_RESPONSE,
                            response.toString()
                    );
                }
        );
    }

    private void validateInvestigationAgent(BankingIntent agent) {
        if (agent == null) {
            throw new IllegalArgumentException(
                    "Investigation agent is required"
            );
        }

        switch (agent) {
            case ACCOUNTS, CARDS, PAYMENTS, FEES -> {
                // Supported investigation domains.
            }

            case GENERAL, UNKNOWN -> throw unsupportedAgent(agent);
        }
    }

    private IllegalArgumentException unsupportedAgent(
            BankingIntent agent) {

        return new IllegalArgumentException(
                "Intent is not an investigation domain: " + agent
        );
    }
}