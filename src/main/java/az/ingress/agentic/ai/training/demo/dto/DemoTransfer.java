package az.ingress.agentic.ai.training.demo.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DemoTransfer(
        String transactionId,
        String cardId,
        LocalDate date,
        BigDecimal amount,
        String currency,
        String transferType,
        String status,
        BigDecimal commission
) {
}
