package az.ingress.agentic.ai.training.services;

import az.ingress.agentic.ai.config.dto.BankingIntent;
import az.ingress.agentic.ai.config.dto.IntentResult;
import az.ingress.agentic.ai.training.domains.account.AccountsAgent;
import az.ingress.agentic.ai.training.domains.cards.CardsAgent;
import az.ingress.agentic.ai.training.domains.fees.FeesAgent;
import az.ingress.agentic.ai.training.domains.general.GeneralSupportAgent;
import az.ingress.agentic.ai.training.domains.payments.PaymentsAgent;
import az.ingress.agentic.ai.training.services.supervisor.BankingSupervisor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

import static az.ingress.agentic.ai.config.dto.BankingIntent.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRouter {

    private static final Set<BankingIntent> SUPPORTED_INVESTIGATION_DOMAINS =
            Set.of(ACCOUNTS, PAYMENTS, FEES);

    private final BankingSupervisor bankingSupervisor;
    private final AccountsAgent accountsAgent;
    private final CardsAgent cardsAgent;
    private final PaymentsAgent paymentsAgent;
    private final FeesAgent feesAgent;
    private final GeneralSupportAgent generalSupportAgent;

    public String route(
            IntentResult result,
            String message,
            String conversationId) {

        if (message == null || message.isBlank()
                || conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException(
                    "Message and conversationId are required"
            );
        }

        if (result == null
                || result.intents() == null
                || result.intents().isEmpty()) {
            return "Could you clarify your banking request?";
        }

        List<BankingIntent> intents = result.intents()
                .stream()
                .distinct()
                .toList();

        log.debug(
                "Routing intents={} requiresClarification={}",
                intents,
                result.requiresClarification()
        );

        if (intents.contains(null) || intents.contains(UNKNOWN)) {
            return "Could you clarify which banking service you need?";
        }

        // General conversation does not require a specific banking request.
        if (intents.size() == 1 && intents.contains(GENERAL)) {
            return generalSupportAgent.chat(message, conversationId);
        }

        if (result.requiresClarification()) {
            return "Could you clarify your banking request?";
        }

        // Ignore GENERAL when a specific banking domain is also present.
        List<BankingIntent> domainIntents = intents.stream()
                .filter(intent -> intent != GENERAL)
                .toList();

        if (domainIntents.size() > 1) {
            return routeMultiDomain(
                    domainIntents,
                    message,
                    conversationId
            );
        }

        return routeSingleDomain(
                domainIntents.get(0),
                message,
                conversationId
        );
    }

    private String routeMultiDomain(
            List<BankingIntent> intents,
            String message,
            String conversationId) {

        Set<BankingIntent> domains = Set.copyOf(intents);

        if (!SUPPORTED_INVESTIGATION_DOMAINS.containsAll(domains)) {
            return "Unsupported multi-domain request.";
        }

        return bankingSupervisor.investigate(
                message,
                conversationId,
                domains
        );
    }

    private String routeSingleDomain(
            BankingIntent intent,
            String message,
            String conversationId) {

        return switch (intent) {
            case ACCOUNTS -> accountsAgent.chat(
                    message, conversationId);

            case CARDS -> cardsAgent.chat(
                    message, conversationId);

            case PAYMENTS -> paymentsAgent.chat(
                    message, conversationId);

            case FEES -> feesAgent.chat(
                    message, conversationId);

            case GENERAL -> generalSupportAgent.chat(
                    message, conversationId);

            case UNKNOWN ->
                    "Could you clarify which banking service you need?";
        };
    }
}