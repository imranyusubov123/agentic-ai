package az.ingress.ai_exprementation.blog.model;

import java.util.List;
public record VisualPlan(List<VisualSpec> visuals) {
    public VisualPlan {
        visuals = Checks.list(visuals);
        if (visuals.size() > 3) throw new IllegalArgumentException("At most three visuals per article");
        if (visuals.stream().map(VisualSpec::visualId).distinct().count() != visuals.size())
            throw new IllegalArgumentException("Duplicate visual IDs");
    }
}
