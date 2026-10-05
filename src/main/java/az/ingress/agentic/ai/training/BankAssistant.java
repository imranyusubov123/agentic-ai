package az.ingress.agentic.ai.training;

import az.ingress.agentic.ai.training.dto.SupportResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class BankAssistant {

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;

    @Value("classpath:/prompts/support-user.st")
    private Resource userPrompt;

    @GetMapping("/bank")
    public SupportResponse chat(
            @RequestParam String message,
            @RequestParam String conversationId,
            @RequestParam(defaultValue = "English") String language,
            @RequestParam(defaultValue = "Web") String channel) {

        return chatClient.prompt()
                .user(user -> user
                        .text(userPrompt)
                        .param("message", message)
                        .param("language", language)
                        .param("channel", channel))
                .advisors(advisor -> advisor
                        .param(
                                ChatMemory.CONVERSATION_ID,
                                conversationId
                        ))
                .call()
                .entity(SupportResponse.class);
    }

    @GetMapping("/bank/memory")
    public Object memory(@RequestParam String conversationId) {
        return chatMemory.get(conversationId);
    }
}