package az.ingress.agentic.ai.training.services.supervisor;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BankingSupervisor {

    private final CompiledGraph<BankingWorkflowState> bankingWorkflow;
    private final ObservationRegistry observationRegistry;

    public String investigate(
            String message,
            String conversationId) {

        if (message == null || message.isBlank()
                || conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException(
                    "Message and conversationId are required"
            );
        }

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
                var initialState = Map.<String, Object>of(
                        BankingWorkflowState.QUESTION, message,
                        BankingWorkflowState.CONVERSATION_ID, conversationId
                );

                var finalState = bankingWorkflow.invoke(initialState)
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Banking workflow returned no state"
                                )
                        );

                String response = finalState.finalResponse()
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Banking workflow returned no response"
                                )
                        );

                observation.highCardinalityKeyValue(
                        "banking.workflow.status",
                        "COMPLETED"
                );

                log.info(
                        "BANKING_WORKFLOW_COMPLETED workflowId={} durationMs={}",
                        workflowId,
                        elapsedMillis(started)
                );

                return response;

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