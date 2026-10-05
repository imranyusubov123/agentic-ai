package az.ingress.ai_exprementation.rest.dto.stocks;

import java.math.BigDecimal;

public record Valuation(
        String ticker,
        BigDecimal forwardPE,
        BigDecimal priceToFreeCashFlow,
        BigDecimal historicalAveragePE
) {
}