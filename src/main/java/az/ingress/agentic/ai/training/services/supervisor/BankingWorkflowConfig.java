package az.ingress.agentic.ai.training.services.supervisor;

import az.ingress.agentic.ai.config.dto.BankingIntent;
import az.ingress.agentic.ai.training.services.supervisor.observability.WorkflowTracing;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Configuration
public class BankingWorkflowConfig {

    @Bean
    public CompiledGraph<BankingWorkflowState> bankingWorkflow(
            BankingWorkflowNodes nodes,
            WorkflowTracing tracing
    ) throws GraphStateException {

        return new StateGraph<>(BankingWorkflowState::new)

                .addNode("accounts", node_async(state ->
                        tracing.observe(
                                "investigation.accounts",
                                tracing.currentObservation(),
                                () -> nodes.investigate(
                                        BankingIntent.ACCOUNTS,
                                        state,
                                        Map.of()
                                )
                        )
                ))

                .addNode("payments", node_async(state ->
                        tracing.observe(
                                "investigation.payments",
                                tracing.currentObservation(),
                                () -> nodes.investigate(
                                        BankingIntent.PAYMENTS,
                                        state,
                                        Map.of(
                                                "accounts",
                                                state.accountResult().orElse(
                                                        "No account findings available"
                                                )
                                        )
                                )
                        )
                ))

                .addNode("aggregate", node_async(state ->
                        tracing.observe(
                                "supervisor.aggregate",
                                tracing.currentObservation(),
                                () -> nodes.aggregate(state)
                        )
                ))

                .addEdge(START, "accounts")
                .addEdge("accounts", "payments")
                .addEdge("payments", "aggregate")
                .addEdge("aggregate", END)

                .compile();
    }
}