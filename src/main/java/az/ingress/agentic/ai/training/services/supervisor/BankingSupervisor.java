package az.ingress.agentic.ai.training.services.supervisor;

import az.ingress.agentic.ai.training.services.supervisor.observability.WorkflowObservabilityService;
import lombok.RequiredArgsConstructor;
import org.bsc.langgraph4j.CompiledGraph;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Coordinates multi-domain banking investigations.
 * <p>
 * The Supervisor does not execute domain agents directly.
 * It initializes the workflow and delegates execution to LangGraph4j.
 * <p>
 * LangGraph4j determines which nodes execute based on the
 * predefined graph structure and conditional edges.
 * <p>
 * Note: This is currently a deterministic Supervisor,
 * not an LLM-powered planning agent.
 */
@Service
@RequiredArgsConstructor
public class BankingSupervisor {

    // Compiled LangGraph4j workflow containing domain agents and transitions.
    private final CompiledGraph<BankingWorkflowState> bankingWorkflow;

    // Handles OpenTelemetry tracing, execution metrics, and logging.
    private final WorkflowObservabilityService observability;

    /**
     * Entry point for multi-domain customer requests.
     * <p>
     * Called by AgentRouter when a request requires
     * coordination between multiple banking domains.
     */
    public String investigate(
            String message,
            String conversationId) {

        // Reject invalid requests before starting the workflow.
        validate(message, conversationId);

        // Execute the investigation inside an observability span.
        // This allows Grafana Tempo to track the workflow's duration and status.
        return observability.traceWorkflow(
                () -> execute(message, conversationId)
        );
    }

    /**
     * Starts the LangGraph4j workflow and returns its final response.
     */
    private String execute(
            String message,
            String conversationId) {

        // Initialize shared workflow state.
        // Every graph node can read these values during execution.
        var initialState = Map.<String, Object>of(
                BankingWorkflowState.QUESTION, message,
                BankingWorkflowState.CONVERSATION_ID, conversationId
        );

        // Execute the compiled graph.
        // LangGraph4j invokes nodes according to the configured edges,
        // applies their state updates, and returns the final state.
        var finalState = bankingWorkflow.invoke(initialState)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Banking workflow returned no state"
                        )
                );

        // Return the response produced by the aggregation node.
        return finalState.finalResponse()
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Banking workflow returned no response"
                        )
                );
    }

    /**
     * Validates the minimum information required to start an investigation.
     */
    private void validate(
            String message,
            String conversationId) {

        if (message == null || message.isBlank()
                || conversationId == null
                || conversationId.isBlank()) {

            throw new IllegalArgumentException(
                    "Message and conversationId are required"
            );
        }
    }
}