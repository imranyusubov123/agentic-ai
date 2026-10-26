package az.ingress.agentic.ai.training.demo.workflow;

import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Configuration
public class TransferFeeWorkflowConfig {

    @Bean
    public MemorySaver transferFeeCheckpointSaver() {
        return new MemorySaver();
    }

    @Bean
    public CompiledGraph<TransferFeeWorkflowState> transferFeeWorkflow(
            TransferFeeWorkflowNodes nodes,
            MemorySaver transferFeeCheckpointSaver
    ) throws GraphStateException {

        var graph = new StateGraph<>(
                TransferFeeWorkflowState::new
        )
                .addNode(
                        "discover_transfers",
                        node_async(nodes::discover)
                )
                .addNode(
                        "verify_fee",
                        node_async(nodes::verifyFee)
                )
                .addNode(
                        "generate_explanation",
                        node_async(nodes::explain)
                )
                .addEdge(START, "discover_transfers")
                .addEdge("discover_transfers", "verify_fee")
                .addEdge("verify_fee", "generate_explanation")
                .addEdge("generate_explanation", END);

        var config = CompileConfig.builder()
                .checkpointSaver(transferFeeCheckpointSaver)

                // Pause before executing fee verification.
                .interruptBefore("verify_fee")

                // Retain checkpoint state for inspection.
                .releaseThread(false)
                .build();

        return graph.compile(config);
    }
}
