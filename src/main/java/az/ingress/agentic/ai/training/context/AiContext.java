package az.ingress.agentic.ai.training.context;

public record AiContext(
        String conversationId,
        String language,
        String channel,
        String knowledgeContext,
        ContextPlan plan
) {
}
