package az.ingress.agentic.ai.training.domains.payments.dto;

import java.time.Instant;

public record PaymentEvent(
        String transactionId,
        String eventType,
        Instant occurredAt,
        String description
) {
}
