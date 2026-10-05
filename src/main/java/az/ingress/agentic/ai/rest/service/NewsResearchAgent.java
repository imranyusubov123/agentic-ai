package az.ingress.ai_exprementation.rest.service;

import az.ingress.ai_exprementation.config.NewsTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class NewsResearchAgent {

    private final ChatClient chatClient;
    private final NewsTools newsTools;

    public NewsResearchAgent(
            ChatClient.Builder builder,
            NewsTools newsTools) {

        this.chatClient = builder.build();
        this.newsTools = newsTools;
    }

    public String analyze(String ticker) {

        return chatClient
                .prompt()
                .system("""
                        You are a financial news research analyst.
                        
                        Investigate recent developments affecting
                        the company.
                        
                        Identify:
                        - positive catalysts
                        - negative developments
                        - earnings-related news
                        - regulatory issues
                        - geopolitical risks
                        - product announcements
                        - management developments
                        
                        Ignore irrelevant news.
                        
                        Never invent news.
                        """)
                .user("""
                        Analyze recent material news for %s.
                        Explain how the developments could affect
                        the investment thesis.
                        """.formatted(ticker))
                .tools(newsTools)
                .call()
                .content();
    }
}