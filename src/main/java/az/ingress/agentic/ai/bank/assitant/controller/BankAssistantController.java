package az.ingress.agentic.ai.bank.assitant.controller;

import az.ingress.agentic.ai.bank.assitant.service.BankAssistantService;
import az.ingress.agentic.ai.training.dto.SupportResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class BankAssistantController {

    private final BankAssistantService assistantService;

    public BankAssistantController(
            BankAssistantService assistantService) {
        this.assistantService = assistantService;
    }

    @GetMapping("/bank")
    public SupportResponse chat(
            @RequestParam String message,
            @RequestParam String conversationId,
            @RequestParam(required = false) String ingestionId,
            @RequestParam(defaultValue = "English") String language,
            @RequestParam(defaultValue = "Web") String channel) {

        if (message == null || message.isBlank()
                || conversationId == null || conversationId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "message and conversationId must not be blank."
            );
        }

        return assistantService.chat(
                message,
                conversationId,
                ingestionId,
                language,
                channel
        );
    }
}