package az.ingress.ai_exprementation.blog.model;

import java.util.Objects;
public record ReviewIssue(Severity severity, String location, String description, String recommendation) {
    public enum Severity { INFO, WARNING, BLOCKER }
    public ReviewIssue {
        Objects.requireNonNull(severity);
        location = Checks.text(location, "location"); description = Checks.text(description, "description");
        recommendation = Checks.text(recommendation, "recommendation");
    }
}
