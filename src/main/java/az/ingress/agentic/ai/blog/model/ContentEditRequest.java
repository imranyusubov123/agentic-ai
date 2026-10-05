package az.ingress.ai_exprementation.blog.model;
import java.time.Instant;
import java.util.Objects;
public record ContentEditRequest(String title, String markdown, Instant expectedUpdatedAt) {
    public ContentEditRequest {
        title=Checks.text(title,"title"); markdown=Checks.text(markdown,"markdown");
        Objects.requireNonNull(expectedUpdatedAt,"expectedUpdatedAt");
        if(title.length()>300 || markdown.length()>200000)throw new IllegalArgumentException("Content too long");
    }
}
