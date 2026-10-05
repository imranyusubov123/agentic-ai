package az.ingress.ai_exprementation.config;

import az.ingress.ai_exprementation.rest.dto.stocks.Financials;
import az.ingress.ai_exprementation.rest.dto.stocks.StockPrice;
import az.ingress.ai_exprementation.rest.dto.stocks.Valuation;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class StockTools {

    @Tool(description = """
            Returns the current market price of a stock.
            Use when you need the current stock price.
            """)
    public StockPrice getStockPrice(
            @ToolParam(description = "Stock ticker such as MU, AAPL or MSFT")
            String ticker) {

        return new StockPrice(
                ticker,
                new BigDecimal("926.55")
        );
    }


    @Tool(description = """
            Returns fundamental financial information for a company,
            including revenue growth, earnings growth, free cash flow,
            debt and operating margin.
            """)
    public Financials getFinancials(
            @ToolParam(description = "Stock ticker")
            String ticker) {

        return new Financials(
                ticker,
                new BigDecimal("25.1"),
                new BigDecimal("31.4"),
                new BigDecimal("12.5"),
                new BigDecimal("28.2")
        );
    }


    @Tool(description = """
            Returns valuation metrics for a company.
            Use this to determine whether a stock may be expensive
            or cheap relative to earnings and cash flow.
            """)
    public Valuation getValuation(
            @ToolParam(description = "Stock ticker")
            String ticker) {

        return new Valuation(
                ticker,
                new BigDecimal("6.2"),
                new BigDecimal("8.4"),
                new BigDecimal("11.5")
        );
    }
}
