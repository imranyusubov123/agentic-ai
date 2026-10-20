package az.ingress.agentic.ai.training.domains.account;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AccountsController {

    private final AccountsAgent accountsAgent;

    @GetMapping("/agents/accounts")
    public String chat(
            @RequestParam String message,
            @RequestParam String conversationId) {

        return accountsAgent.chat(message, conversationId);
    }
}