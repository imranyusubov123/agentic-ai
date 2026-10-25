package az.ingress.agentic.ai.training.services.supervisor.planning;

import az.ingress.agentic.ai.training.services.supervisor.BankingWorkflowNodes;
import az.ingress.agentic.ai.training.services.supervisor.BankingWorkflowState;
import lombok.RequiredArgsConstructor;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Service
@RequiredArgsConstructor
public class LangGraphPlanExecutor {

    private final BankingWorkflowNodes nodes;
    private final Executor bankingWorkflowExecutor;

    public String execute(
            ExecutionPlan plan,
            String message,
            String conversationId) {

        try {
            // Build execution stages from task dependencies.
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
                        node_async(state -> executeStage(stage, state))
                );

                graph.addEdge(previous, nodeName);
                previous = nodeName;
            }

            graph.addNode(
                    "aggregate",
                    node_async(nodes::aggregate)
            );

            graph.addEdge(previous, "aggregate");
            graph.addEdge("aggregate", END);

            var compiledGraph = graph.compile();

            var initialState = Map.<String, Object>of(
                    BankingWorkflowState.QUESTION, message,
                    BankingWorkflowState.CONVERSATION_ID, conversationId
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

    /**
     * Executes all independent tasks in the current stage.
     * The next stage cannot begin until every task completes.
     */
    private Map<String, Object> executeStage(
            List<ExecutionPlan.InvestigationTask> stage,
            BankingWorkflowState state) {

        Map<String, ExecutionPlan.InvestigationTask> tasksById =
                stage.stream().collect(Collectors.toMap(
                        ExecutionPlan.InvestigationTask::id,
                        task -> task
                ));

        List<CompletableFuture<Map<String, Object>>> futures =
                stage.stream()
                        .map(task -> CompletableFuture.supplyAsync(
                                () -> executeTask(task, state),
                                bankingWorkflowExecutor
                        ))
                        .toList();

        // Wait for all agents before moving to the next graph node.
        CompletableFuture.allOf(
                futures.toArray(CompletableFuture[]::new)
        ).join();

        Map<String, Object> updates = new HashMap<>();

        for (var future : futures) {
            updates.putAll(future.join());
        }

        return updates;
    }

    private Map<String, Object> executeTask(
            ExecutionPlan.InvestigationTask task,
            BankingWorkflowState state) {

        Map<String, String> dependencyFindings = new HashMap<>();

        // Read only findings from explicitly declared dependencies.
        // Dependency tasks have completed in earlier stages.
        for (String dependencyId : task.dependsOn()) {
            String result = findDependencyResult(
                    dependencyId,
                    state
            );
            dependencyFindings.put(dependencyId, result);
        }

        return nodes.investigate(
                task.agent(),
                state,
                dependencyFindings
        );
    }

    private String findDependencyResult(
            String dependencyId,
            BankingWorkflowState state) {

        // Dependency IDs are resolved through the execution plan.
        // This method will be replaced by the plan-aware lookup below.
        return state.<String>value(dependencyId)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Missing dependency result: " + dependencyId
                        )
                );
    }

    /**
     * Creates topological execution stages.
     * <p>
     * Tasks with satisfied dependencies are placed in the same stage
     * and can execute concurrently.
     */
    private List<List<ExecutionPlan.InvestigationTask>> buildStages(
            ExecutionPlan plan) {

        Map<String, ExecutionPlan.InvestigationTask> remaining =
                plan.tasks().stream().collect(Collectors.toMap(
                        ExecutionPlan.InvestigationTask::id,
                        task -> task,
                        (a, b) -> {
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
                    remaining.values().stream()
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

            for (var task : ready) {
                remaining.remove(task.id());
                completed.add(task.id());
            }
        }

        return stages;
    }
}