package az.ingress.ai_exprementation.visuals;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("visuals")
public class VisualTools {
    private final DiagramService diagrams;
    private final MemoryDiagramTemplate memory;
    public VisualTools(DiagramService diagrams, MemoryDiagramTemplate memory) { this.diagrams=diagrams; this.memory=memory; }
    @McpTool(name="render_graph", description=DiagramToolCatalog.GRAPH + DiagramToolCatalog.RESULT)
    public VisualAsset graph(@McpToolParam(description="Complete graph or digraph DOT source", required=true) String source) {
        return diagrams.render(new DiagramRequest(DiagramEngine.GRAPHVIZ,source));
    }
    @McpTool(name="render_uml", description=DiagramToolCatalog.UML + DiagramToolCatalog.RESULT)
    public VisualAsset uml(@McpToolParam(description="PlantUML source including @startuml and @enduml", required=true) String source) {
        return diagrams.render(new DiagramRequest(DiagramEngine.PLANTUML,source));
    }
    @McpTool(name="render_flowchart", description=DiagramToolCatalog.FLOW + DiagramToolCatalog.RESULT)
    public VisualAsset flowchart(@McpToolParam(description="Mermaid flowchart or sequenceDiagram source", required=true) String source) {
        return diagrams.render(new DiagramRequest(DiagramEngine.MERMAID,source));
    }
    @McpTool(name="render_memory_diagram", description=DiagramToolCatalog.MEMORY + DiagramToolCatalog.RESULT)
    public VisualAsset memory(@McpToolParam(description="Frames with variables, heap objects with fields. Use targetObjectId for references and value for primitive/null values.",required=true) MemoryDiagram diagram) {
        return diagrams.render(memory.build(diagram));
    }
}
