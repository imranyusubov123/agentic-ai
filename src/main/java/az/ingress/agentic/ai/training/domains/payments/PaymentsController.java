package az.ingress.agentic.ai.training.domains.payments;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PaymentsController {

    private final PaymentsAgent paymentsAgent;

    @GetMapping("/agents/payments")
    public String chat(
            @RequestParam String message,
            @RequestParam String conversationId) {

        if (message == null || message.isBlank()
                || conversationId == null
                || conversationId.isBlank()) {
            throw new IllegalArgumentException(
                    "message and conversationId are required"
            );
        }

        return paymentsAgent.chat(message, conversationId);
    }
}