package az.ingress.agentic.ai.training.controller;

import az.ingress.agentic.ai.config.dto.IntentResult;
import az.ingress.agentic.ai.training.services.AgentRouter;
import az.ingress.agentic.ai.training.services.IntentDetector;
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

    private final IntentDetector intentDetector;
    private final AgentRouter agentRouter;

    @GetMapping("/chat")
    public String chat(
            @RequestParam String message,
            @RequestParam String conversationId) {

        if (message.isBlank() || conversationId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "message and conversationId must not be blank"
            );
        }

        IntentResult result = intentDetector.detect(message);

        return agentRouter.route(result, message, conversationId);
    }
}
