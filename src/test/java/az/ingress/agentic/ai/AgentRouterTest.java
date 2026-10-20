package az.ingress.agentic.ai;

import az.ingress.agentic.ai.config.dto.BankingIntent;
import az.ingress.agentic.ai.config.dto.IntentResult;
import az.ingress.agentic.ai.training.domains.account.AccountsAgent;
import az.ingress.agentic.ai.training.domains.cards.CardsAgent;
import az.ingress.agentic.ai.training.domains.fees.FeesAgent;
import az.ingress.agentic.ai.training.domains.payments.PaymentsAgent;
import az.ingress.agentic.ai.training.services.AgentRouter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentRouterTest {

    @Mock
    AccountsAgent accountsAgent;
    @Mock
    CardsAgent cardsAgent;
    @Mock
    PaymentsAgent paymentsAgent;
    @Mock
    FeesAgent feesAgent;

    @InjectMocks
    AgentRouter router;

    @Test
    void shouldRouteToAccountsAgent() {

        IntentResult result = new IntentResult(
                List.of(BankingIntent.ACCOUNTS),
                false
        );

        when(accountsAgent.chat("What is my balance?", "test-123"))
                .thenReturn("Your balance is 2500 AZN");

        String response = router.route(
                result,
                "What is my balance?",
                "test-123"
        );

        assertThat(response).isEqualTo("Your balance is 2500 AZN");

        verify(accountsAgent).chat(
                "What is my balance?",
                "test-123"
        );

        verifyNoInteractions(
                cardsAgent,
                paymentsAgent,
                feesAgent
        );
    }

    @Test
    void shouldNotInvokeAgentsForMultipleIntents() {

        IntentResult result = new IntentResult(
                List.of(
                        BankingIntent.ACCOUNTS,
                        BankingIntent.PAYMENTS
                ),
                false
        );

        String response = router.route(
                result,
                "Check my balance and last payment",
                "test-456"
        );

        assertThat(response)
                .contains("multiple banking domains");

        verifyNoInteractions(
                accountsAgent,
                cardsAgent,
                paymentsAgent,
                feesAgent
        );
    }
}
