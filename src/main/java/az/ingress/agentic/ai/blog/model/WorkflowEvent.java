package az.ingress.ai_exprementation.blog.model;

import java.time.Instant;
public record WorkflowEvent(WorkflowStatus status, Instant at) {}
