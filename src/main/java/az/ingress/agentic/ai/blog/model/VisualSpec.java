package az.ingress.ai_exprementation.blog.model;

import az.ingress.ai_exprementation.visuals.*;

/** Renderer-neutral plan; the legacy mermaid field remains accepted for existing clients. */
public record VisualSpec(String visualId, String afterBlockId, String purpose,
                         String mermaid, String caption, String altText,
                         DiagramEngine engine, String source) {
    public VisualSpec(String visualId, String afterBlockId, String purpose, String mermaid, String caption, String altText) {
        this(visualId, afterBlockId, purpose, mermaid, caption, altText, DiagramEngine.MERMAID, mermaid);
    }
    public VisualSpec {
        visualId = Checks.text(visualId, "visualId");
        if (!visualId.matches("[a-z][a-z0-9-]{0,63}")) throw new IllegalArgumentException("Invalid visualId");
        afterBlockId = Checks.text(afterBlockId, "afterBlockId");
        purpose = Checks.text(purpose, "purpose");
        caption = Checks.text(caption, "caption"); altText = Checks.text(altText, "altText");
        engine = engine == null ? DiagramEngine.MERMAID : engine;
        source = source == null || source.isBlank() ? mermaid : source;
        source = new DiagramRequest(engine, source).source();
        mermaid = engine == DiagramEngine.MERMAID ? source : null;
    }
}
