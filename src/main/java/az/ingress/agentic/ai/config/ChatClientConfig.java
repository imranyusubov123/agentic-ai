package az.ingress.agentic.ai.config;

import az.ingress.agentic.ai.training.services.langfuse.LangfuseAgentAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class ChatClientConfig {

    @Bean
    public ChatMemory chatMemory(JdbcChatMemoryRepository repository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(3)
                .build();
    }

    @Bean
    @Primary
    public ChatClient bankingChatClient(
            OllamaChatModel model,
            ChatMemory chatMemory,
            LangfuseAgentAdvisor langfuseAgentAdvisor) {

        return ChatClient.builder(model)
                .defaultAdvisors(
                        langfuseAgentAdvisor,
                        MessageChatMemoryAdvisor.builder(chatMemory)
                                .build()
                )
                .build();
    }

    @Bean
    public ChatClient intentChatClient(
            OllamaChatModel model,
            LangfuseAgentAdvisor langfuseAgentAdvisor) {

        return ChatClient.builder(model)
                .defaultAdvisors(langfuseAgentAdvisor)
                .build();
    }

    @Bean("supervisorChatClient")
    public ChatClient supervisorChatClient(
            OllamaChatModel model,
            LangfuseAgentAdvisor langfuseAgentAdvisor) {

        return ChatClient.builder(model)
                .defaultAdvisors(langfuseAgentAdvisor)
                .build();
    }
}