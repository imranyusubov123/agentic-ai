package az.ingress.agentic.ai.training.services.supervisor;

import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Configuration
public class BankingWorkflowConfig {

    @Bean
    public CompiledGraph<BankingWorkflowState> bankingWorkflow(
            BankingWorkflowNodes nodes
    ) throws GraphStateException {

        return new StateGraph<>(
                BankingWorkflowState::new
        )
                .addNode(
                        "accounts",
                        node_async(nodes::investigateAccounts)
                )
                .addNode(
                        "payments",
                        node_async(nodes::investigatePayments)
                )
                .addNode(
                        "aggregate",
                        node_async(nodes::aggregate)
                )
                .addEdge(START, "accounts")
                .addEdge("accounts", "payments")
                .addEdge("payments", "aggregate")
                .addEdge("aggregate", END)
                .compile();
    }
}
