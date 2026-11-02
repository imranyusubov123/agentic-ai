package az.ingress.agentic.ai.training.domains.general;

import az.ingress.agentic.ai.training.services.langfuse.LangfuseAgentAdvisor;
import az.ingress.agentic.ai.training.services.langfuse.ReloadableSystemPrompt;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class GeneralSupportAgent {

    private final ChatClient chatClient;
    private final ReloadableSystemPrompt systemPrompt;

    public GeneralSupportAgent(
            @Qualifier("bankingChatClient") ChatClient chatClient,
            ReloadableSystemPrompt systemPrompt) {

        this.chatClient = chatClient;
        this.systemPrompt = systemPrompt;
    }

    public String chat(String message, String conversationId) {
        String instructions = systemPrompt.get() + """

                
                GENERAL SUPPORT:
                Respond naturally to greetings, thanks and goodbyes.
                Explain your banking support capabilities when asked.
                Use approved information for bank-specific questions.
                Never invent bank products, fees, rates or opening hours.

                Return only the final customer-facing reply
                in natural language.
                Do not output JSON, planning notes or action objects.
                """;

        return chatClient.prompt()
                .system(instructions)
                .user(message)
                .advisors(a -> a
                        .param(
                                ChatMemory.CONVERSATION_ID,
                                conversationId
                        )
                        .param(
                                LangfuseAgentAdvisor.AGENT_NAME,
                                "general-support-agent"
                        )
                        .param(
                                LangfuseAgentAdvisor.SESSION_ID,
                                conversationId
                        )
                )
                .call()
                .content();
    }
}