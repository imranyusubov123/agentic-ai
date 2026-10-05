package az.ingress.ai_exprementation.blog;

import az.ingress.ai_exprementation.blog.model.*;
import az.ingress.ai_exprementation.blog.service.*;
import az.ingress.ai_exprementation.blog.store.*;
import az.ingress.ai_exprementation.blog.web.BlogWorkflowController;
import org.springframework.ai.chat.client.ChatClient;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static az.ingress.ai_exprementation.blog.BlogWorkflowServiceTest.*;

class BlogVisualsTest {
    final ArticleLayoutService layouts = new ArticleLayoutService();
    VisualSpec spec(String id, String anchor) {
        return new VisualSpec(id, anchor, "Explain request flow", "flowchart LR\nA[Request] --> B[Model]",
                "Request flow", "A request goes to the model.");
    }
    @Test void blocksPreserveCodeFencesAndStableIdsAcrossInsertionsAndReordering() {
        var first = layouts.reconcile("# Title\n\nOne paragraph.\n\n```java\nint a = 1;\n\nint b = 2;\n```\n\nLast paragraph.", ArticleLayout.empty());
        assertEquals(4, first.blocks().size());
        assertTrue(first.blocks().get(2).markdown().contains("\n\nint b"));
        var next = layouts.reconcile("New paragraph.\n\nLast paragraph.\n\n# Title\n\nOne paragraph.", first);
        assertEquals(first.blocks().get(3).id(), next.blocks().get(1).id());
        assertEquals(first.blocks().get(0).id(), next.blocks().get(2).id());
        assertEquals(first.blocks().get(1).id(), next.blocks().get(3).id());
    }
    @Test void duplicateParagraphsHaveDistinctIdsAndTildeFencesStayIntact() {
        var layout = layouts.reconcile("Same.\n\nSame.\n\n~~~java\na\n\nb\n~~~", ArticleLayout.empty());
        assertEquals(3, layout.blocks().size());
        assertNotEquals(layout.blocks().get(0).id(), layout.blocks().get(1).id());
        assertEquals(layout, layouts.reconcile("Same.\n\nSame.\n\n~~~java\na\n\nb\n~~~", layout));
    }
    @Test void placesDiagramExactlyAfterAnchorWithCaptionAndDescription() {
        var layout = layouts.reconcile("Intro.\n\nImplementation.", ArticleLayout.empty());
        layout = layouts.apply(layout, new VisualPlan(List.of(spec("flow", layout.blocks().get(0).id()))));
        var text = layouts.markdown(DRAFT, layout);
        assertTrue(text.indexOf("Intro.") < text.indexOf("**Figure:**"));
        assertTrue(text.indexOf("**Figure:**") < text.indexOf("Implementation."));
        assertTrue(text.contains("**Figure:** Request flow"));
        assertTrue(text.contains("**Description:** A request goes to the model."));
    }
    @Test void removedOrRewrittenAnchorNeedsPlacementAndIsNotExported() {
        var layout = layouts.reconcile("Intro.\n\nImplementation.", ArticleLayout.empty());
        var original = layouts.apply(layout, new VisualPlan(List.of(spec("flow", layout.blocks().get(0).id()))));
        for (String edited : List.of("Implementation.", "Rewritten intro.\n\nImplementation.")) {
            var next = layouts.reconcile(edited, original);
            assertEquals(ArticleVisual.Status.NEEDS_PLACEMENT, next.visuals().get(0).status());
            assertEquals(original.visuals().get(0).spec(), next.visuals().get(0).spec());
            assertFalse(layouts.markdown(DRAFT, next).contains("```mermaid"));
        }
    }
    @Test void otherEditsFlagSurvivingVisualForReview() {
        var layout = layouts.reconcile("Intro.\n\nCode.", ArticleLayout.empty());
        layout = layouts.apply(layout, new VisualPlan(List.of(spec("flow", layout.blocks().get(0).id()))));
        var next = layouts.reconcile("Intro.\n\nChanged code.", layout);
        assertEquals(ArticleVisual.Status.NEEDS_REVIEW, next.visuals().get(0).status());
        assertEquals(layout.blocks().get(0).id(), next.blocks().get(0).id());
    }
    @Test void validatesAnchorsAndUniqueVisualIdsAndAllowsNoVisuals() {
        var layout = layouts.reconcile("Intro.", ArticleLayout.empty());
        assertThrows(IllegalArgumentException.class,
                () -> layouts.apply(layout, new VisualPlan(List.of(spec("flow", "invented")))));
        var s = spec("flow", layout.blocks().get(0).id());
        assertThrows(IllegalArgumentException.class, () -> new VisualPlan(List.of(s, s)));
        assertThrows(IllegalArgumentException.class, () -> new VisualPlan(List.of(
                s, spec("two",s.afterBlockId()), spec("three",s.afterBlockId()), spec("four",s.afterBlockId()))));
        assertTrue(layouts.apply(layout, new VisualPlan(List.of())).visuals().isEmpty());
    }
    @Test void rejectsUnsafeDiagramContent() {
        for (String source : List.of("flowchart LR\nclick A callback", "flowchart LR\n%%{init:{}}%%",
                "flowchart LR\nA[<script>oops</script>]", "flowchart LR\n```", "pie\nA:1"))
            assertThrows(IllegalArgumentException.class,
                    () -> new VisualSpec("flow", "block", "Purpose", source, "Caption", "Alt"));
    }
    @Test void moveValidatesDestinationAndVersionsPlacement() {
        var layout = layouts.reconcile("Intro.\n\nCode.", ArticleLayout.empty());
        var original = layouts.apply(layout, new VisualPlan(List.of(spec("flow", layout.blocks().get(0).id()))));
        var moved = layouts.move(original, "flow", layout.blocks().get(1).id());
        assertEquals(2, moved.visuals().get(0).version());
        assertEquals(ArticleVisual.Status.NEEDS_REVIEW, moved.visuals().get(0).status());
        assertThrows(IllegalArgumentException.class, () -> layouts.move(original, "flow", "missing"));
        assertThrows(NoSuchElementException.class, () -> layouts.move(original, "missing", original.blocks().get(0).id()));
    }
    @Test void plannerParsesStructuredOutputAndReceivesBlocksAndEditorialInstructions() {
        var layout = layouts.reconcile("Intro.", ArticleLayout.empty());
        var anchor = layout.blocks().get(0).id();
        var model = new BlogComponentsTest.StubModel("""
                {"visuals":[{"visualId":"flow","afterBlockId":"%s","purpose":"Explain",
                "mermaid":"flowchart LR\\nA --> B","caption":"Flow","altText":"A goes to B"}]}
                """.formatted(anchor));
        var planner = new VisualPlanner(ChatClient.create(model));
        assertEquals(anchor, planner.plan(TOPIC, RESEARCH, DRAFT, layout, "Place after introduction").visuals().get(0).afterBlockId());
        assertTrue(model.prompt.getContents().contains(anchor));
        assertTrue(model.prompt.getContents().contains("Place after introduction"));
        model.response = "not json";
        assertThrows(RuntimeException.class, () -> planner.plan(TOPIC, RESEARCH, DRAFT, layout, "Edit"));
    }
    @Test void plannerSupportsDifferentEnginesWithinOneArticle() {
        var layout = layouts.reconcile("Structure.\n\nInteractions.\n\nDecisions.", ArticleLayout.empty());
        var model = new BlogComponentsTest.StubModel("""
                {"visuals":[
                  {"visualId":"tree","afterBlockId":"%s","purpose":"Directory hierarchy: Graphviz fits trees",
                   "engine":"GRAPHVIZ","source":"digraph G { root -> home }","caption":"Tree","altText":"Root contains home"},
                  {"visualId":"calls","afterBlockId":"%s","purpose":"Sequence: PlantUML shows ordered calls",
                   "engine":"PLANTUML","source":"@startuml\\nA -> B: call\\n@enduml","caption":"Calls","altText":"A calls B"},
                  {"visualId":"decision","afterBlockId":"%s","purpose":"Decision flow: Mermaid shows branches",
                   "engine":"MERMAID","source":"flowchart LR\\nA --> B","caption":"Flow","altText":"A leads to B"}
                ]}
                """.formatted(layout.blocks().get(0).id(), layout.blocks().get(1).id(), layout.blocks().get(2).id()));
        var plan = new VisualPlanner(ChatClient.create(model)).plan(TOPIC,RESEARCH,DRAFT,layout,"Choose automatically");
        assertEquals(List.of(az.ingress.ai_exprementation.visuals.DiagramEngine.GRAPHVIZ,
                az.ingress.ai_exprementation.visuals.DiagramEngine.PLANTUML,
                az.ingress.ai_exprementation.visuals.DiagramEngine.MERMAID),
                plan.visuals().stream().map(VisualSpec::engine).toList());
        var renderer = org.mockito.Mockito.mock(az.ingress.ai_exprementation.visuals.DiagramService.class);
        org.mockito.Mockito.when(renderer.render(org.mockito.ArgumentMatchers.any())).thenReturn(
                new az.ingress.ai_exprementation.visuals.VisualAsset(UUID.randomUUID(),"/api/visuals/assets/example.png","image/png",20,20));
        var rendered = new ArticleLayoutService(renderer).apply(layout,plan);
        for(var spec:plan.visuals()) org.mockito.Mockito.verify(renderer).render(
                new az.ingress.ai_exprementation.visuals.DiagramRequest(spec.engine(),spec.source()));
        assertTrue(rendered.visuals().stream().allMatch(v -> v.status()==ArticleVisual.Status.READY));
    }
    @Test void workflowPlansReviewsExportsMovesAndPreservesOrphanedVisuals() throws Exception {
        var store = new InMemoryBlogWorkflowStore(); var seen = new ArrayList<String>();
        var service = new BlogWorkflowService(new Researcher(null) {
            public ResearchResult research(Topic t) { return RESEARCH; }
        }, new Writer(null) {
            public ArticleDraft write(Topic t, ResearchResult r) { return new ArticleDraft("Title", "Intro.\n\nCode.", List.of("s1")); }
            public ArticleDraft revise(BlogWorkflow w, String i) { return new ArticleDraft("Title", "Intro.", List.of("s1")); }
        }, new TechnicalReviewer(null) {
            public ReviewResult review(Topic t, ResearchResult r, ArticleDraft d) {
                seen.add(d.markdown()); return new ReviewResult("Reviewed", List.of());
            }
        }, store, new VisualPlanner(null) {
            public VisualPlan plan(Topic t, ResearchResult r, ArticleDraft d, ArticleLayout l, String i) {
                return new VisualPlan(List.of(spec("flow", l.blocks().get(0).id())));
            }
        }, layouts);
        var run = service.run(TOPIC);
        assertEquals(WorkflowStatus.AWAITING_HUMAN_APPROVAL, run.status());
        assertTrue(seen.get(0).contains("flowchart LR"));
        var moved = service.moveVisual(run.id(), "flow", new VisualPlacementRequest(run.layout().blocks().get(1).id()));
        assertEquals(2, moved.layout().visuals().get(0).version());
        assertTrue(service.markdown(run.id()).indexOf("Code.") < service.markdown(run.id()).indexOf("**Figure:**"));
        var edited = service.revise(run.id(), new RevisionRequest("Remove Code paragraph"));
        assertEquals(ArticleVisual.Status.NEEDS_PLACEMENT, edited.layout().visuals().get(0).status());
        assertFalse(service.markdown(run.id()).contains("```mermaid"));
        assertFalse(edited.review().issues().isEmpty());
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(new BlogWorkflowController(service)).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .patch("/api/blog/workflows/" + run.id() + "/visuals/flow/placement")
                .contentType("application/json").content("{\"afterBlockId\":\"unknown\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        assertEquals(edited, service.find(run.id()).orElseThrow());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/blog/workflows/" + run.id() + "/preview"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("securityLevel:'strict'")));
        var replanned = service.planVisuals(run.id(), new RevisionRequest("Place after introduction"));
        assertEquals(ArticleVisual.Status.READY, replanned.layout().visuals().get(0).status());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/blog/workflows/" + run.id() + "/markdown"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
    }
}
