package az.ingress.agentic.ai.training.domains.fees;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/agents/fees")
@RequiredArgsConstructor
public class FeesController {

    private final FeesAgent feesAgent;

    @GetMapping
    public Flux<String> chat(
            @RequestParam String message,
            @RequestParam String conversationId) {

        return feesAgent.chat(message, conversationId);
    }
}