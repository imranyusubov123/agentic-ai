package az.ingress.ai_exprementation.blog;

import az.ingress.ai_exprementation.blog.model.*;
import az.ingress.ai_exprementation.blog.service.*;
import az.ingress.ai_exprementation.blog.web.BlogWorkflowController;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.server.ResponseStatusException;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static az.ingress.ai_exprementation.blog.BlogWorkflowServiceTest.*;

class BlogComponentsTest {
    static class StubModel implements ChatModel {
        String response;
        Prompt prompt;
        StubModel(String response) { this.response = response; }
        public ChatResponse call(Prompt prompt) {
            this.prompt = prompt;
            return new ChatResponse(List.of(new Generation(new AssistantMessage(response))));
        }
    }
    @Test void researcherConvertsStructuredOutputAndStatesLimits() {
        var model = new StubModel("""
                {"summary":"Brief","keyConcepts":["ChatClient"],"sources":[
                {"id":"s1","title":"Spring","url":"https://spring.io","relevance":"Official"}],
                "gaps":["Verify version"]}
                """);
        var result = new Researcher(ChatClient.create(model)).research(TOPIC);
        assertEquals("Brief", result.summary()); assertEquals("s1", result.sources().get(0).id());
        assertTrue(model.prompt.getContents().contains(TOPIC.title()));
        assertTrue(model.prompt.getContents().contains("NO browsing"));
    }
    @Test void writerReceivesResearchAndProducesTypedDraft() {
        var model = new StubModel("""
                {"title":"Guide","markdown":"# Guide","sourceIds":["s1"]}
                """);
        var result = new Writer(ChatClient.create(model)).write(TOPIC, RESEARCH);
        assertEquals("# Guide", result.markdown()); assertEquals(List.of("s1"), result.sourceIds());
        assertTrue(model.prompt.getContents().contains(RESEARCH.summary()));
        assertTrue(model.prompt.getContents().contains(RESEARCH.sources().get(0).url()));
    }
    @Test void reviewerConvertsSeverityAndReceivesDraft() {
        var model = new StubModel("""
                {"summary":"Fix API","issues":[{"severity":"BLOCKER","location":"Example",
                "description":"Invalid API","recommendation":"Check docs"}]}
                """);
        var result = new TechnicalReviewer(ChatClient.create(model)).review(TOPIC, RESEARCH, DRAFT);
        assertTrue(result.hasBlockers()); assertTrue(model.prompt.getContents().contains(DRAFT.markdown()));
    }
    @Test void malformedAndEmptyOutputsFail() {
        var model = new StubModel("not json"); var researcher = new Researcher(ChatClient.create(model));
        assertThrows(RuntimeException.class, () -> researcher.research(TOPIC));
        model.response = "null";
        assertThrows(RuntimeException.class, () -> researcher.research(TOPIC));
    }
    @Test void controllerReturnsSnapshotAnd404ForUnknownRun() {
        var fixture = new BlogWorkflowServiceTest();
        var controller = new BlogWorkflowController(fixture.service());
        var run = controller.run(TOPIC);
        assertEquals(run, controller.find(run.id()));
        var exception = assertThrows(ResponseStatusException.class, () -> controller.find(UUID.randomUUID()));
        assertEquals(404, exception.getStatusCode().value());
    }
    @Test void httpEndpointsSerializeRecordsAndRejectInvalidTopics() throws Exception {
        var fixture = new BlogWorkflowServiceTest();
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(new BlogWorkflowController(fixture.service())).build();
        var response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/blog/workflows").contentType("application/json").content("""
                        {"title":"Spring AI","audience":"Java developers", "scope":"Introductory example; verify versions"}
                        """))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(response.contains("AWAITING_HUMAN_APPROVAL"));
        assertTrue(response.contains("Sources unverified"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/blog/workflows").contentType("application/json").content("""
                        {"title":" ","audience":"Java developers","scope":"Intro"}
                        """))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/blog/workflows/" + UUID.randomUUID()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
    }
    @Test void componentsUseQualifiedExistingClientWithMultipleBeans() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean("openAIChatClient", ChatClient.class,
                    () -> ChatClient.create(new StubModel("""
                            {"summary":"Brief","keyConcepts":[],"sources":[],"gaps":[]}
                            """)));
            context.registerBean("chatClient", ChatClient.class,
                    () -> ChatClient.create(new StubModel("wrong client")));
            context.scan("az.ingress.ai_exprementation.blog", "az.ingress.ai_exprementation.visuals"); context.refresh();
            assertEquals("Brief", context.getBean(Researcher.class).research(TOPIC).summary());
            assertNotNull(context.getBean(BlogWorkflowController.class));
        }
    }
    @Test void editorialMemoryIsScopedToRequestAndIncludesEarlierTurns() {
        var model = new StubModel("""
                {"title":"Edited","markdown":"Edited body","sourceIds":["s1"]}
                """);
        var writer = new Writer(ChatClient.create(model));
        var fixture = new BlogWorkflowServiceTest(); var service = fixture.service();
        var first = service.run(TOPIC); var second = service.run(TOPIC);
        writer.remember(first.id(), "Always use short paragraphs", DRAFT);
        writer.revise(first, "Add an example");
        assertTrue(model.prompt.getContents().contains("Always use short paragraphs"));
        assertTrue(model.prompt.getContents().contains(first.draft().markdown()));
        writer.remember(first.id(), "Add an example", DRAFT);
        writer.revise(first, "Keep the previous style");
        assertTrue(model.prompt.getContents().contains("Add an example"));
        writer.revise(second, "Edit this article");
        assertFalse(model.prompt.getContents().contains("Always use short paragraphs"));
        assertFalse(model.prompt.getContents().contains("Add an example"));
    }
    @Test void revisionHttpApiSupportsRequestIdAndValidation() throws Exception {
        var fixture = new BlogWorkflowServiceTest(); var service = fixture.service(); var run = service.run(TOPIC);
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(new BlogWorkflowController(service)).build();
        var url = "/api/blog/workflows/" + run.id() + "/revisions";
        var response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post(url).contentType("application/json").content("{\"instructions\":\"Shorten\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(response.contains("\"requestId\":\"" + run.id() + "\""));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post(url).contentType("application/json").content("{\"instructions\":\" \"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/blog/workflows/" + UUID.randomUUID() + "/revisions")
                .contentType("application/json").content("{\"instructions\":\"Edit\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
    }
}
