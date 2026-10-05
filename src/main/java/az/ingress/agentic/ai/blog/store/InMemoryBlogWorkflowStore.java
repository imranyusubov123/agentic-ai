package az.ingress.ai_exprementation.blog.store;

import az.ingress.ai_exprementation.blog.model.BlogWorkflow;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-local storage; replace with durable storage before production use.
 */
@Repository
public class InMemoryBlogWorkflowStore implements BlogWorkflowStore {
    private final ConcurrentHashMap<UUID, BlogWorkflow> workflows = new ConcurrentHashMap<>();

    public java.util.List<BlogWorkflow> findAll() {
        return workflows.values().stream()
                .sorted(java.util.Comparator.comparing(BlogWorkflow::updatedAt).reversed()
                        .thenComparing(BlogWorkflow::id)).toList();
    }

    public void save(BlogWorkflow workflow) {
        workflows.put(workflow.id(), workflow);
    }

    public Optional<BlogWorkflow> find(UUID id) {
        return Optional.ofNullable(workflows.get(id));
    }
}
