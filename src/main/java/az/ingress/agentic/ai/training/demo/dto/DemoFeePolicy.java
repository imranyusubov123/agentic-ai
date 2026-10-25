package az.ingress.agentic.ai.training.demo.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DemoFeePolicy(
        String cardProduct,
        String transferType,
        BigDecimal monthlyFreeAllowance,
        BigDecimal commissionRate,
        LocalDate effectiveFrom
) {
}
