package az.ingress.agentic.ai.training.demo.controller;

import az.ingress.agentic.ai.training.demo.service.TransferInvestigationService;
import az.ingress.agentic.ai.training.demo.service.TransferInvestigationService.InvestigationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/investigations")
@RequiredArgsConstructor
public class TransferInvestigationController {

    private final TransferInvestigationService investigationService;

    @PostMapping
    public InvestigationResponse start(
            @RequestBody StartRequest request) {

        return investigationService.start(
                request.conversationId(),
                request.message()
        );
    }

    @PostMapping("/{investigationId}/select")
    public InvestigationResponse select(
            @PathVariable String investigationId,
            @RequestBody SelectionRequest request) {

        return investigationService.select(
                investigationId,
                request.conversationId(),
                request.transactionId()
        );
    }

    public record StartRequest(
            String conversationId,
            String message
    ) {
    }

    public record SelectionRequest(
            String conversationId,
            String transactionId
    ) {
    }
}