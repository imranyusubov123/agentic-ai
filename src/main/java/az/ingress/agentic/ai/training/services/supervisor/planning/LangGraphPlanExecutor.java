package az.ingress.agentic.ai.training.services.supervisor.planning;

import az.ingress.agentic.ai.training.services.supervisor.BankingWorkflowNodes;
import az.ingress.agentic.ai.training.services.supervisor.BankingWorkflowState;
import az.ingress.agentic.ai.training.services.supervisor.observability.WorkflowObservabilityService;
import io.micrometer.observation.Observation;
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

@Service
public class LangGraphPlanExecutor {

    private final BankingWorkflowNodes nodes;
    private final Executor bankingWorkflowExecutor;
    private final WorkflowObservabilityService observability;

    public LangGraphPlanExecutor(
            BankingWorkflowNodes nodes,
            @Qualifier("bankingWorkflowExecutor")
            Executor bankingWorkflowExecutor,
            WorkflowObservabilityService observability) {

        this.nodes = nodes;
        this.bankingWorkflowExecutor = bankingWorkflowExecutor;
        this.observability = observability;
    }

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

        // Capture supervisor.execute while it is active on this thread.
        final Observation executionParent =
                observability.currentObservation();

        try {
            List<List<ExecutionPlan.InvestigationTask>> stages =
                    buildStages(plan);

            var graph = new StateGraph<>(BankingWorkflowState::new);

            String previous = START;

            for (int i = 0; i < stages.size(); i++) {
                String nodeName = "stage_" + i;

                List<ExecutionPlan.InvestigationTask> stage =
                        List.copyOf(stages.get(i));

                graph.addNode(
                        nodeName,
                        node_async(state ->
                                observability.withParent(
                                        executionParent,
                                        () -> observability.traceStage(
                                                "workflow." + nodeName,
                                                () -> executeStage(
                                                        stage,
                                                        plan,
                                                        state
                                                )
                                        )
                                )
                        )
                );

                graph.addEdge(previous, nodeName);
                previous = nodeName;
            }

            graph.addNode(
                    "aggregate",
                    node_async(state ->
                            observability.withParent(
                                    executionParent,
                                    () -> observability.traceStage(
                                            "supervisor.aggregate",
                                            () -> nodes.aggregate(state)
                                    )
                            )
                    )
            );

            graph.addEdge(previous, "aggregate");
            graph.addEdge("aggregate", END);

            var compiledGraph = graph.compile();

            var initialState = Map.<String, Object>of(
                    BankingWorkflowState.QUESTION,
                    message,
                    BankingWorkflowState.CONVERSATION_ID,
                    conversationId
            );

            var finalState = compiledGraph.invoke(initialState)
                    .orElseThrow(() ->
                            new IllegalStateException(
                                    "Workflow returned no state"
                            )
                    );

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

    private Map<String, Object> executeStage(
            List<ExecutionPlan.InvestigationTask> stage,
            ExecutionPlan plan,
            BankingWorkflowState state) {

        /*
         * tracedTask() captures the active stage observation here,
         * before supplyAsync moves execution onto a worker thread.
         */
        List<CompletableFuture<Map<String, Object>>> futures =
                stage.stream()
                        .map(task -> CompletableFuture.supplyAsync(
                                observability.tracedTask(
                                        "investigation."
                                                + task.agent()
                                                .name()
                                                .toLowerCase(Locale.ROOT),
                                        () -> executeTask(task, plan, state)
                                ),
                                bankingWorkflowExecutor
                        ))
                        .toList();

        // Keep the stage open until all workers finish.
        CompletableFuture.allOf(
                futures.toArray(CompletableFuture[]::new)
        ).join();

        Map<String, Object> updates = new HashMap<>();

        for (var future : futures) {
            Map<String, Object> result = future.join();

            for (var entry : result.entrySet()) {
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

    private Map<String, Object> executeTask(
            ExecutionPlan.InvestigationTask task,
            ExecutionPlan plan,
            BankingWorkflowState state) {

        Map<String, ExecutionPlan.InvestigationTask> tasksById =
                plan.tasks().stream()
                        .collect(Collectors.toMap(
                                ExecutionPlan.InvestigationTask::id,
                                taskDefinition -> taskDefinition
                        ));

        Map<String, String> dependencyFindings =
                new LinkedHashMap<>();

        for (String dependencyId : task.dependsOn()) {
            var dependency = tasksById.get(dependencyId);

            if (dependency == null) {
                throw new IllegalArgumentException(
                        "Unknown dependency: " + dependencyId
                );
            }

            String resultKey = nodes.resultKey(dependency.agent());

            String finding = state.<String>value(resultKey)
                    .orElseThrow(() ->
                            new IllegalStateException(
                                    "Missing finding for dependency: "
                                            + dependencyId
                                            + " (state key: "
                                            + resultKey
                                            + ")"
                            )
                    );

            dependencyFindings.put(dependencyId, finding);
        }

        return nodes.investigate(
                task.agent(),
                state,
                dependencyFindings
        );
    }

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
            List<ExecutionPlan.InvestigationTask> ready =
                    remaining.values()
                            .stream()
                            .filter(task ->
                                    completed.containsAll(task.dependsOn())
                            )
                            .toList();

            if (ready.isEmpty()) {
                throw new IllegalArgumentException(
                        "Invalid or cyclic execution plan"
                );
            }

            stages.add(ready);

            for (var task : ready) {
                remaining.remove(task.id());
                completed.add(task.id());
            }
        }

        return stages;
    }
}