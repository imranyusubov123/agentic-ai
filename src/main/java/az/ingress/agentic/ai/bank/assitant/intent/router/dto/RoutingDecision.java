package az.ingress.agentic.ai.bank.assitant.intent.router.dto;

import az.ingress.agentic.ai.bank.assitant.intent.router.Domain;

public record RoutingDecision(
        Domain domain,
        String intent,
        TaskType taskType,
        double confidence
) {
}
