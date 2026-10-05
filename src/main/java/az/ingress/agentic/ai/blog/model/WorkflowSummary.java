package az.ingress.ai_exprementation.blog.model;

import java.time.Instant;
import java.util.UUID;

/** Lightweight navigation entry; full article content is fetched separately. */
public record WorkflowSummary(UUID id, String title, WorkflowStatus status, Instant updatedAt) {
    public static WorkflowSummary from(BlogWorkflow workflow) {
        return new WorkflowSummary(workflow.id(), workflow.topic().title(), workflow.status(), workflow.updatedAt());
    }
}
