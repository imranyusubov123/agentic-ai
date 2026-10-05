package az.ingress.ai_exprementation.blog.model;

public record RevisionRequest(String instructions) {
    public RevisionRequest { instructions = Checks.text(instructions, "instructions"); }
}
