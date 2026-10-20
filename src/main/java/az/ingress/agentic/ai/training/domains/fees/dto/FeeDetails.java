package az.ingress.agentic.ai.training.domains.fees.dto;

import java.math.BigDecimal;

public record FeeDetails(
        String transactionId,
        BigDecimal chargedFee,
        String currency,
        String customerSegment,
        String transferType
) {
}