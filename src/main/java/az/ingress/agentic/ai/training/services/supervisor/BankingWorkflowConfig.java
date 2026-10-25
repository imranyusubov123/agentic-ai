package az.ingress.agentic.ai.training.services.supervisor;

import az.ingress.agentic.ai.config.dto.BankingIntent;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * Configures the banking investigation workflow using LangGraph4j.
 *
 * Current execution:
 *
 * START
 *   |
 *   v
 * Accounts Agent
 *   |
 *   v
 * Payments Agent
 *   |
 *   v
 * Aggregate Findings
 *   |
 *   v
 * END
 *
 * This is a deterministic, sequential workflow.
 *
 * The Accounts Agent executes first.
 * The Payments Agent receives the Accounts investigation findings.
 * The Aggregator combines the results into a final response.
 *
 * TODO:
 * Replace this fixed workflow with a plan-driven executor
 * supporting dynamic agent selection and parallel execution.
 */
@Configuration
public class BankingWorkflowConfig {

    @Bean
    public CompiledGraph<BankingWorkflowState> bankingWorkflow(
            BankingWorkflowNodes nodes
    ) throws GraphStateException {

        return new StateGraph<>(BankingWorkflowState::new)

                // Step 1: Investigate the account debit.
                .addNode(
                        "accounts",
                        node_async(state -> nodes.investigate(
                                BankingIntent.ACCOUNTS,
                                state,
                                Map.of()
                        ))
                )

                // Step 2: Investigate the payment.
                // The Accounts findings are passed as dependency context.
                .addNode(
                        "payments",
                        node_async(state -> nodes.investigate(
                                BankingIntent.PAYMENTS,
                                state,
                                Map.of(
                                        "accounts",
                                        state.accountResult().orElse(
                                                "No account findings available"
                                        )
                                )
                        ))
                )

                // Step 3: Combine investigation results.
                .addNode(
                        "aggregate",
                        node_async(nodes::aggregate)
                )

                // Define sequential execution order.
                .addEdge(START, "accounts")
                .addEdge("accounts", "payments")
                .addEdge("payments", "aggregate")
                .addEdge("aggregate", END)

                // Compile the graph for execution.
                .compile();
    }
}