package az.ingress.ai_exprementation.blog.model;
import az.ingress.ai_exprementation.visuals.DiagramEngine;
import java.time.Instant;
import java.util.Objects;
public record DiagramEditRequest(DiagramEngine engine, String source, String caption, String altText, Instant expectedUpdatedAt) {
    public DiagramEditRequest {
        Objects.requireNonNull(engine,"engine"); Objects.requireNonNull(expectedUpdatedAt,"expectedUpdatedAt");
        source=Checks.text(source,"source"); caption=Checks.text(caption,"caption"); altText=Checks.text(altText,"altText");
        if(source.length()>12000 || caption.length()>2000 || altText.length()>2000)throw new IllegalArgumentException("Diagram fields too long");
    }
}
