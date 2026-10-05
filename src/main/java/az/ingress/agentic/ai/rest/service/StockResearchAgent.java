package az.ingress.ai_exprementation.rest.service;

import az.ingress.ai_exprementation.config.StockTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class StockResearchAgent {

    private final ChatClient chatClient;
    private final StockTools stockTools;

    public StockResearchAgent(
            ChatClient.Builder builder,
            StockTools stockTools) {

        this.chatClient = builder.build();
        this.stockTools = stockTools;
    }

    public String research(String request) {

        return chatClient
                .prompt()
                .system("""
                        You are a stock research agent.
                        
                        Your job is to investigate companies using
                        the available tools.
                        
                        Do not invent financial information.
                        
                        When necessary:
                        - retrieve the stock price
                        - retrieve company financials
                        - retrieve valuation metrics
                        
                        Determine whether the company appears
                        potentially undervalued.
                        
                        Explain the evidence and important risks.
                        """)
                .user(request)
                .tools(stockTools)
                .call()
                .content();
    }
}