package az.ingress.ai_exprementation.blog.model;

import java.util.List;
public record ArticleLayout(List<ArticleBlock> blocks, List<ArticleVisual> visuals) {
    public ArticleLayout { blocks = Checks.list(blocks); visuals = Checks.list(visuals); }
    public static ArticleLayout empty() { return new ArticleLayout(List.of(), List.of()); }
}
