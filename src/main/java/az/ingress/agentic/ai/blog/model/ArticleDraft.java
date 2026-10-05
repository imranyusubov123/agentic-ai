package az.ingress.ai_exprementation.blog.model;

import java.util.List;
public record ArticleDraft(String title, String markdown, List<String> sourceIds) {
    public ArticleDraft {
        title = Checks.text(title, "title"); markdown = Checks.text(markdown, "markdown");
        sourceIds = Checks.list(sourceIds);
    }
}
