package az.ingress.agentic.ai.training.domains.payments.dto;

import java.math.BigDecimal;

public record TransferDetails(
        String transactionId,
        BigDecimal amount,
        String currency,
        String status,
        boolean accountDebited,
        boolean settlementConfirmed
) {
}
