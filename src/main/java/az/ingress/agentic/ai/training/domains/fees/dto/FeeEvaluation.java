package az.ingress.agentic.ai.training.domains.fees.dto;

import java.math.BigDecimal;

public record FeeEvaluation(
        BigDecimal chargedFee,
        BigDecimal expectedFee,
        BigDecimal difference,
        boolean potentialOvercharge
) {
}
