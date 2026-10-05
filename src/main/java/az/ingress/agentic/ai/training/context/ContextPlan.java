package az.ingress.agentic.ai.training.context;

public record ContextPlan(
        boolean useMemory,
        boolean useRag,
        boolean allowTools
) {
}
