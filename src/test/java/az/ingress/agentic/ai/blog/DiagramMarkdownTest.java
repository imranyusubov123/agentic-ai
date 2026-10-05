package az.ingress.ai_exprementation.blog;

import az.ingress.ai_exprementation.blog.service.DiagramMarkdown;
import az.ingress.ai_exprementation.blog.service.ArticleLayoutService;
import az.ingress.ai_exprementation.blog.model.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DiagramMarkdownTest {
    @Test void removesDiagramFencesButKeepsJavaAndSurroundingText() {
        String input="Before\n```dot\ndigraph MethodCall { first -> box; }\n```\nAfter\n```java\nint amount = 1;\n```";
        String filtered=DiagramMarkdown.withoutDiagramSource(input);
        assertEquals("Before\nAfter\n```java\nint amount = 1;\n```",filtered);
        var draft=new ArticleDraft("Title",input,List.of());
        var layouts=new ArticleLayoutService();
        assertFalse(layouts.markdown(draft,layouts.reconcile(input,ArticleLayout.empty())).contains("digraph"));
        assertFalse(layouts.markdown(draft,ArticleLayout.empty()).contains("digraph"));
    }
    @Test void handlesOtherLanguagesUnlabelledAndLongerFences() {
        for(String language:List.of("dot","graphviz","mermaid","plantuml","puml","")) {
            assertEquals("Before\nAfter", DiagramMarkdown.withoutDiagramSource("Before\n~~~~"+language+"\ndigraph G {a -> b}\n~~~~~\nAfter"));
        }
        assertEquals("",DiagramMarkdown.withoutDiagramSource("```dot\ndigraph G { a -> b }"));
    }
    @Test void doesNotRemoveDiagramTextInsideAnOrdinaryCodeExample() {
        String example="````java\nString example = \"digraph G {}\";\n// ```dot\n````";
        assertEquals(example,DiagramMarkdown.withoutDiagramSource(example));
    }
}
