package az.ingress.ai_exprementation.rest.dto.stocks;

import java.math.BigDecimal;

public record Financials(
        String ticker,
        BigDecimal revenueGrowth,
        BigDecimal epsGrowth,
        BigDecimal freeCashFlow,
        BigDecimal operatingMargin
) {
}