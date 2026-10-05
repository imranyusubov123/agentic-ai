package az.ingress.ai_exprementation.rest.dto;

public record TrainingRecommendation(
        String training,
        String level,
        String reason,
        int matchPercentage
) {
}