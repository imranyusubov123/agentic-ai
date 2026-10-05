package az.ingress.ai_exprementation.blog.model;

import java.util.List;
public record ReviewResult(String summary, List<ReviewIssue> issues) {
    public ReviewResult { summary = Checks.text(summary, "summary"); issues = Checks.list(issues); }
    public boolean hasBlockers() {
        return issues.stream().anyMatch(issue -> issue.severity() == ReviewIssue.Severity.BLOCKER);
    }
}
