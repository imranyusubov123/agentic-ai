package az.ingress.agentic.ai.training.dto;

public record SupportResponse(
        String answer,
        String intent,
        double confidence,
        boolean requiresAction,
        InformationSource source
) {}