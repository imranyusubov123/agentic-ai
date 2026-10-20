package az.ingress.agentic.ai.training.services;

import az.ingress.agentic.ai.config.dto.IntentResult;
import az.ingress.agentic.ai.training.domains.account.AccountsAgent;
import az.ingress.agentic.ai.training.domains.cards.CardsAgent;
import az.ingress.agentic.ai.training.domains.fees.FeesAgent;
import az.ingress.agentic.ai.training.domains.payments.PaymentsAgent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import static az.ingress.agentic.ai.config.dto.BankingIntent.ACCOUNTS;
import static az.ingress.agentic.ai.config.dto.BankingIntent.CARDS;
import static az.ingress.agentic.ai.config.dto.BankingIntent.FEES;
import static az.ingress.agentic.ai.config.dto.BankingIntent.PAYMENTS;
import static az.ingress.agentic.ai.config.dto.BankingIntent.UNKNOWN;

@Service
@RequiredArgsConstructor
public class AgentRouter {

    private final AccountsAgent accountsAgent;
    private final CardsAgent cardsAgent;
    private final PaymentsAgent paymentsAgent;
    private final FeesAgent feesAgent;

    public String route(
            IntentResult result,
            String message,
            String conversationId) {

        if (result == null
                || result.requiresClarification()
                || result.intents() == null
                || result.intents().isEmpty()) {
            return "Could you clarify your banking request?";
        }

        var intents = result.intents().stream()
                .distinct()
                .toList();

        if (intents.contains(UNKNOWN)) {
            return "Could you clarify which banking service you need?";
        }

        if (intents.size() > 1) {
            return "Your request involves multiple banking domains. "
                    + "Multi-agent coordination will be available soon.";
        }

        return switch (intents.get(0)) {
            case ACCOUNTS -> accountsAgent.chat(message, conversationId);
            case CARDS -> cardsAgent.chat(message, conversationId);
            case PAYMENTS -> paymentsAgent.chat(message, conversationId);
            case FEES -> feesAgent.chat(message, conversationId);
            case UNKNOWN -> "Unsupported banking request.";
        };
    }
}