package az.ingress.ai_exprementation.blog.model;

public record ArticleBlock(String id, String markdown) {
    public ArticleBlock { id = Checks.text(id, "id"); markdown = Checks.text(markdown, "markdown"); }
}
