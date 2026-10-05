package az.ingress.agentic.ai.training;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class BankAssistant {

    private final ChatClient chatClient;

    @GetMapping("/bank")
    public String chat(
            @RequestParam(defaultValue = "Hello") String message) {
        return chatClient.prompt()
                .user(message)
                .call()
                .content();
    }
}