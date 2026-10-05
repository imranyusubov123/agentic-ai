package az.ingress.ai_exprementation.blog;

import az.ingress.ai_exprementation.blog.model.*;
import az.ingress.ai_exprementation.blog.store.InMemoryBlogWorkflowStore;
import az.ingress.ai_exprementation.blog.web.BlogWorkflowController;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static az.ingress.ai_exprementation.blog.BlogWorkflowServiceTest.TOPIC;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BlogStudioTest {
    @Test void listReturnsLatestSnapshotsInMostRecentlyUpdatedOrder() {
        var store = new InMemoryBlogWorkflowStore();
        var earlier = Instant.parse("2026-01-01T00:00:00Z");
        var later = earlier.plusSeconds(30);
        var first = new BlogWorkflow(UUID.randomUUID(), TOPIC, WorkflowStatus.CREATED, earlier,
                earlier, null, null, null, null, null, List.of());
        var second = new BlogWorkflow(UUID.randomUUID(), TOPIC, WorkflowStatus.CREATED, later,
                later, null, null, null, null, null, List.of());
        store.save(first); store.save(second);
        assertEquals(List.of(second, first), store.findAll());
        var edited = new BlogWorkflow(first.id(), TOPIC, WorkflowStatus.FAILED, earlier,
                later.plusSeconds(30), null, null, null, WorkflowStatus.WRITING, "Failed", List.of());
        store.save(edited);
        assertEquals(List.of(edited, second), store.findAll());
    }
    @Test void listEndpointIsEmptyInitiallyAndOmitsFullArticlePayload() throws Exception {
        var service = new BlogWorkflowServiceTest().service();
        var mvc = MockMvcBuilders.standaloneSetup(new BlogWorkflowController(service)).build();
        mvc.perform(get("/api/blog/workflows")).andExpect(status().isOk())
                .andExpect(content().json("[]"));
        var run = service.run(TOPIC);
        mvc.perform(get("/api/blog/workflows")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(run.id().toString()))
                .andExpect(jsonPath("$[0].title").value(TOPIC.title()))
                .andExpect(jsonPath("$[0].status").value("AWAITING_HUMAN_APPROVAL"))
                .andExpect(jsonPath("$[0].draft").doesNotExist())
                .andExpect(jsonPath("$[0].research").doesNotExist());
    }
}
