package az.ingress.ai_exprementation.blog.service;

import az.ingress.ai_exprementation.blog.model.*;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class ArticleLayoutService {
    private final az.ingress.ai_exprementation.visuals.DiagramService diagrams;
    /** Legacy source-only mode for consumers that render Mermaid themselves. */
    public ArticleLayoutService() { this.diagrams=null; }
    @org.springframework.beans.factory.annotation.Autowired
    public ArticleLayoutService(az.ingress.ai_exprementation.visuals.DiagramService diagrams) { this.diagrams=diagrams; }
    public ArticleVisual render(ArticleVisual visual) {
        if (diagrams == null) return visual.withStatus(ArticleVisual.Status.READY);
        try {
            var asset=diagrams.render(new az.ingress.ai_exprementation.visuals.DiagramRequest(visual.spec().engine(),visual.spec().source()));
            return new ArticleVisual(visual.spec(),visual.version(),ArticleVisual.Status.READY,asset,null);
        } catch (RuntimeException ex) {
            return new ArticleVisual(visual.spec(),visual.version(),ArticleVisual.Status.RENDER_FAILED,null,
                    "Rendering failed. Check Kroki availability or replan invalid diagram source, then retry.");
        }
    }
    public ArticleLayout retry(ArticleLayout layout, String visualId) {
        if(layout.visuals().stream().noneMatch(v -> v.spec().visualId().equals(visualId)))throw new NoSuchElementException("Unknown visual");
        return new ArticleLayout(layout.blocks(), layout.visuals().stream().map(v -> {
            if(!v.spec().visualId().equals(visualId))return v;
            if(layout.blocks().stream().noneMatch(b -> b.id().equals(v.spec().afterBlockId())))throw new IllegalArgumentException("Choose a valid placement first");
            return render(v);
        }).toList());
    }

    /** Conservative matching: only identical blocks retain identity, including after reordering. */
    public ArticleLayout reconcile(String markdown, ArticleLayout previous) {
        var available = new HashMap<String, Deque<String>>();
        for (var block : previous.blocks())
            available.computeIfAbsent(block.markdown(), key -> new ArrayDeque<>()).add(block.id());
        var blocks = new ArrayList<ArticleBlock>();
        for (String text : split(markdown)) {
            var ids = available.get(text);
            blocks.add(new ArticleBlock(ids == null || ids.isEmpty() ? "block-" + UUID.randomUUID() : ids.remove(), text));
        }
        var present = new HashSet<String>(); blocks.forEach(block -> present.add(block.id()));
        boolean changed = !blocks.equals(previous.blocks());
        var visuals = previous.visuals().stream().map(v -> v.withStatus(
                !present.contains(v.spec().afterBlockId()) ? ArticleVisual.Status.NEEDS_PLACEMENT
                : v.status() == ArticleVisual.Status.RENDER_FAILED ? v.status()
                : changed ? ArticleVisual.Status.NEEDS_REVIEW : v.status())).toList();
        return new ArticleLayout(blocks, visuals);
    }

    // Keep fenced code (including blank lines) intact; headings get their own anchor.
    List<String> split(String markdown) {
        var result = new ArrayList<String>(); var current = new StringBuilder();
        char fence = 0; int fenceSize = 0;
        for (String line : markdown.replace("\r\n", "\n").split("\n", -1)) {
            String trimmed = line.stripLeading();
            int count = 0;
            if (!trimmed.isEmpty() && (trimmed.charAt(0) == '`' || trimmed.charAt(0) == '~'))
                while (count < trimmed.length() && trimmed.charAt(count) == trimmed.charAt(0)) count++;
            if (fence != 0) {
                current.append(line).append('\n');
                if (count >= fenceSize && trimmed.charAt(0) == fence && trimmed.substring(count).isBlank()) {
                    fence = 0; flush(result, current);
                }
            } else if (count >= 3) {
                flush(result, current); fence = trimmed.charAt(0); fenceSize = count;
                current.append(line).append('\n');
            } else if (line.isBlank()) flush(result, current);
            else if (trimmed.matches("#{1,6} .*")) {
                flush(result, current); result.add(line.strip());
            } else current.append(line).append('\n');
        }
        flush(result, current); return result;
    }
    private void flush(List<String> blocks, StringBuilder current) {
        if (!current.toString().isBlank()) blocks.add(current.toString().stripTrailing());
        current.setLength(0);
    }

    public ArticleLayout apply(ArticleLayout layout, VisualPlan plan) {
        var ids = new HashSet<String>(); layout.blocks().forEach(b -> ids.add(b.id()));
        var old = new HashMap<String, ArticleVisual>(); layout.visuals().forEach(v -> old.put(v.spec().visualId(), v));
        var visuals = new ArrayList<ArticleVisual>();
        for (var spec : plan.visuals()) {
            if (!ids.contains(spec.afterBlockId())) throw new IllegalArgumentException("Unknown anchor: " + spec.afterBlockId());
            var prior = old.get(spec.visualId());
            int version = prior == null ? 1 : prior.version() + (prior.spec().equals(spec) ? 0 : 1);
            visuals.add(prior != null && prior.spec().equals(spec) && prior.asset() != null
                    ? prior.withStatus(ArticleVisual.Status.READY)
                    : render(new ArticleVisual(spec, version, ArticleVisual.Status.READY)));
        }
        return new ArticleLayout(layout.blocks(), visuals);
    }

    public ArticleLayout move(ArticleLayout layout, String visualId, String afterBlockId) {
        if (layout.blocks().stream().noneMatch(b -> b.id().equals(afterBlockId)))
            throw new IllegalArgumentException("Unknown block ID");
        if (layout.visuals().stream().noneMatch(v -> v.spec().visualId().equals(visualId)))
            throw new NoSuchElementException("Unknown visual ID");
        return new ArticleLayout(layout.blocks(), layout.visuals().stream().map(v -> {
            if (!v.spec().visualId().equals(visualId)) return v;
            var s = v.spec();
            return new ArticleVisual(new VisualSpec(s.visualId(), afterBlockId, s.purpose(), s.mermaid(),
                    s.caption(), s.altText(), s.engine(), s.source()), v.version() + 1,
                    v.status() == ArticleVisual.Status.RENDER_FAILED ? v.status() : ArticleVisual.Status.NEEDS_REVIEW, v.asset(), v.error());
        }).toList());
    }

    /** Markdown export, intended for a Mermaid-capable renderer. Pending visuals stay out of the article. */
    private boolean vIsAsset(ArticleVisual visual) { return visual.asset() != null; }

    public String markdown(ArticleDraft draft, ArticleLayout layout) {
        var out = new StringBuilder();
        for (var block : layout.blocks()) {
            out.append(DiagramMarkdown.withoutDiagramSource(block.markdown())).append("\n\n");
            for (var visual : layout.visuals()) {
                var spec = visual.spec();
                if (visual.status() != ArticleVisual.Status.READY || !spec.afterBlockId().equals(block.id())) continue;
                if(vIsAsset(visual)) out.append("![").append(spec.altText().replace("[", "(").replace("]", ")").replace("\n", " "))
                        .append("](").append(visual.asset().url()).append(")\n\n");
                else out.append("*Diagram unavailable; render it before publishing.*\n\n");
                out
                        .append("**Figure:** ").append(spec.caption()).append("\n\n")
                        .append("**Description:** ").append(spec.altText()).append("\n\n");
            }
        }
        return layout.blocks().isEmpty() ? DiagramMarkdown.withoutDiagramSource(draft.markdown()) : out.toString().strip();
    }
}
