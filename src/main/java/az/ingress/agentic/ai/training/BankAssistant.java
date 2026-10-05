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
                .system("""
                        You are a customer support assistant for ABC Bank.
                        
                        Your responsibilities:
                        - Help customers with banking-related questions.
                        - Keep answers concise and easy to understand.
                        - Never invent customer or transaction information.
                        - If you don't know something, say that you don't know.
                        """)
                .user(message)
                .call()
                .content();
    }
}