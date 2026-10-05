package az.ingress.ai_exprementation.config;

import az.ingress.ai_exprementation.rest.dto.NewsItem;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NewsTools {

    @Tool(description = """
            Returns recent material news about a publicly traded company.
            Use this when investigating developments that could affect
            the company's business or stock.
            """)
    public List<NewsItem> getRecentNews(
            @ToolParam(description = "Stock ticker such as MU or AAPL")
            String ticker) {

        return List.of(
                new NewsItem(
                        "2026-09-17",
                        "Example news item",
                        "Example summary for " + ticker
                ),
                new NewsItem(
                        "2026-09-16",
                        "Another example",
                        "Another example development for " + ticker
                )
        );
    }
}