package az.ingress.agentic.ai;

import az.ingress.agentic.ai.training.services.supervisor.BankingWorkflowConfig;
import az.ingress.agentic.ai.training.services.supervisor.BankingWorkflowNodes;
import az.ingress.agentic.ai.training.services.supervisor.BankingWorkflowState;
import org.junit.jupiter.api.Test;
import org.bsc.langgraph4j.CompiledGraph;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BankingWorkflowTest {

    @Test
    void shouldExecuteAccountsPaymentsAndAggregationInOrder()
            throws Exception {

        // Arrange
        BankingWorkflowNodes nodes =
                mock(BankingWorkflowNodes.class);

        when(nodes.investigateAccounts(any()))
                .thenReturn(Map.of(
                        BankingWorkflowState.ACCOUNT_RESULT,
                        "500 AZN debited"
                ));

        when(nodes.investigatePayments(any()))
                .thenReturn(Map.of(
                        BankingWorkflowState.PAYMENT_RESULT,
                        "Transfer TX-1001 is PENDING"
                ));

        when(nodes.aggregate(any()))
                .thenReturn(Map.of(
                        BankingWorkflowState.FINAL_RESPONSE,
                        "Investigation completed"
                ));

        CompiledGraph<BankingWorkflowState> graph =
                new BankingWorkflowConfig()
                        .bankingWorkflow(nodes);

        // Act
        var finalState = graph.invoke(Map.of(
                BankingWorkflowState.QUESTION,
                "Investigate transfer TX-1001",
                BankingWorkflowState.CONVERSATION_ID,
                "test-123"
        )).orElseThrow();

        // Assert
        assertThat(finalState.accountResult())
                .contains("500 AZN debited");

        assertThat(finalState.paymentResult())
                .contains("Transfer TX-1001 is PENDING");

        assertThat(finalState.finalResponse())
                .contains("Investigation completed");

        // Verify execution order
        var order = inOrder(nodes);

        order.verify(nodes).investigateAccounts(any());
        order.verify(nodes).investigatePayments(any());
        order.verify(nodes).aggregate(any());

        verifyNoMoreInteractions(nodes);
    }
}