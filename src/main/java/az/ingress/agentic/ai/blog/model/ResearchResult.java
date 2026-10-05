package az.ingress.ai_exprementation.blog.model;

import java.util.List;
import java.util.HashSet;
public record ResearchResult(String summary, List<String> keyConcepts, List<Source> sources,
                             List<String> gaps) {
    public ResearchResult {
        summary = Checks.text(summary, "summary");
        keyConcepts = Checks.list(keyConcepts); sources = Checks.list(sources); gaps = Checks.list(gaps);
        var ids = new HashSet<String>();
        for (Source source : sources) if (!ids.add(source.id()))
            throw new IllegalArgumentException("Duplicate source ID: " + source.id());
    }
}
