package az.ingress.agentic.ai.training.services.supervisor.observability;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowObservabilityService {

    private final ObservationRegistry observationRegistry;

    public <T> T traceWorkflow(Supplier<T> operation) {

        String workflowId = UUID.randomUUID().toString();

        Observation observation = Observation.createNotStarted(
                        "banking.workflow.execution",
                        observationRegistry
                )
                .lowCardinalityKeyValue(
                        "workflow.type",
                        "multi_domain_investigation"
                )
                .highCardinalityKeyValue(
                        "banking.workflow.id",
                        workflowId
                );

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

            } catch (RuntimeException exception) {

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

    private static long elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}
