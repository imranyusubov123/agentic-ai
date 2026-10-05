package az.ingress.ai_exprementation.blog.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
/** Immutable snapshot. The approval status never implies a successful technical review. */
public record BlogWorkflow(UUID id, Topic topic, WorkflowStatus status, Instant createdAt,
                           Instant updatedAt, ResearchResult research, ArticleDraft draft,
                           ReviewResult review, WorkflowStatus failedStage, String error,
                           List<WorkflowEvent> history, ArticleLayout layout) {
    public BlogWorkflow(UUID id, Topic topic, WorkflowStatus status, Instant createdAt, Instant updatedAt,
                        ResearchResult research, ArticleDraft draft, ReviewResult review,
                        WorkflowStatus failedStage, String error, List<WorkflowEvent> history) {
        this(id, topic, status, createdAt, updatedAt, research, draft, review,
                failedStage, error, history, ArticleLayout.empty());
    }
    public BlogWorkflow withLayout(ArticleLayout layout) {
        return new BlogWorkflow(id, topic, status, createdAt, updatedAt, research, draft,
                review, failedStage, error, history, layout);
    }
    /** Stable request identifier used to retrieve and revise this article. */
    @com.fasterxml.jackson.annotation.JsonProperty("requestId")
    public UUID requestId() { return id; }

    public BlogWorkflow { history = List.copyOf(history); java.util.Objects.requireNonNull(layout); }
}
