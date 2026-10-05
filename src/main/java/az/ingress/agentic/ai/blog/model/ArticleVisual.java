package az.ingress.ai_exprementation.blog.model;

import java.util.Objects;
import az.ingress.ai_exprementation.visuals.VisualAsset;

public record ArticleVisual(VisualSpec spec, int version, Status status, VisualAsset asset, String error) {
    public enum Status { READY, NEEDS_REVIEW, NEEDS_PLACEMENT, RENDER_FAILED }
    public ArticleVisual(VisualSpec spec, int version, Status status) { this(spec, version, status, null, null); }
    public ArticleVisual { Objects.requireNonNull(spec); Objects.requireNonNull(status);
        if (version < 1) throw new IllegalArgumentException("Invalid version"); }
    public ArticleVisual withStatus(Status status) { return new ArticleVisual(spec,version,status,asset,error); }
}
