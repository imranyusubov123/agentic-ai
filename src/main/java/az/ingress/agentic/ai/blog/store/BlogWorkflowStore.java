package az.ingress.ai_exprementation.blog.store;

import az.ingress.ai_exprementation.blog.model.BlogWorkflow;

import java.util.Optional;
import java.util.UUID;

public interface BlogWorkflowStore {
    java.util.List<BlogWorkflow> findAll();

    void save(BlogWorkflow workflow);

    Optional<BlogWorkflow> find(UUID id);
}
