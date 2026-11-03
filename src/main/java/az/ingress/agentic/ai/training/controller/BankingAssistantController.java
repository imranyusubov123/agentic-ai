package az.ingress.agentic.ai.training.controller;

import az.ingress.agentic.ai.training.demo.service.BankingConversationOrchestrator;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

@RestController
@RequestMapping("/banking")
@RequiredArgsConstructor
public class BankingAssistantController {

    private final BankingConversationOrchestrator orchestrator;
    private final ObservationRegistry observationRegistry;

    // JSON event data preserves whitespace and newlines in text chunks.
    public record ChatEvent(String text) {
    }

    @GetMapping(
            value = "/chat",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public Flux<ServerSentEvent<ChatEvent>> chat(
            @RequestParam String message,
            @RequestParam String conversationId) {

        if (message == null || message.isBlank()
                || conversationId == null || conversationId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "message and conversationId must not be blank"
            );
        }

        Observation httpObservation =
                observationRegistry.getCurrentObservation();

        if (httpObservation != null) {
            httpObservation
                    .highCardinalityKeyValue(
                            "langfuse.session.id", conversationId)
                    .highCardinalityKeyValue(
                            "langfuse.trace.name", "banking-chat")
                    .highCardinalityKeyValue(
                            "langfuse.observation.input", message);
        }

        return Flux.defer(() -> {
            StringBuffer answer = new StringBuffer();

            Observation workflow = Observation
                    .createNotStarted(
                            "banking.workflow",
                            observationRegistry
                    )
                    .contextualName("banking-chat")
                    .highCardinalityKeyValue(
                            "langfuse.session.id", conversationId)
                    .highCardinalityKeyValue(
                            "langfuse.observation.type", "chain")
                    .highCardinalityKeyValue(
                            "langfuse.observation.input", message);

            if (httpObservation != null) {
                workflow.parentObservation(httpObservation);
            }

            workflow.start();

            Flux<ServerSentEvent<ChatEvent>> response =
                    Flux.defer(() ->
                                    orchestrator.chat(message, conversationId)
                            )
                            .doOnNext(answer::append)
                            .map(chunk -> event("delta", chunk));

            return Flux.concat(
                            Flux.just(event(
                                    "status", "Processing your request…")),
                            response,
                            Flux.just(event("done", ""))
                    )
                    .doOnError(workflow::error)
                    .onErrorResume(error ->
                            Flux.just(event(
                                    "error",
                                    "Unable to complete your request. Please try again."
                            ))
                    )
                    .doFinally(signal -> {
                        String output = answer.toString();

                        workflow.highCardinalityKeyValue(
                                "langfuse.observation.output", output);

                        if (signal == SignalType.CANCEL) {
                            workflow.highCardinalityKeyValue(
                                    "banking.stream.cancelled", "true");
                        }

                        if (httpObservation != null) {
                            httpObservation.highCardinalityKeyValue(
                                    "langfuse.observation.output", output);
                        }

                        workflow.stop();
                    })
                    .contextWrite(context -> context.put(
                            ObservationThreadLocalAccessor.KEY,
                            workflow
                    ));
        });
    }

    private static ServerSentEvent<ChatEvent> event(
            String name,
            String text) {

        return ServerSentEvent.<ChatEvent>builder()
                .event(name)
                .data(new ChatEvent(text))
                .build();
    }
}