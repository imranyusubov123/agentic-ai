package az.ingress.ai_exprementation.blog.service;

import java.util.*;
import java.util.regex.Pattern;

/** Removes diagram implementation fences from reader-facing Markdown, keeping ordinary code. */
public final class DiagramMarkdown {
    private DiagramMarkdown() {}
    private static final Pattern OPEN = Pattern.compile("^ {0,3}(`{3,}|~{3,})[ \\t]*(.*)$");
    private static final Set<String> LANGUAGES = Set.of("dot", "graphviz", "mermaid", "plantuml", "puml");
    public static String withoutDiagramSource(String markdown) {
        var lines = markdown.replace("\r\n", "\n").split("\n", -1);
        var result = new ArrayList<String>();
        for (int i=0; i<lines.length; i++) {
            var opening = OPEN.matcher(lines[i]);
            if (!opening.matches()) { result.add(lines[i]); continue; }
            String fence=opening.group(1), info=opening.group(2).strip().toLowerCase(Locale.ROOT);
            String language=info.isEmpty()?"":info.split("\\s+",2)[0];
            int end=i+1;
            var closing=Pattern.compile("^ {0,3}"+Pattern.quote(fence.substring(0,1))+"{"+fence.length()+",}[ \\t]*$");
            while(end<lines.length && !closing.matcher(lines[end]).matches()) end++;
            String body=String.join("\n",Arrays.copyOfRange(lines,i+1,end)).stripLeading();
            boolean diagram=LANGUAGES.contains(language) || (language.isEmpty()
                    && body.matches("(?s)(?:(?:strict\\s+)?(?:digraph|graph)\\b.*|@startuml\\b.*|flowchart\\s.*|sequenceDiagram\\b.*)"));
            if (!diagram) for(int j=i;j<=end && j<lines.length;j++) result.add(lines[j]);
            i=end;
        }
        return String.join("\n",result);
    }
}
