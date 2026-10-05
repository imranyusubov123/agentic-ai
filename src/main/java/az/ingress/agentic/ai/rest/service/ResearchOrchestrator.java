package az.ingress.ai_exprementation.rest.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class ResearchOrchestrator {

    private final FundamentalResearchAgent fundamentalAgent;
    private final NewsResearchAgent newsAgent;
    private final ChatClient chatClient;

    public ResearchOrchestrator(
            FundamentalResearchAgent fundamentalAgent,
            NewsResearchAgent newsAgent,
            ChatClient.Builder builder) {

        this.fundamentalAgent = fundamentalAgent;
        this.newsAgent = newsAgent;
        this.chatClient = builder.build();
    }

    public String analyze(String ticker) {

        String fundamentals =
                fundamentalAgent.analyze(ticker);

        String news =
                newsAgent.analyze(ticker);

        return synthesize(
                ticker,
                fundamentals,
                news
        );
    }

    private String synthesize(
            String ticker,
            String fundamentals,
            String news) {

        return chatClient
                .prompt()
                .system("""
                        You are a senior equity research analyst.
                        
                        Combine research produced by specialist analysts.
                        
                        Do not invent information.
                        
                        Identify:
                        - strongest parts of the investment thesis
                        - weaknesses
                        - important catalysts
                        - important risks
                        - contradictions between the research
                        
                        Produce a concise research summary.
                        """)
                .user("""
                        Company: %s
                        
                        FUNDAMENTAL ANALYSIS
                        --------------------
                        %s
                        
                        NEWS ANALYSIS
                        --------------------
                        %s
                        """.formatted(
                        ticker,
                        fundamentals,
                        news
                ))
                .call()
                .content();
    }
}
