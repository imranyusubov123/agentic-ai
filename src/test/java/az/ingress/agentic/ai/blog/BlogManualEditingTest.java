package az.ingress.ai_exprementation.blog;

import az.ingress.ai_exprementation.blog.model.*;
import az.ingress.ai_exprementation.blog.service.*;
import az.ingress.ai_exprementation.blog.store.InMemoryBlogWorkflowStore;
import az.ingress.ai_exprementation.visuals.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static az.ingress.ai_exprementation.blog.BlogWorkflowServiceTest.*;

class BlogManualEditingTest {
    final InMemoryBlogWorkflowStore store=new InMemoryBlogWorkflowStore();
    final DiagramService renderer=mock(DiagramService.class);
    final Writer writer=mock(Writer.class);
    final TechnicalReviewer reviewer=mock(TechnicalReviewer.class);
    final BlogWorkflowService service=new BlogWorkflowService(mock(Researcher.class),writer,reviewer,store,mock(VisualPlanner.class),new ArticleLayoutService(renderer));
    BlogWorkflow seed() {
        var now=Instant.now();
        var spec=new VisualSpec("diagram","paragraph","Explain","flowchart LR\nA --> B","Caption","Description");
        var asset=new VisualAsset(UUID.randomUUID(),"/api/visuals/assets/old.png","image/png",20,20);
        var layout=new ArticleLayout(List.of(new ArticleBlock("paragraph","Original text")),List.of(
                new ArticleVisual(spec,1,ArticleVisual.Status.READY,asset,null),
                new ArticleVisual(new VisualSpec("other","paragraph","Other","flowchart LR\nC --> D","Other","Other"),1,ArticleVisual.Status.READY,asset,null)));
        var run=new BlogWorkflow(UUID.randomUUID(),TOPIC,WorkflowStatus.AWAITING_HUMAN_APPROVAL,now,now,RESEARCH,
                new ArticleDraft("Title","Original text",List.of("s1")),new ReviewResult("Old review",List.of()),null,null,List.of(),layout);
        store.save(run);return run;
    }
    @Test void manualContentSavePreservesSourceIdsInvalidatesReviewAndRejectsStaleSave() {
        var run=seed();
        var request=new ContentEditRequest("New title","Changed text",run.updatedAt());
        var result=service.editContent(run.id(),request);
        assertEquals("Changed text",result.draft().markdown());assertEquals(List.of("s1"),result.draft().sourceIds());
        assertEquals(ArticleVisual.Status.NEEDS_PLACEMENT,result.layout().visuals().get(0).status());
        assertEquals("Manual edit",result.review().issues().get(0).location());
        assertThrows(IllegalStateException.class,()->service.editContent(run.id(),request));
        verifyNoInteractions(renderer,reviewer);
        verify(writer).remember(eq(run.id()),anyString(),eq(result.draft()));
    }
    @Test void diagramEditOnlyRendersSelectedVisualAndDoesNotCallModels() {
        var run=seed();var asset=new VisualAsset(UUID.randomUUID(),"/api/visuals/assets/new.png","image/png",40,40);
        when(renderer.render(any())).thenReturn(asset);
        var result=service.editDiagram(run.id(),"diagram",new DiagramEditRequest(DiagramEngine.GRAPHVIZ,"digraph G { A -> B }","New caption","New description",run.updatedAt()));
        assertEquals(run.draft(),result.draft());assertEquals(run.layout().blocks(),result.layout().blocks());
        assertEquals(run.layout().visuals().get(1),result.layout().visuals().get(1));
        assertEquals(2,result.layout().visuals().get(0).version());assertEquals(asset,result.layout().visuals().get(0).asset());
        verify(renderer,times(1)).render(any());verifyNoInteractions(writer,reviewer);
    }
    @Test void renderFailureAndInvalidSourceKeepOriginalSnapshot() {
        var run=seed();when(renderer.render(any())).thenThrow(new IllegalStateException("Unavailable"));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.editDiagram(run.id(),"diagram",
                new DiagramEditRequest(DiagramEngine.GRAPHVIZ,"digraph G {A -> B}","Caption","Alt",run.updatedAt())));
        assertEquals(run,store.find(run.id()).orElseThrow());
        assertThrows(IllegalArgumentException.class,()->service.editDiagram(run.id(),"diagram",
                new DiagramEditRequest(DiagramEngine.GRAPHVIZ,"invalid source","Caption","Alt",run.updatedAt())));
        assertEquals(run,store.find(run.id()).orElseThrow());
    }
    @Test void patchEndpointsReturnSavedArticleAndConflictForStaleTimestamp() throws Exception {
        var run=seed();var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new az.ingress.ai_exprementation.blog.web.BlogWorkflowController(service)).build();
        var body="{\"title\":\"Edited\",\"markdown\":\"Saved text\",\"expectedUpdatedAt\":\""+run.updatedAt()+"\"}";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/blog/workflows/"+run.id()+"/content").contentType("application/json").content(body))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.draft.markdown").value("Saved text"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/blog/workflows/"+run.id()+"/content").contentType("application/json").content(body))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict());
    }
}
