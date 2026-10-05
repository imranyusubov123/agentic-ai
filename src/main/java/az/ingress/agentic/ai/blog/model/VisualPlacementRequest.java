package az.ingress.ai_exprementation.blog.model;

public record VisualPlacementRequest(String afterBlockId) {
    public VisualPlacementRequest { afterBlockId = Checks.text(afterBlockId, "afterBlockId"); }
}
