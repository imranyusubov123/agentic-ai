package az.ingress.ai_exprementation.blog.model;

public record Topic(String title, String audience, String scope) {
    public Topic {
        title = Checks.text(title, "title");
        audience = Checks.text(audience, "audience");
        scope = Checks.text(scope, "scope");
    }
}
