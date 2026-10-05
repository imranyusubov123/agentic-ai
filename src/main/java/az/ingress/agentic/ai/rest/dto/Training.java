package az.ingress.ai_exprementation.rest.dto;

public record Training(
        Long id,
        String name,
        String level,
        int durationWeeks
) {
}