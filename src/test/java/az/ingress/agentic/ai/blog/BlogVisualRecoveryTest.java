package az.ingress.ai_exprementation.blog;

import az.ingress.ai_exprementation.blog.model.*;
import az.ingress.ai_exprementation.blog.service.*;
import az.ingress.ai_exprementation.blog.store.InMemoryBlogWorkflowStore;
import az.ingress.ai_exprementation.visuals.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static az.ingress.ai_exprementation.blog.BlogWorkflowServiceTest.*;

class BlogVisualRecoveryTest {
    @Test void plannerFailurePreservesAnchorsAndStillReviewsTextThenCanReplan() {
        var planner=mock(VisualPlanner.class);
        when(planner.plan(any(),any(),any(),any(),any())).thenThrow(new IllegalArgumentException("Invalid visualId"));
        var store=new InMemoryBlogWorkflowStore();
        var researcher=mock(Researcher.class);when(researcher.research(TOPIC)).thenReturn(RESEARCH);
        var writer=mock(Writer.class);when(writer.write(TOPIC,RESEARCH)).thenReturn(DRAFT);
        var reviewer=mock(TechnicalReviewer.class);when(reviewer.review(any(),any(),any())).thenReturn(new ReviewResult("Reviewed",List.of()));
        var service=new BlogWorkflowService(researcher,writer,reviewer,store,planner,new ArticleLayoutService());
        var run=service.run(TOPIC);
        assertEquals(WorkflowStatus.AWAITING_HUMAN_APPROVAL,run.status());assertEquals(DRAFT,run.draft());
        assertFalse(run.layout().blocks().isEmpty());assertEquals("Visual planning",run.review().issues().get(0).location());
        verify(planner,times(2)).plan(any(),any(),any(),any(),any());
        doReturn(new VisualPlan(List.of())).when(planner).plan(any(),any(),any(),any(),any());
        assertTrue(service.planVisuals(run.id(),new RevisionRequest("Try again")).review().issues().isEmpty());
    }
    @Test void oneFailedRenderDoesNotDiscardSuccessfulVisualAndCanRetryOnlyFailure() {
        var diagrams=mock(DiagramService.class);
        var asset=new VisualAsset(UUID.randomUUID(),"/api/visuals/assets/test.png","image/png",20,20);
        when(diagrams.render(any())).thenReturn(asset).thenThrow(new IllegalStateException("Unavailable")).thenReturn(asset);
        var layouts=new ArticleLayoutService(diagrams);
        var base=layouts.reconcile("First paragraph.\n\nSecond paragraph.",ArticleLayout.empty());
        var specs=List.of(new VisualSpec("first",base.blocks().get(0).id(),"Explain","flowchart LR\nA --> B","Caption","Description"),
                new VisualSpec("second",base.blocks().get(1).id(),"Explain","flowchart LR\nB --> C","Caption","Description"));
        var layout=layouts.apply(base,new VisualPlan(specs));
        assertEquals(ArticleVisual.Status.READY,layout.visuals().get(0).status());
        assertEquals(ArticleVisual.Status.RENDER_FAILED,layout.visuals().get(1).status());
        assertTrue(layouts.markdown(DRAFT,layout).contains(asset.url()));
        var retried=layouts.retry(layout,"second");
        assertEquals(ArticleVisual.Status.READY,retried.visuals().get(1).status());
        assertEquals(layout.visuals().get(0),retried.visuals().get(0));verify(diagrams,times(3)).render(any());
        var moved=layouts.move(retried,"first",base.blocks().get(1).id());
        assertEquals(asset,moved.visuals().get(0).asset());
        var edited=layouts.reconcile("Changed paragraph.",retried);
        assertEquals(ArticleVisual.Status.NEEDS_PLACEMENT,edited.visuals().get(0).status());
        assertFalse(layouts.markdown(DRAFT,edited).contains(asset.url()));
    }
}
