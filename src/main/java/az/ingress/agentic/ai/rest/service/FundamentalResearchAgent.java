package az.ingress.ai_exprementation.rest.service;

import az.ingress.ai_exprementation.config.StockTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class FundamentalResearchAgent {

    private final ChatClient chatClient;
    private final StockTools stockTools;

    public FundamentalResearchAgent(
            ChatClient.Builder builder,
            StockTools stockTools) {

        this.chatClient = builder.build();
        this.stockTools = stockTools;
    }

    public String analyze(String ticker) {

        return chatClient
                .prompt()
                .system("""
                        You are a fundamental equity research analyst.
                        
                        Analyze the company's:
                        - financial performance
                        - growth
                        - valuation
                        - financial health
                        
                        Use the available tools whenever data is required.
                        
                        Never invent financial data.
                        
                        Explain both positive and negative findings.
                        """)
                .user("Analyze " + ticker)
                .tools(stockTools)
                .call()
                .content();
    }
}
