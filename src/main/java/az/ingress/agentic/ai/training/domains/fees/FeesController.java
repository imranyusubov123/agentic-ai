package az.ingress.agentic.ai.training.domains.fees;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/agents/fees")
@RequiredArgsConstructor
public class FeesController {

    private final FeesAgent feesAgent;

    @GetMapping
    public String chat(
            @RequestParam String message,
            @RequestParam String conversationId) {

        return feesAgent.chat(message, conversationId);
    }
}