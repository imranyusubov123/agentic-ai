package az.ingress.agentic.ai.training.services.supervisor.planning;

import az.ingress.agentic.ai.training.services.supervisor.BankingWorkflowNodes;
import az.ingress.agentic.ai.training.services.supervisor.BankingWorkflowState;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * Executes validated, LLM-generated investigation plans.
 *
 * The Supervisor decides which agents are needed and their dependencies.
 * This executor converts the plan into sequential LangGraph4j stages.
 *
 * Independent agents within a stage execute concurrently.
 * Dependent agents execute only after their prerequisites complete.
 *
 * Example:
 *
 * Stage 0: ACCOUNTS + PAYMENTS
 * Stage 1: FEES
 * Stage 2: AGGREGATE
 */
@Service
public class LangGraphPlanExecutor {

    private final BankingWorkflowNodes nodes;
    private final Executor bankingWorkflowExecutor;

    public LangGraphPlanExecutor(
            BankingWorkflowNodes nodes,
            @Qualifier("bankingWorkflowExecutor")
            Executor bankingWorkflowExecutor) {

        this.nodes = nodes;
        this.bankingWorkflowExecutor = bankingWorkflowExecutor;
    }

    /**
     * Builds and executes a LangGraph4j workflow.
     *
     * The caller must validate the execution plan first.
     */
    public String execute(
            ExecutionPlan plan,
            String message,
            String conversationId) {

        Objects.requireNonNull(plan, "Execution plan is required");

        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException(
                    "Customer message is required"
            );
        }

        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException(
                    "Conversation ID is required"
            );
        }

        try {
            // Convert dependencies into execution stages.
            List<List<ExecutionPlan.InvestigationTask>> stages =
                    buildStages(plan);

            // Create a graph specifically for this execution plan.
            var graph = new StateGraph<>(BankingWorkflowState::new);

            String previous = START;

            for (int i = 0; i < stages.size(); i++) {

                String nodeName = "stage_" + i;

                List<ExecutionPlan.InvestigationTask> stage =
                        List.copyOf(stages.get(i));

                // All tasks in this stage execute concurrently.
                graph.addNode(
                        nodeName,
                        node_async(state ->
                                executeStage(stage, plan, state)
                        )
                );

                // Stages execute sequentially.
                graph.addEdge(previous, nodeName);
                previous = nodeName;
            }

            // Aggregate the results after all stages complete.
            graph.addNode(
                    "aggregate",
                    node_async(nodes::aggregate)
            );

            graph.addEdge(previous, "aggregate");
            graph.addEdge("aggregate", END);

            // Compile the dynamically generated workflow.
            var compiledGraph = graph.compile();

            // Initialize shared workflow state.
            var initialState = Map.<String, Object>of(
                    BankingWorkflowState.QUESTION, message,
                    BankingWorkflowState.CONVERSATION_ID, conversationId
            );

            // Execute LangGraph4j.
            var finalState = compiledGraph.invoke(initialState)
                    .orElseThrow(() ->
                            new IllegalStateException(
                                    "Workflow returned no state"
                            )
                    );

            // Return the final aggregated response.
            return finalState.finalResponse()
                    .orElseThrow(() ->
                            new IllegalStateException(
                                    "Workflow returned no response"
                            )
                    );

        } catch (GraphStateException exception) {
            throw new IllegalStateException(
                    "Unable to compile investigation workflow",
                    exception
            );
        }
    }

    /**
     * Executes all independent tasks in a stage concurrently.
     *
     * The next stage starts only after every task in this stage
     * completes successfully.
     */
    private Map<String, Object> executeStage(
            List<ExecutionPlan.InvestigationTask> stage,
            ExecutionPlan plan,
            BankingWorkflowState state) {

        List<CompletableFuture<Map<String, Object>>> futures =
                stage.stream()
                        .map(task -> CompletableFuture.supplyAsync(
                                () -> executeTask(task, plan, state),
                                bankingWorkflowExecutor
                        ))
                        .toList();

        // Wait for every task in this stage.
        CompletableFuture.allOf(
                futures.toArray(CompletableFuture[]::new)
        ).join();

        // Merge results into a single LangGraph4j state update.
        Map<String, Object> updates = new HashMap<>();

        for (var future : futures) {

            Map<String, Object> result = future.join();

            for (var entry : result.entrySet()) {

                // Prevent two agents from writing the same state key.
                if (updates.putIfAbsent(
                        entry.getKey(),
                        entry.getValue()
                ) != null) {

                    throw new IllegalStateException(
                            "Duplicate workflow state key: "
                                    + entry.getKey()
                    );
                }
            }
        }

        return updates;
    }

    /**
     * Executes one task and supplies only its declared
     * dependency findings.
     */
    private Map<String, Object> executeTask(
            ExecutionPlan.InvestigationTask task,
            ExecutionPlan plan,
            BankingWorkflowState state) {

        // Map LLM-generated task IDs to their task definitions.
        Map<String, ExecutionPlan.InvestigationTask> tasksById =
                plan.tasks().stream()
                        .collect(Collectors.toMap(
                                ExecutionPlan.InvestigationTask::id,
                                taskDefinition -> taskDefinition
                        ));

        Map<String, String> dependencyFindings =
                new LinkedHashMap<>();

        for (String dependencyId : task.dependsOn()) {

            // Resolve the task referenced by the dependency ID.
            var dependency = tasksById.get(dependencyId);

            if (dependency == null) {
                throw new IllegalArgumentException(
                        "Unknown dependency: " + dependencyId
                );
            }

            // Convert task ID -> agent -> workflow state key.
            //
            // Example:
            // task_payments_recent_transfers
            //       -> PAYMENTS
            //       -> paymentResult
            String resultKey = nodes.resultKey(
                    dependency.agent()
            );

            // Dependency tasks completed in previous stages.
            String finding = state.<String>value(resultKey)
                    .orElseThrow(() ->
                            new IllegalStateException(
                                    "Missing finding for dependency: "
                                            + dependencyId
                                            + " (state key: "
                                            + resultKey + ")"
                            )
                    );

            dependencyFindings.put(
                    dependencyId,
                    finding
            );
        }

        // Invoke the domain agent selected by the Supervisor.
        return nodes.investigate(
                task.agent(),
                state,
                dependencyFindings
        );
    }

    /**
     * Converts a dependency graph into execution stages.
     *
     * Tasks whose dependencies have already been satisfied
     * are grouped into the same stage.
     *
     * Example:
     *
     * ACCOUNTS: []
     * PAYMENTS: []
     * FEES: [PAYMENTS]
     *
     * Stage 0: ACCOUNTS + PAYMENTS
     * Stage 1: FEES
     */
    private List<List<ExecutionPlan.InvestigationTask>> buildStages(
            ExecutionPlan plan) {

        if (plan.tasks() == null || plan.tasks().isEmpty()) {
            throw new IllegalArgumentException(
                    "Execution plan must contain tasks"
            );
        }

        Map<String, ExecutionPlan.InvestigationTask> remaining =
                plan.tasks().stream()
                        .collect(Collectors.toMap(
                                ExecutionPlan.InvestigationTask::id,
                                task -> task,
                                (first, second) -> {
                                    throw new IllegalArgumentException(
                                            "Duplicate task ID"
                                    );
                                },
                                LinkedHashMap::new
                        ));

        Set<String> completed = new HashSet<>();

        List<List<ExecutionPlan.InvestigationTask>> stages =
                new ArrayList<>();

        while (!remaining.isEmpty()) {

            // Find tasks whose dependencies are already satisfied.
            List<ExecutionPlan.InvestigationTask> ready =
                    remaining.values()
                            .stream()
                            .filter(task ->
                                    completed.containsAll(
                                            task.dependsOn()
                                    )
                            )
                            .toList();

            if (ready.isEmpty()) {
                throw new IllegalArgumentException(
                        "Invalid or cyclic execution plan"
                );
            }

            stages.add(ready);

            // Mark tasks as scheduled for the next dependency layer.
            // Their actual execution occurs later in executeStage().
            for (var task : ready) {
                remaining.remove(task.id());
                completed.add(task.id());
            }
        }

        return stages;
    }
}