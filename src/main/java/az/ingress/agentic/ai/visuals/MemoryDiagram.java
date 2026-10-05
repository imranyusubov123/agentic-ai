package az.ingress.ai_exprementation.visuals;

import java.util.List;

/** Conceptual snapshot, not a promise about physical JVM allocation or optimization. */
public record MemoryDiagram(List<Frame> frames, List<HeapObject> objects) {
    public record Variable(String name, @org.springframework.ai.mcp.annotation.McpToolParam(required=false) String value,
                           @org.springframework.ai.mcp.annotation.McpToolParam(required=false) String targetObjectId) {}
    public record Frame(String name, List<Variable> variables) {}
    public record HeapObject(String id, String type, List<Variable> fields) {}
}
