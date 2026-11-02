package az.ingress.agentic.ai.training.controller;

import az.ingress.agentic.ai.training.demo.service.BankingConversationOrchestrator;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
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

        Observation observation = Observation
                .createNotStarted(
                        "banking.workflow",
                        observationRegistry
                )
                .contextualName("banking-chat")
                .highCardinalityKeyValue(
                        "langfuse.observation.type", "chain"
                )
                .highCardinalityKeyValue(
                        "langfuse.observation.input", message
                );

        return observation.observe(() -> {
            String answer = orchestrator.chat(message, conversationId);

            observation.highCardinalityKeyValue(
                    "langfuse.observation.output",
                    answer == null ? "" : answer
            );

            return answer;
        });
    }
}