package az.ingress.agentic.ai.training.controller;

import az.ingress.agentic.ai.training.services.BankingFeedbackService;
import az.ingress.agentic.ai.training.services.langfuse.ObservedBankingConversation;
import jakarta.servlet.http.HttpSession;
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

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@RestController
@RequestMapping("/banking")
@RequiredArgsConstructor
public class BankingAssistantController {

    private final ObservedBankingConversation conversation;
    private final BankingFeedbackService feedbackService;

    @GetMapping(
            value = "/chat",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public Flux<ServerSentEvent<ChatEvent>> chat(
            @RequestParam String message,
            @RequestParam String conversationId,
            @RequestParam UUID messageId,
            HttpSession session) {

        validate(message, conversationId);

        final UUID conversationUuid;
        try {
            conversationUuid = UUID.fromString(conversationId);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "conversationId must be a UUID"
            );
        }

        AtomicReference<String> traceId = new AtomicReference<>();

        Flux<ServerSentEvent<ChatEvent>> deltas = conversation
                .chat(message, conversationId, traceId::set)
                .map(chunk -> event("delta", chunk));

        Flux<ServerSentEvent<ChatEvent>> completion = Flux.defer(() -> {
            String capturedTraceId = traceId.get();

            if (capturedTraceId == null || capturedTraceId.isBlank()) {
                return Flux.<ServerSentEvent<ChatEvent>>error(
                        new IllegalStateException("Chat trace ID was not captured")
                );
            }

            feedbackService.registerCompletedAnswer(
                    session,
                    conversationUuid,
                    messageId,
                    capturedTraceId
            );

            return Flux.just(event("done", ""));
        });

        return Flux.concat(
                Flux.just(event("status", "Processing your request…")),
                deltas,
                completion
        ).onErrorResume(error -> Flux.just(
                event("error", "Unable to complete your request. Please try again.")
        ));
    }

    private static void validate(String message, String conversationId) {
        if (message == null || message.isBlank()
                || conversationId == null || conversationId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "message and conversationId must not be blank");
        }
    }

    private static ServerSentEvent<ChatEvent> event(String name, String text) {
        return ServerSentEvent.<ChatEvent>builder()
                .event(name)
                .data(new ChatEvent(text))
                .build();
    }
}
