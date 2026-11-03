package az.ingress.agentic.ai.training.services.langfuse;

import az.ingress.agentic.ai.training.demo.service.BankingConversationOrchestrator;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import io.micrometer.tracing.handler.TracingObservationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class ObservedBankingConversation {

    private static final String INPUT = "langfuse.observation.input";
    private static final String OUTPUT = "langfuse.observation.output";
    private static final String SESSION_ID = "langfuse.session.id";
    private static final String TRACE_NAME = "banking-chat";

    private final BankingConversationOrchestrator orchestrator;
    private final ObservationRegistry observationRegistry;

    // Preserve compatibility with existing callers.
    public Flux<String> chat(String message, String conversationId) {
        return chat(message, conversationId, traceId -> {
        });
    }

    public Flux<String> chat(
            String message,
            String conversationId,
            Consumer<String> onTraceId) {

        // Capture the HTTP parent before deferred execution.
        Observation parent = observationRegistry.getCurrentObservation();
        enrichParent(parent, message, conversationId);

        return Flux.defer(() -> {
            StringBuffer answer = new StringBuffer();
            Observation workflow =
                    startWorkflow(parent, message, conversationId);

            return Flux.defer(() -> {
                        TracingObservationHandler.TracingContext tracing =
                                workflow.getContextView().get(
                                        TracingObservationHandler
                                                .TracingContext.class
                                );

                        if (tracing == null || tracing.getSpan() == null) {
                            return Flux.<String>error(
                                    new IllegalStateException(
                                            "No tracing span found for banking.workflow"
                                    )
                            );
                        }

                        String traceId =
                                tracing.getSpan().context().traceId();

                        if (traceId == null
                                || traceId.isBlank()
                                || traceId.matches("0+")) {
                            return Flux.<String>error(
                                    new IllegalStateException(
                                            "banking.workflow has no valid trace ID"
                                    )
                            );
                        }

                        onTraceId.accept(traceId);

                        return orchestrator.chat(message, conversationId);
                    })
                    .doOnNext(answer::append)
                    .doOnError(workflow::error)
                    .doFinally(signal ->
                            finish(workflow, parent, answer, signal))
                    .contextWrite(context -> context.put(
                            ObservationThreadLocalAccessor.KEY,
                            workflow
                    ));
        });
    }

    private static void enrichParent(
            Observation parent,
            String message,
            String conversationId) {

        if (parent != null) {
            parent.highCardinalityKeyValue(SESSION_ID, conversationId)
                    .highCardinalityKeyValue(
                            "langfuse.trace.name", TRACE_NAME)
                    .highCardinalityKeyValue(INPUT, message);
        }
    }

    private Observation startWorkflow(
            Observation parent,
            String message,
            String conversationId) {

        Observation workflow = Observation
                .createNotStarted("banking.workflow", observationRegistry)
                .contextualName(TRACE_NAME)
                .highCardinalityKeyValue(SESSION_ID, conversationId)
                .highCardinalityKeyValue(
                        "langfuse.observation.type", "chain")
                .highCardinalityKeyValue(INPUT, message);

        if (parent != null) {
            workflow.parentObservation(parent);
        }

        return workflow.start();
    }

    private static void finish(
            Observation workflow,
            Observation parent,
            StringBuffer answer,
            SignalType signal) {

        try {
            String output = answer.toString();

            workflow.highCardinalityKeyValue(OUTPUT, output);

            if (signal == SignalType.CANCEL) {
                workflow.highCardinalityKeyValue(
                        "banking.stream.cancelled", "true");
            }

            if (parent != null) {
                parent.highCardinalityKeyValue(OUTPUT, output);
            }
        } finally {
            workflow.stop();
        }
    }
}