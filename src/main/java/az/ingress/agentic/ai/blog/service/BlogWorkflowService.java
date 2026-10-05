package az.ingress.ai_exprementation.blog.service;

import az.ingress.ai_exprementation.blog.model.*;
import az.ingress.ai_exprementation.blog.store.BlogWorkflowStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BlogWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(BlogWorkflowService.class);

    private final Researcher researcher;
    private final Writer writer;
    private final TechnicalReviewer reviewer;
    private final BlogWorkflowStore store;
    private final VisualPlanner visualPlanner;
    private final ArticleLayoutService layouts;

    public BlogWorkflowService(Researcher researcher, Writer writer, TechnicalReviewer reviewer,
                               BlogWorkflowStore store, VisualPlanner visualPlanner, ArticleLayoutService layouts) {
        this.researcher = researcher;
        this.writer = writer;
        this.reviewer = reviewer;
        this.store = store;
        this.visualPlanner = visualPlanner;
        this.layouts = layouts;
    }

    // Bounded lock stripes serialize edits to the same article without retaining per-ID locks.
    private final Object[] revisionLocks = java.util.stream.IntStream.range(0, 64)
            .mapToObj(i -> new Object()).toArray();

    public BlogWorkflow revise(UUID id, RevisionRequest request) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(request);
        synchronized (revisionLocks[Math.floorMod(id.hashCode(), revisionLocks.length)]) {
            var original = store.find(id).orElseThrow(() -> new NoSuchElementException("Unknown workflow"));
            if (original.draft() == null || original.research() == null
                    || !(original.status() == WorkflowStatus.AWAITING_HUMAN_APPROVAL
                    || original.status() == WorkflowStatus.FAILED))
                throw new IllegalStateException("Workflow is not ready for revision");
            var run = transition(original, WorkflowStatus.REVISING, original.research(),
                    original.draft(), original.review(), null, null);
            try {
                var draft = Objects.requireNonNull(writer.revise(original, request.instructions()));
                var sourceIds = original.research().sources().stream().map(Source::id).collect(Collectors.toSet());
                if (!sourceIds.containsAll(draft.sourceIds()))
                    throw new IllegalStateException("Draft references unknown source IDs");
                run = transition(run, WorkflowStatus.REVIEWING, original.research(),
                        original.draft(), original.review(), null, null);
                var layout = layouts.reconcile(draft.markdown(), original.layout());
                var review = reviewWithVisuals(original.topic(), original.research(), draft, layout);
                run = run.withLayout(layout);
                writer.remember(id, request.instructions(), draft);
                return transition(run, WorkflowStatus.AWAITING_HUMAN_APPROVAL,
                        original.research(), draft, review, null, null);
            } catch (RuntimeException ex) {
                log.warn("Blog revision {} failed at {} ({})", id, run.status(), ex.getClass().getSimpleName());
                // Keep the last usable draft and its matching review; unsuccessful turns are not remembered.
                return transition(run.withLayout(original.layout()), WorkflowStatus.FAILED, original.research(), original.draft(),
                        original.review(), run.status(), "Revision failed; previous draft retained. You can retry.");
            }
        }
    }

    public List<WorkflowSummary> list() {
        return store.findAll().stream().map(WorkflowSummary::from).toList();
    }

    public Optional<BlogWorkflow> find(UUID id) {
        return store.find(id);
    }

    public BlogWorkflow run(Topic topic) {
        Objects.requireNonNull(topic, "topic");
        var now = Instant.now();
        var run = new BlogWorkflow(UUID.randomUUID(), topic, WorkflowStatus.CREATED, now, now,
                null, null, null, null, null, List.of(new WorkflowEvent(WorkflowStatus.CREATED, now)));
        store.save(run);
        ResearchResult research = null;
        ArticleDraft draft = null;
        ReviewResult review = null;
        try {
            run = transition(run, WorkflowStatus.RESEARCHING, research, draft, review, null, null);
            research = Objects.requireNonNull(researcher.research(topic));
            run = transition(run, WorkflowStatus.WRITING, research, draft, review, null, null);
            draft = Objects.requireNonNull(writer.write(topic, research));
            var sourceIds = research.sources().stream().map(Source::id).collect(Collectors.toSet());
            if (!sourceIds.containsAll(draft.sourceIds()))
                throw new IllegalStateException("Draft references unknown source IDs");
            run = transition(run, WorkflowStatus.PLANNING_VISUALS, research, draft, review, null, null);
            var layout = layouts.reconcile(draft.markdown(), ArticleLayout.empty());
            run = run.withLayout(layout);
            store.save(run); // Preserve anchors even if structured planning fails.
            boolean visualPlanningFailed = false;
            try {
                run = run.withLayout(planWithRetry(topic, research, draft, layout,
                        "Explain only concepts that benefit from a diagram."));
            } catch (RuntimeException ex) {
                visualPlanningFailed = true;
                log.warn("Visual planning {} failed ({})", run.id(), ex.getClass().getSimpleName());
            }
            run = transition(run, WorkflowStatus.REVIEWING, research, draft, review, null, null);
            review = reviewWithVisuals(topic, research, draft, run.layout());
            if (visualPlanningFailed) {
                var issues = new ArrayList<>(review.issues());
                issues.add(new ReviewIssue(ReviewIssue.Severity.WARNING, "Visual planning",
                        "The visual plan could not be generated; the article and paragraph anchors are saved.",
                        "Use Plan visuals to retry. Text review is complete."));
                review = new ReviewResult(review.summary(), issues);
            }
            writer.remember(run.id(), "Create article for " + topic, draft);
            return transition(run, WorkflowStatus.AWAITING_HUMAN_APPROVAL, research, draft, review, null, null);
        } catch (RuntimeException ex) {
            // Do not log provider response bodies, credentials, or article content.
            log.warn("Blog workflow {} failed at {} ({})", run.id(), run.status(), ex.getClass().getSimpleName());
            return transition(run, WorkflowStatus.FAILED, research, draft, review, run.status(),
                    "Stage failed; inspect configuration or structured output and start a new run.");
        }
    }

    private ReviewResult reviewWithVisuals(Topic topic, ResearchResult research, ArticleDraft draft, ArticleLayout layout) {
        var review = Objects.requireNonNull(reviewer.review(topic, research,
                new ArticleDraft(draft.title(), layouts.markdown(draft, layout)
                        + (layout.visuals().isEmpty() ? "" : "\n\nDiagram source for technical verification:\n"
                        + layout.visuals().stream().map(v -> v.spec().visualId() + " (" + v.spec().engine() + ")\n" + v.spec().source())
                            .collect(Collectors.joining("\n\n"))), draft.sourceIds())));
        var issues = new ArrayList<>(review.issues());
        for (var visual : layout.visuals()) if (visual.status() != ArticleVisual.Status.READY)
            issues.add(new ReviewIssue(ReviewIssue.Severity.WARNING, visual.spec().visualId(),
                    "Visual requires attention: " + visual.status(),
                    "Replan visuals or move this visual to a valid block, then review."));
        return new ReviewResult(review.summary(), issues);
    }

    public BlogWorkflow planVisuals(UUID id, RevisionRequest request) {
        return updateVisuals(id, run -> planWithRetry(run.topic(), run.research(), run.draft(),
                run.layout().blocks().isEmpty() ? layouts.reconcile(run.draft().markdown(),run.layout()) : run.layout(), request.instructions()));
    }

    private ArticleLayout planWithRetry(Topic topic, ResearchResult research, ArticleDraft draft,
                                       ArticleLayout layout, String instructions) {
        try { return layouts.apply(layout, visualPlanner.plan(topic,research,draft,layout,instructions)); }
        catch (RuntimeException ex) {
            return layouts.apply(layout, visualPlanner.plan(topic,research,draft,layout,instructions
                    + "\nThe previous plan was rejected. Return valid structured fields, unique IDs, exact supplied block IDs,"
                    + " and plain labels. No HTML, external resources or directives. Use at most three visuals."));
        }
    }

    public BlogWorkflow retryVisual(UUID id, String visualId) {
        return updateVisuals(id, run -> layouts.retry(run.layout(),visualId));
    }

    public BlogWorkflow moveVisual(UUID id, String visualId, VisualPlacementRequest request) {
        return updateVisuals(id, run -> {
            var layout = layouts.move(run.layout(), visualId, request.afterBlockId());
            // This placement change is reviewed immediately below; other stale visuals remain pending.
            return new ArticleLayout(layout.blocks(), layout.visuals().stream().map(v ->
                    v.spec().visualId().equals(visualId)
                    ? (v.status() == ArticleVisual.Status.RENDER_FAILED ? v : v.withStatus(ArticleVisual.Status.READY)) : v).toList());
        });
    }

    private BlogWorkflow updateVisuals(UUID id, java.util.function.Function<BlogWorkflow, ArticleLayout> change) {
        synchronized (revisionLocks[Math.floorMod(id.hashCode(), revisionLocks.length)]) {
            var original = store.find(id).orElseThrow(() -> new NoSuchElementException("Unknown workflow"));
            if (original.draft() == null || original.research() == null ||
                    !(original.status() == WorkflowStatus.AWAITING_HUMAN_APPROVAL || original.status() == WorkflowStatus.FAILED))
                throw new IllegalStateException("Workflow is not ready for editing");
            // Validate/build before saving; a rejected placement cannot overwrite the existing article.
            var layout = change.apply(original);
            var run = transition(original, WorkflowStatus.REVIEWING, original.research(), original.draft(),
                    original.review(), null, null);
            try {
                var review = reviewWithVisuals(original.topic(), original.research(), original.draft(), layout);
                return transition(run.withLayout(layout), WorkflowStatus.AWAITING_HUMAN_APPROVAL,
                        original.research(), original.draft(), review, null, null);
            } catch (RuntimeException ex) {
                log.warn("Visual review {} failed ({})", id, ex.getClass().getSimpleName());
                return transition(run.withLayout(original.layout()), WorkflowStatus.FAILED, original.research(), original.draft(),
                        original.review(), WorkflowStatus.REVIEWING, "Visual review failed; previous layout retained.");
            }
        }
    }

    /** Manual editing makes no model calls. Prior automated review is explicitly invalidated. */
    public BlogWorkflow editContent(UUID id, ContentEditRequest request) {
        synchronized (revisionLocks[Math.floorMod(id.hashCode(), revisionLocks.length)]) {
            var original = editableSnapshot(id,request.expectedUpdatedAt());
            var draft = new ArticleDraft(request.title(), DiagramMarkdown.withoutDiagramSource(request.markdown()), original.draft().sourceIds());
            var layout = layouts.reconcile(draft.markdown(),original.layout());
            var saved = transition(original.withLayout(layout), WorkflowStatus.AWAITING_HUMAN_APPROVAL,
                    original.research(),draft,manualReview("Article content changed manually."),null,null);
            writer.remember(id,"Manual article edit; use this as the current draft.",draft);
            return saved;
        }
    }

    public BlogWorkflow editDiagram(UUID id, String visualId, DiagramEditRequest request) {
        synchronized (revisionLocks[Math.floorMod(id.hashCode(), revisionLocks.length)]) {
            var original = editableSnapshot(id,request.expectedUpdatedAt());
            var old = original.layout().visuals().stream().filter(v -> v.spec().visualId().equals(visualId))
                    .findFirst().orElseThrow(() -> new NoSuchElementException("Unknown visual"));
            var s=old.spec();
            var spec=new VisualSpec(visualId,s.afterBlockId(),s.purpose(),null,request.caption(),request.altText(),request.engine(),request.source());
            var rendered=layouts.render(new ArticleVisual(spec,old.version()+1,ArticleVisual.Status.NEEDS_REVIEW));
            if(rendered.status()==ArticleVisual.Status.RENDER_FAILED)
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                        "Diagram could not render; previous image retained. Check syntax and Kroki availability.");
            if(original.layout().blocks().stream().noneMatch(b -> b.id().equals(spec.afterBlockId())))
                rendered=rendered.withStatus(ArticleVisual.Status.NEEDS_PLACEMENT);
            var replacement=rendered;
            var layout=new ArticleLayout(original.layout().blocks(),original.layout().visuals().stream()
                    .map(v -> v.spec().visualId().equals(visualId)?replacement:v).toList());
            return transition(original.withLayout(layout),WorkflowStatus.AWAITING_HUMAN_APPROVAL,
                    original.research(),original.draft(),manualReview("Diagram " + visualId + " changed manually."),null,null);
        }
    }

    private BlogWorkflow editableSnapshot(UUID id, Instant expectedUpdatedAt) {
        var run=store.find(id).orElseThrow(() -> new NoSuchElementException("Unknown workflow"));
        if(!run.updatedAt().equals(expectedUpdatedAt))throw new IllegalStateException("Article changed since the editor was opened");
        if(run.draft()==null || run.research()==null || !(run.status()==WorkflowStatus.AWAITING_HUMAN_APPROVAL || run.status()==WorkflowStatus.FAILED))
            throw new IllegalStateException("Workflow is not ready for editing");
        return run;
    }

    private ReviewResult manualReview(String change) {
        return new ReviewResult("Manual edits saved; previous automated review is out of date.",List.of(
                new ReviewIssue(ReviewIssue.Severity.WARNING,"Manual edit",change,
                        "Review the updated article and diagrams before publishing. No automated review was run for this edit.")));
    }

    public String markdown(UUID id) {
        var run = store.find(id).orElseThrow(() -> new NoSuchElementException("Unknown workflow"));
        if (run.draft() == null) throw new IllegalStateException("No draft available");
        return layouts.markdown(run.draft(), run.layout());
    }

    private BlogWorkflow transition(BlogWorkflow previous, WorkflowStatus status, ResearchResult research,
                                    ArticleDraft draft, ReviewResult review, WorkflowStatus failedStage, String error) {
        var now = Instant.now();
        var history = new ArrayList<>(previous.history());
        history.add(new WorkflowEvent(status, now));
        var next = new BlogWorkflow(previous.id(), previous.topic(), status, previous.createdAt(), now,
                research, draft, review, failedStage, error, history, previous.layout());
        store.save(next);
        log.info("Blog workflow {} status={}", next.id(), status);
        return next;
    }
}
