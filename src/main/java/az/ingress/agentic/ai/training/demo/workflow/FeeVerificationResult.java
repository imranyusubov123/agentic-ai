package az.ingress.agentic.ai.training.demo.workflow;

import java.math.BigDecimal;

public record FeeVerificationResult(
        String transactionId,
        String cardProduct,
        String transferType,
        BigDecimal transferAmount,
        BigDecimal monthlyAllowance,
        BigDecimal previousUsage,
        BigDecimal remainingAllowance,
        BigDecimal commissionRate,
        BigDecimal expectedFee,
        BigDecimal recordedFee,
        boolean matchesPolicy,
        String reasonCode
) implements java.io.Serializable {
}