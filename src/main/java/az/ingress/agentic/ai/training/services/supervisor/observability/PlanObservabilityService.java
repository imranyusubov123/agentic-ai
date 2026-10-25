package az.ingress.agentic.ai.training.services.supervisor.observability;

import az.ingress.agentic.ai.training.services.supervisor.planning.ExecutionPlan;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PlanObservabilityService {

    private final ObservationRegistry registry;

    public <T> T observePlanning(Supplier<T> operation) {
        return Observation.createNotStarted(
                "banking.supervisor.plan", registry
        ).observe(operation);
    }

    public ExecutionPlan observeValidation(
            Supplier<ExecutionPlan> operation) {

        return Observation.createNotStarted(
                "banking.supervisor.validate", registry
        ).observe(operation);
    }

    public <T> T observeExecution(
            ExecutionPlan plan,
            Supplier<T> operation) {

        Observation observation = Observation.createNotStarted(
                "banking.plan.execute", registry
        ).lowCardinalityKeyValue(
                "workflow.type", "dynamic_plan"
        );

        addPlanAttributes(observation, plan);

        return observation.observe(operation);
    }

    public <T> T observeStage(
            int stageIndex,
            List<ExecutionPlan.InvestigationTask> tasks,
            Supplier<T> operation) {

        Observation observation = Observation.createNotStarted(
                "banking.plan.stage", registry
        ).highCardinalityKeyValue(
                "stage.index", String.valueOf(stageIndex)
        ).highCardinalityKeyValue(
                "stage.agents",
                tasks.stream()
                        .map(t -> t.agent().name())
                        .collect(Collectors.joining(","))
        ).highCardinalityKeyValue(
                "stage.task.count",
                String.valueOf(tasks.size())
        );

        return observation.observe(operation);
    }

    public <T> T observeTask(
            ExecutionPlan.InvestigationTask task,
            Supplier<T> operation) {

        Observation observation = Observation.createNotStarted(
                "banking.plan.task", registry
        ).lowCardinalityKeyValue(
                "agent", task.agent().name()
        ).highCardinalityKeyValue(
                "task.id", task.id()
        ).highCardinalityKeyValue(
                "task.dependencies",
                String.join(",", task.dependsOn())
        );

        return observation.observe(operation);
    }

    private void addPlanAttributes(
            Observation observation,
            ExecutionPlan plan) {

        observation.highCardinalityKeyValue(
                "plan.task.count",
                String.valueOf(plan.tasks().size())
        );

        observation.highCardinalityKeyValue(
                "plan.agents",
                plan.tasks().stream()
                        .map(t -> t.agent().name())
                        .collect(Collectors.joining(","))
        );

        observation.highCardinalityKeyValue(
                "plan.dependencies",
                plan.tasks().stream()
                        .map(t -> t.id() + "->" + t.dependsOn())
                        .collect(Collectors.joining(";"))
        );
    }
}
