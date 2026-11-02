package az.ingress.agentic.ai.training.domains.account;

import az.ingress.agentic.ai.training.services.langfuse.LangfuseAgentAdvisor;
import az.ingress.agentic.ai.training.services.langfuse.ReloadableSystemPrompt;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class AccountsAgent {

    private final ChatClient chatClient;
    private final AccountsTools accountsTools;
    private final ReloadableSystemPrompt systemPrompt;

    public AccountsAgent(
            @Qualifier("bankingChatClient") ChatClient chatClient,
            AccountsTools accountsTools,
            ReloadableSystemPrompt systemPrompt) {

        this.chatClient = chatClient;
        this.accountsTools = accountsTools;
        this.systemPrompt = systemPrompt;
    }

    public String chat(String message, String conversationId) {
        return chat(message, conversationId, conversationId);
    }

    public String chat(
            String message,
            String memoryConversationId,
            String sessionId) {

        String instructions = systemPrompt.get() + """
                
                
                ACCOUNT SUPPORT:
                - Answer account-related banking questions.
                - Use authorized tools to retrieve account data.
                - Never invent balances, transactions,
                  account details or other financial information.
                - Never infer payment settlement solely
                  from an account debit.
                
                Return the final customer-facing answer in natural language.
                Do not output planning notes or action objects.
                """;

        return chatClient.prompt()
                .system(instructions)
                .user(message)
                .tools(accountsTools)
                .advisors(a -> a
                        .param(
                                ChatMemory.CONVERSATION_ID,
                                memoryConversationId
                        )
                        .param(
                                LangfuseAgentAdvisor.AGENT_NAME,
                                "accounts-agent"
                        )
                        .param(
                                LangfuseAgentAdvisor.SESSION_ID,
                                sessionId
                        )
                )
                .call()
                .content();
    }
}