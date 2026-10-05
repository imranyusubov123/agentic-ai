package az.ingress.ai_exprementation.blog;

import az.ingress.ai_exprementation.blog.model.*;
import az.ingress.ai_exprementation.blog.service.*;
import az.ingress.ai_exprementation.blog.store.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BlogWorkflowServiceTest {
    static final Topic TOPIC = new Topic("Spring AI", "Java developers", "Introductory example; verify versions");
    static final ResearchResult RESEARCH = new ResearchResult("Research", List.of("ChatClient"),
            List.of(new Source("s1", "Spring AI", "https://docs.spring.io/spring-ai/reference/", "Official docs")),
            List.of("Sources unverified"));
    static final ArticleDraft DRAFT = new ArticleDraft("Introduction", "# Example", List.of("s1"));
    final List<String> calls = new ArrayList<>();
    final InMemoryBlogWorkflowStore store = new InMemoryBlogWorkflowStore();
    String failAt;
    ArticleDraft draft = DRAFT;
    ReviewResult review = new ReviewResult("Review complete; code not executed", List.of());
    BlogWorkflowService service() {
        return new BlogWorkflowService(new Researcher(null) {
            public ResearchResult research(Topic topic) {
                assertEquals(TOPIC, topic); step("research"); return RESEARCH;
            }
        }, new Writer(null) {
            public ArticleDraft revise(BlogWorkflow workflow, String instructions) {
                step("revise"); return draft;
            }
            public ArticleDraft write(Topic topic, ResearchResult research) {
                assertEquals(TOPIC, topic); assertEquals(RESEARCH, research); step("write"); return draft;
            }
        }, new TechnicalReviewer(null) {
            public ReviewResult review(Topic topic, ResearchResult research, ArticleDraft article) {
                assertEquals(TOPIC, topic); assertEquals(RESEARCH, research); assertEquals(draft, article);
                step("review"); return review;
            }
        }, store, new VisualPlanner(null) {
            public VisualPlan plan(Topic topic, ResearchResult research, ArticleDraft article,
                                   ArticleLayout layout, String instructions) {
                return new VisualPlan(List.of());
            }
        }, new ArticleLayoutService());
    }
    void step(String name) { calls.add(name); if (name.equals(failAt)) throw new IllegalStateException("secret provider body"); }
    @Test void runsInOrderAndStopsForHumanApproval() {
        var service = service();
        var run = service.run(TOPIC);
        assertEquals(List.of("research", "write", "review"), calls);
        assertEquals(WorkflowStatus.AWAITING_HUMAN_APPROVAL, run.status());
        assertEquals(RESEARCH, run.research()); assertEquals(DRAFT, run.draft()); assertEquals(review, run.review());
        assertEquals(run, service.find(run.id()).orElseThrow());
        assertEquals(List.of(WorkflowStatus.CREATED, WorkflowStatus.RESEARCHING, WorkflowStatus.WRITING,
                WorkflowStatus.PLANNING_VISUALS, WorkflowStatus.REVIEWING, WorkflowStatus.AWAITING_HUMAN_APPROVAL),
                run.history().stream().map(WorkflowEvent::status).toList());
        assertThrows(UnsupportedOperationException.class, () -> run.history().clear());
    }
    @Test void blockingReviewIsPreservedAtHumanBoundary() {
        review = new ReviewResult("Needs fixes", List.of(new ReviewIssue(ReviewIssue.Severity.BLOCKER,
                "Example", "Unknown API", "Verify against pinned version")));
        var run = service().run(TOPIC);
        assertEquals(WorkflowStatus.AWAITING_HUMAN_APPROVAL, run.status());
        assertTrue(run.review().hasBlockers());
    }
    @Test void researchFailureStopsDownstreamWork() {
        failAt = "research";
        var run = service().run(TOPIC);
        assertEquals(WorkflowStatus.FAILED, run.status());
        assertEquals(WorkflowStatus.RESEARCHING, run.failedStage());
        assertEquals(List.of("research"), calls); assertNull(run.draft());
        assertFalse(run.error().contains("secret"));
    }
    @Test void writerFailureKeepsResearch() {
        failAt = "write";
        var run = service().run(TOPIC);
        assertEquals(WorkflowStatus.WRITING, run.failedStage());
        assertEquals(RESEARCH, run.research()); assertNull(run.draft());
        assertEquals(List.of("research", "write"), calls);
    }
    @Test void reviewerFailureKeepsResearchAndDraft() {
        failAt = "review";
        var run = service().run(TOPIC);
        assertEquals(WorkflowStatus.REVIEWING, run.failedStage()); assertEquals(DRAFT, run.draft());
        assertEquals(RESEARCH, run.research()); assertNull(run.review());
    }
    @Test void unknownCitationFailsBeforeReview() {
        draft = new ArticleDraft("Title", "Article", List.of("invented"));
        var run = service().run(TOPIC);
        assertEquals(WorkflowStatus.FAILED, run.status()); assertEquals(WorkflowStatus.WRITING, run.failedStage());
        assertEquals(List.of("research", "write"), calls);
    }
    @Test void runsHaveSeparateIdsAndState() {
        var service = service(); var first = service.run(TOPIC); var second = service.run(TOPIC);
        assertNotEquals(first.id(), second.id()); assertEquals(first, store.find(first.id()).orElseThrow());
        assertTrue(service.find(UUID.randomUUID()).isEmpty());
    }
    @Test void validatesInputAndDefensivelyCopiesLists() {
        assertThrows(IllegalArgumentException.class, () -> new Topic(" ", "Java", "Scope"));
        assertThrows(IllegalArgumentException.class, () -> new Source("id", "Title", "file:///tmp/test", "Ref"));
        assertThrows(IllegalArgumentException.class, () -> new ResearchResult("Summary", List.of(),
                List.of(RESEARCH.sources().get(0), RESEARCH.sources().get(0)), List.of()));
        var ids = new ArrayList<>(List.of("s1")); var article = new ArticleDraft("Title", "Text", ids);
        ids.clear(); assertEquals(List.of("s1"), article.sourceIds());
        assertThrows(NullPointerException.class, () -> service().run(null));
    }
    @Test void revisionKeepsRequestIdAndReviewsNewDraft() {
        var service = service(); var first = service.run(TOPIC); calls.clear();
        draft = new ArticleDraft("Revised", "Shorter introduction", List.of("s1"));
        var revised = service.revise(first.requestId(), new RevisionRequest("Shorten introduction"));
        assertEquals(first.id(), revised.requestId());
        assertEquals(first.createdAt(), revised.createdAt());
        assertEquals(draft, revised.draft());
        assertEquals(List.of("revise", "review"), calls);
        assertEquals(WorkflowStatus.AWAITING_HUMAN_APPROVAL, revised.status());
        assertEquals(9, revised.history().size());
    }
    @Test void failedRevisionPreservesDraftAndCanBeRetried() {
        var service = service(); var first = service.run(TOPIC);
        failAt = "review";
        draft = new ArticleDraft("Changed", "Changed text", List.of("s1"));
        var failed = service.revise(first.id(), new RevisionRequest("Change text"));
        assertEquals(WorkflowStatus.FAILED, failed.status());
        assertEquals(first.draft(), failed.draft()); assertEquals(first.review(), failed.review());
        failAt = null;
        assertEquals(draft, service.revise(first.id(), new RevisionRequest("Try again")).draft());
    }
    @Test void revisionRejectsUnknownIdsAndRunsWithoutDrafts() {
        var service = service();
        assertThrows(NoSuchElementException.class,
                () -> service.revise(UUID.randomUUID(), new RevisionRequest("Edit")));
        failAt = "research"; var failed = service.run(TOPIC);
        assertThrows(IllegalStateException.class,
                () -> service.revise(failed.id(), new RevisionRequest("Edit")));
        assertThrows(IllegalArgumentException.class, () -> new RevisionRequest(" "));
    }
    @Test void revisionRejectsInventedSources() {
        var service = service(); var first = service.run(TOPIC); calls.clear();
        draft = new ArticleDraft("Changed", "Text", List.of("unknown"));
        var failed = service.revise(first.id(), new RevisionRequest("Edit"));
        assertEquals(WorkflowStatus.REVISING, failed.failedStage());
        assertEquals(first.draft(), failed.draft()); assertEquals(List.of("revise"), calls);
    }
}
