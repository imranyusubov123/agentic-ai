package az.ingress.ai_exprementation.visuals;

/** Shared selection guidance for the blog planner and MCP tool discovery. */
public final class DiagramToolCatalog {
    private DiagramToolCatalog() {}
    public static final String GRAPH = "Choose render_graph (GRAPHVIZ) for directory trees, hierarchies, dependency graphs, network topology, object references, and structural relationships. Use DOT source, with -> for directed edges and quoted plain labels. For behavior over time prefer render_uml; for decisions prefer render_flowchart.";
    public static final String UML = "Choose render_uml (PLANTUML) for UML class relationships and sequence diagrams showing ordered calls, participants, requests and responses. Use complete @startuml / @enduml source. For a directory tree or dependency graph prefer render_graph.";
    public static final String FLOW = "Choose render_flowchart (MERMAID) for algorithms, branching decisions and step-by-step workflows. Use flowchart syntax; sequenceDiagram is supported but prefer render_uml for interaction sequences. For structural trees prefer render_graph.";
    public static final String MEMORY = "Choose render_memory_diagram for a conceptual Java stack/heap snapshot when frames, variables, objects and references are available as structured data. It uses a Graphviz template. Use either value or targetObjectId for each variable; all object references must resolve. For arbitrary graphs use render_graph.";
    public static final String RESULT = " Returns a saved PNG asset with a server-relative URL. No external resources, HTML labels, includes or directives; no code execution.";
    public static final String PLANNER_GUIDE = """
            Decide independently for EACH visual based on what it explains. One article may mix engines.
            """ + GRAPH + "\n" + UML + "\n" + FLOW + """

            This planner returns engine and source for the application to render; it does not invoke MCP tools.
            For Java memory diagrams use GRAPHVIZ source here; the structured memory template is a separate MCP tool.
            For a Linux filesystem article, prefer GRAPHVIZ for its directory hierarchy and mount relationships.
            Respect an explicit compatible renderer request. Otherwise choose automatically; do not ask the user.
            In purpose, state the diagram type, what it explains, and briefly why this engine fits.
            Return no visual when a diagram would not clarify the explanation.
            Do not invent unavailable engines or use diagram syntax belonging to another engine.
            """;
}
