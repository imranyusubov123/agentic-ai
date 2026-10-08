package az.ingress.agentic.ai.training.dto;

public record SupportIntent(
        Intent intent,
        double confidence,
        boolean requiresAction
) {
}
