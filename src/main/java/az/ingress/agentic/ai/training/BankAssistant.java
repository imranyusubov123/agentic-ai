package az.ingress.agentic.ai.training;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class BankAssistant {

    private final ChatClient chatClient;

    @Value("classpath:/prompts/support-user.st")
    private Resource userPrompt;

    public String chat(
            String message,
            String language,
            String channel) {

        return chatClient.prompt()
                .user(user -> user
                        .text(userPrompt)
                        .param("message", message)
                        .param("language", language)
                        .param("channel", channel))
                .call()
                .content();
    }
}