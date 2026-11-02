package az.ingress.agentic.ai.training.controller;

import az.ingress.agentic.ai.training.demo.service.BankingConversationOrchestrator;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/banking")
@RequiredArgsConstructor
public class BankingAssistantController {

    private final BankingConversationOrchestrator orchestrator;
    private final ObservationRegistry observationRegistry;

    @GetMapping("/chat")
    public String chat(
            @RequestParam String message,
            @RequestParam String conversationId) {

        if (message == null || message.isBlank()
                || conversationId == null || conversationId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "message and conversationId must not be blank"
            );
        }

        // Spring MVC's current HTTP observation.
        Observation httpObservation =
                observationRegistry.getCurrentObservation();

        if (httpObservation != null) {
            httpObservation.highCardinalityKeyValue(
                    "langfuse.session.id",
                    conversationId
            );

            httpObservation.highCardinalityKeyValue(
                    "langfuse.trace.name",
                    "banking-chat"
            );

            httpObservation.highCardinalityKeyValue(
                    "langfuse.observation.input",
                    message
            );
        }

        Observation workflow = Observation
                .createNotStarted(
                        "banking.workflow",
                        observationRegistry
                )
                .contextualName("banking-chat")
                .highCardinalityKeyValue(
                        "langfuse.session.id",
                        conversationId
                )
                .highCardinalityKeyValue(
                        "langfuse.observation.type",
                        "chain"
                )
                .highCardinalityKeyValue(
                        "langfuse.observation.input",
                        message
                );

        return workflow.observe(() -> {
            String answer = orchestrator.chat(
                    message,
                    conversationId
            );

            String output = answer == null ? "" : answer;

            workflow.highCardinalityKeyValue(
                    "langfuse.observation.output",
                    output
            );

            if (httpObservation != null) {
                httpObservation.highCardinalityKeyValue(
                        "langfuse.observation.output",
                        output
                );
            }

            return answer;
        });

    }
}