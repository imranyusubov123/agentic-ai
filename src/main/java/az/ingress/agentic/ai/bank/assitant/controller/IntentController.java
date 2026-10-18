package az.ingress.agentic.ai.bank.assitant.controller;

import az.ingress.agentic.ai.bank.assitant.intent.router.IntentRouter;
import az.ingress.agentic.ai.bank.assitant.intent.router.dto.RouteRequest;
import az.ingress.agentic.ai.bank.assitant.intent.router.dto.RoutingDecision;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class IntentController {

    private final IntentRouter intentRouter;

    @PostMapping("/route")
    public RoutingDecision route(
            @RequestBody RouteRequest request
    ) {
        return intentRouter.route(request.message());
    }
}