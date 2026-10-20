package az.ingress.agentic.ai.training.services;

import az.ingress.agentic.ai.config.dto.BankingIntent;
import az.ingress.agentic.ai.config.dto.IntentResult;
import az.ingress.agentic.ai.training.domains.account.AccountsAgent;
import az.ingress.agentic.ai.training.domains.cards.CardsAgent;
import az.ingress.agentic.ai.training.domains.fees.FeesAgent;
import az.ingress.agentic.ai.training.domains.payments.PaymentsAgent;
import az.ingress.agentic.ai.training.services.supervisor.BankingSupervisor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

import static az.ingress.agentic.ai.config.dto.BankingIntent.*;

@Service
@RequiredArgsConstructor
public class AgentRouter {

    private static final Set<BankingIntent> PAYMENT_WORKFLOW =
            Set.of(ACCOUNTS, PAYMENTS);

    private static final Set<BankingIntent> PAYMENT_FEE_WORKFLOW =
            Set.of(ACCOUNTS, PAYMENTS, FEES);

    private final BankingSupervisor bankingSupervisor;

    private final AccountsAgent accountsAgent;
    private final CardsAgent cardsAgent;
    private final PaymentsAgent paymentsAgent;
    private final FeesAgent feesAgent;

    public String route(
            IntentResult result,
            String message,
            String conversationId) {

        if (message == null || message.isBlank()
                || conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException(
                    "Message and conversationId are required");
        }

        if (result == null
                || result.requiresClarification()
                || result.intents() == null
                || result.intents().isEmpty()) {

            return "Could you clarify your banking request?";
        }

        List<BankingIntent> intents = result.intents()
                .stream()
                .distinct()
                .toList();

        if (intents.contains(null) || intents.contains(UNKNOWN)) {
            return "Could you clarify which banking service you need?";
        }

        if (intents.size() > 1) {
            return routeMultiDomain(
                    intents,
                    message,
                    conversationId
            );
        }

        return routeSingleDomain(
                intents.get(0),
                message,
                conversationId
        );
    }

    private String routeMultiDomain(
            List<BankingIntent> intents,
            String message,
            String conversationId) {

        Set<BankingIntent> domains = Set.copyOf(intents);

        if (domains.equals(PAYMENT_WORKFLOW)
                || domains.equals(PAYMENT_FEE_WORKFLOW)) {

            return bankingSupervisor.investigate(
                    message,
                    conversationId
            );
        }

        return """
                Your request involves multiple banking domains.
                This combination is not supported yet.
                """;
    }

    private String routeSingleDomain(
            BankingIntent intent,
            String message,
            String conversationId) {

        return switch (intent) {
            case ACCOUNTS ->
                    accountsAgent.chat(message, conversationId);

            case CARDS ->
                    cardsAgent.chat(message, conversationId);

            case PAYMENTS ->
                    paymentsAgent.chat(message, conversationId);

            case FEES ->
                    feesAgent.chat(message, conversationId);

            case UNKNOWN ->
                    "Unsupported banking request.";
        };
    }
}