package az.ingress.agentic.ai.training.domains.account;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountsAgent {

    private final ChatClient chatClient;
    private final AccountsTools accountsTools;

    public String chat(String message, String conversationId) {

        return chatClient.prompt()
                .system("""
                    You are an Accounts Banking Agent.
                    Use authorized tools to retrieve account data.
                    Never invent financial information.
                    """)
                .user(message)
                .tools(accountsTools)
                .advisors(advisor -> advisor.param(
                        ChatMemory.CONVERSATION_ID,
                        conversationId
                ))
                .call()
                .content();
    }
}