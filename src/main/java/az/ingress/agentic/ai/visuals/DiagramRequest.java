package az.ingress.ai_exprementation.visuals;

import java.util.Locale;
import java.util.Objects;

public record DiagramRequest(DiagramEngine engine, String source) {
    public DiagramRequest {
        Objects.requireNonNull(engine, "engine is required");
        if (source == null || source.isBlank() || source.length() > 12000)
            throw new IllegalArgumentException("Diagram source must contain 1–12000 characters");
        source = source.strip();
        String lower = source.toLowerCase(Locale.ROOT);
        // Renderers run on an isolated network too. Never allow source-directed file/network access.
        if (lower.contains("http:") || lower.contains("https:") || lower.contains("file:")
                || lower.contains("ftp:") || lower.contains("!" ) || lower.contains("%%{")
                || lower.contains("<") || lower.contains("```") || lower.contains("~~~")
                || lower.matches("(?s).*\\b(click|href|url|image|shapefile)\\b.*")
                || lower.contains("%") || lower.contains("\\\\"))
            throw new IllegalArgumentException("Use plain labels and arrows; external content and directives are unsupported");
        boolean valid = switch (engine) {
            case GRAPHVIZ -> source.matches("(?s)(strict\\s+)?(digraph|graph)\\b.*");
            case PLANTUML -> source.startsWith("@startuml") && source.endsWith("@enduml");
            case MERMAID -> source.startsWith("flowchart ") || source.startsWith("sequenceDiagram");
        };
        if (!valid) throw new IllegalArgumentException("Source does not match " + engine + " syntax");
    }
}
