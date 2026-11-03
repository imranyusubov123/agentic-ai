package az.ingress.agentic.ai.training.services.supervisor.observability;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowObservabilityService {

    private final ObservationRegistry observationRegistry;

    public Observation currentObservation() {
        return observationRegistry.getCurrentObservation();
    }

    /**
     * Restores an existing parent observation on the current thread.
     * Does not start or stop that parent.
     */
    public <T> T withParent(
            Observation parent,
            Supplier<T> operation) {

        Objects.requireNonNull(operation, "operation is required");

        if (parent == null) {
            return operation.get();
        }

        try (Observation.Scope scope = parent.openScope()) {
            return operation.get();
        }
    }

    public <T> T traceWorkflow(Supplier<T> operation) {
        Objects.requireNonNull(operation, "operation is required");

        String workflowId = UUID.randomUUID().toString();

        Observation observation = Observation
                .createNotStarted(
                        "banking.workflow.execution",
                        observationRegistry
                )
                .contextualName("banking-investigation")
                .lowCardinalityKeyValue(
                        "workflow.type",
                        "multi_domain_investigation"
                )
                .highCardinalityKeyValue(
                        "langfuse.observation.type",
                        "chain"
                )
                .highCardinalityKeyValue(
                        "banking.workflow.id",
                        workflowId
                );

        Observation parent = currentObservation();

        if (parent != null) {
            observation.parentObservation(parent);
        }

        return observation.observe(() -> {
            long started = System.nanoTime();

            log.info(
                    "BANKING_WORKFLOW_STARTED workflowId={}",
                    workflowId
            );

            try {
                T result = operation.get();

                observation.highCardinalityKeyValue(
                        "banking.workflow.status",
                        "COMPLETED"
                );

                log.info(
                        "BANKING_WORKFLOW_COMPLETED workflowId={} durationMs={}",
                        workflowId,
                        elapsedMillis(started)
                );

                return result;

            } catch (RuntimeException | Error exception) {
                observation.highCardinalityKeyValue(
                        "banking.workflow.status",
                        "FAILED"
                );

                log.error(
                        "BANKING_WORKFLOW_FAILED workflowId={} "
                                + "exceptionType={} durationMs={}",
                        workflowId,
                        exception.getClass().getSimpleName(),
                        elapsedMillis(started),
                        exception
                );

                throw exception;
            }
        });
    }

    /**
     * Create this supplier on the submitting thread while the parent
     * observation is active. Execute it on the worker thread.
     */
    public <T> Supplier<T> tracedTask(
            String name,
            Supplier<T> operation) {

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Task observation name is required"
            );
        }

        Objects.requireNonNull(operation, "operation is required");

        Observation parent = currentObservation();

        return () -> {
            Observation child = Observation
                    .createNotStarted(name, observationRegistry)
                    .contextualName(name)
                    .highCardinalityKeyValue(
                            "langfuse.observation.type",
                            "chain"
                    );

            if (parent != null) {
                child.parentObservation(parent);
            }

            return child.observe(() -> {
                long started = System.nanoTime();

                log.info("BANKING_TASK_STARTED task={}", name);

                try {
                    T result = operation.get();

                    child.highCardinalityKeyValue(
                            "banking.task.status",
                            "COMPLETED"
                    );

                    log.info(
                            "BANKING_TASK_COMPLETED task={} durationMs={}",
                            name,
                            elapsedMillis(started)
                    );

                    return result;

                } catch (RuntimeException | Error exception) {
                    child.highCardinalityKeyValue(
                            "banking.task.status",
                            "FAILED"
                    );

                    log.error(
                            "BANKING_TASK_FAILED task={} durationMs={}",
                            name,
                            elapsedMillis(started),
                            exception
                    );

                    throw exception;
                }
            });
        };
    }

    public <T> T traceStage(
            String name,
            Supplier<T> operation) {

        return tracedTask(name, operation).get();
    }

    private static long elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}