package az.ingress.ai_exprementation.blog.model;

import java.net.URI;
/** A model-suggested reference, not proof that the URL was fetched or verified. */
public record Source(String id, String title, String url, String relevance) {
    public Source {
        id = Checks.text(id, "id"); title = Checks.text(title, "title");
        url = Checks.text(url, "url"); relevance = Checks.text(relevance, "relevance");
        URI uri = URI.create(url);
        if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) || uri.getHost() == null)
            throw new IllegalArgumentException("Source URL must be an absolute HTTP(S) URL");
    }
}
