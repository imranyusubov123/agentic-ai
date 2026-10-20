package az.ingress.agentic.ai.training.domains.account.dto;

import java.math.BigDecimal;

public record AccountBalance(
        String accountId,
        BigDecimal availableBalance,
        String currency
) {
}