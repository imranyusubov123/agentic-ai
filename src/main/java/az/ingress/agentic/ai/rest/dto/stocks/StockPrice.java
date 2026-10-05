package az.ingress.ai_exprementation.rest.dto.stocks;

import java.math.BigDecimal;

public record StockPrice(
        String ticker,
        BigDecimal price
) {
}
