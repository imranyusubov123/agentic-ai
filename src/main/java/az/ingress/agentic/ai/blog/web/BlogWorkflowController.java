package az.ingress.ai_exprementation.blog.web;

import az.ingress.ai_exprementation.blog.model.*;
import az.ingress.ai_exprementation.blog.service.BlogWorkflowService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

@RestController
@RequestMapping("/api/blog/workflows")
public class BlogWorkflowController {

    private final BlogWorkflowService workflows;

    public BlogWorkflowController(BlogWorkflowService workflows) { this.workflows = workflows; }

    @GetMapping
    public java.util.List<WorkflowSummary> list() { return workflows.list(); }

    @PostMapping
    public BlogWorkflow run(@RequestBody Topic topic) { return workflows.run(topic); }

    @PostMapping("/{requestId}/revisions")
    public BlogWorkflow revise(@PathVariable UUID requestId, @RequestBody RevisionRequest request) {
        try {
            return workflows.revise(requestId, request);
        } catch (java.util.NoSuchElementException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown workflow");
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Workflow is not ready for revision");
        }
    }

    @PostMapping("/{id}/visuals/plan")
    public BlogWorkflow planVisuals(@PathVariable UUID id, @RequestBody RevisionRequest request) {
        return visualOperation(() -> workflows.planVisuals(id, request));
    }

    @PostMapping("/{id}/visuals/{visualId}/retry")
    public BlogWorkflow retryVisual(@PathVariable UUID id, @PathVariable String visualId) {
        return visualOperation(() -> workflows.retryVisual(id, visualId));
    }

    @PatchMapping("/{id}/visuals/{visualId}/placement")
    public BlogWorkflow moveVisual(@PathVariable UUID id, @PathVariable String visualId,
                                  @RequestBody VisualPlacementRequest request) {
        return visualOperation(() -> workflows.moveVisual(id, visualId, request));
    }

    @PatchMapping("/{id}/content")
    public BlogWorkflow editContent(@PathVariable UUID id, @RequestBody ContentEditRequest request) {
        return visualOperation(() -> workflows.editContent(id,request));
    }

    @PatchMapping("/{id}/visuals/{visualId}")
    public BlogWorkflow editDiagram(@PathVariable UUID id, @PathVariable String visualId, @RequestBody DiagramEditRequest request) {
        return visualOperation(() -> workflows.editDiagram(id,visualId,request));
    }

    @GetMapping(value = "/{id}/markdown", produces = "text/markdown;charset=UTF-8")
    public String markdown(@PathVariable UUID id) {
        return visualOperation(() -> workflows.markdown(id));
    }

    @GetMapping(value = "/{id}/preview", produces = "text/html;charset=UTF-8")
    public String preview(@PathVariable UUID id) throws java.io.IOException {
        visualOperation(() -> workflows.markdown(id));
        return new org.springframework.core.io.ClassPathResource("blog/preview.html")
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }

    private <T> T visualOperation(java.util.function.Supplier<T> operation) {
        try { return operation.get(); }
        catch (java.util.NoSuchElementException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown workflow or visual");
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid visual plan or placement");
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Workflow is not ready for editing");
        }
    }

    @GetMapping("/{id}")
    public BlogWorkflow find(@PathVariable UUID id) {
        return workflows.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
