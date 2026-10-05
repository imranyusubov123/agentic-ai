# Technical visuals: local rendering and MCP

## Start

Run from the project root:

```sh
docker compose -f docker-compose.visuals.yaml up -d
./gradlew bootRun --args='--spring.profiles.active=visuals --server.port=8083'
```

- Blog Studio: http://127.0.0.1:8083/blog/index.html
- MCP Streamable HTTP endpoint: http://127.0.0.1:8083/mcp
- Kroki (local renderer): http://127.0.0.1:8001

In an MCP client, add an HTTP/Streamable HTTP server using the `/mcp` URL. Connection
configuration is client-specific. This does not automatically install or register the
server in any desktop client. Remote cloud clients cannot reach your machine's localhost.

The `visuals` profile enables MCP and binds the application to loopback. MCP is disabled
without this profile. Rendering remains available to the blog when Kroki is running.
The profile uses the project's existing database/model configuration; this first version
ships in the same Spring Boot process, with an independent `visuals` package that can later
be extracted. The blog invokes the service directly rather than making loopback MCP calls.

## Tools

| MCP tool | Input | Renderer |
| --- | --- | --- |
| `render_graph` | `source`: Graphviz DOT | Graphviz |
| `render_uml` | `source`: complete @startuml / @enduml block | PlantUML |
| `render_flowchart` | `source`: flowchart or sequenceDiagram | Mermaid |
| `render_memory_diagram` | `diagram`: frames, objects and references | Validated Java-memory template → Graphviz |

Each returns `id`, `url`, `mediaType`, `width`, and `height`. The URL is relative to the
Spring server, e.g. `/api/visuals/assets/{id}.png`; prepend the server origin to fetch it.
These are rendering tools: they don't invoke an LLM, execute Java, verify Java semantics,
choose article placement, or publish. The calling LLM chooses a tool using its description.
The blog planner chooses GRAPHVIZ, PLANTUML, or MERMAID plus an exact paragraph anchor.

Example arguments for `render_memory_diagram`:

```json
{
  "diagram": {
    "frames": [{"name":"main()", "variables":[
      {"name":"person", "targetObjectId":"person1"},
      {"name":"count", "value":"1"}
    ]}],
    "objects": [{"id":"person1", "type":"Person", "fields":[
      {"name":"age", "value":"27"}
    ]}]
  }
}
```

Use either `value` or `targetObjectId`. For a null reference use `"value":"null"`.
Object IDs must be unique and references must resolve. The template produces a conceptual
snapshot, not a claim about physical allocation after JVM optimization. It is available
through MCP; the blog's first-version planner still generates diagram source directly.

## Blog lifecycle

1. Write the article and save its paragraph IDs before visual planning.
2. Ask the existing model for a renderer, source, purpose, caption, alt text, and `afterBlockId`.
3. Retry a rejected structured plan once. If both attempts fail, retain the text and anchors,
   run technical review, and attach a warning. Use **Plan visuals** to try again.
4. Render each planned visual independently. Transient transport/5xx errors get one retry;
   syntax errors require replanning. Each successful PNG is saved before attaching it.
5. A failed visual becomes `RENDER_FAILED`; successful visuals remain usable. The UI exposes
   **Retry rendering**, backed by `POST /api/blog/workflows/{id}/visuals/{visualId}/retry`.
6. Review the composed text and diagram source together. The reviewer receives diagram source
   even though the article preview uses PNGs. This is not a vision-based review of the PNG.
7. Move a diagram using its paragraph selector. The saved asset is reused and review reruns.
   Text edits flag visuals for review or replacement of missing anchors. Explicit replanning
   replaces the visual set, including placements; locked manual placements are future work.
8. Stop at human review. No publishing or final approval recording.

The `mermaid` field and six-argument VisualSpec constructor remain compatible with existing
clients. New plans use `engine` and `source`. Pending/failed visuals stay out of exports.

## Assets and limits

PNGs are saved under `./data/visuals` by default; configure `visuals.asset-directory` and
`visuals.kroki-url` to change locations. Identical image bytes reuse an ID. Asset storage is
independent of workflow storage: files survive restart, but workflow state and chat memory
are still process-local. Keep/back up the directory; there is no automatic asset cleanup yet.
Markdown exports reference server-relative image URLs, so transfer the PNG files and rewrite
those URLs when publishing elsewhere.

Inputs are limited to 12,000 characters. Outputs are limited to 5 MB and bounded dimensions.
The HTTP request timeout is 20 seconds per attempt. Source-directed URLs, file includes,
HTML labels and directives are rejected. Kroki uses secure mode, a 15-second conversion
limit and 64 KB request limit. Mermaid is on an internal Docker network; the Kroki gateway
also has a bridge network for the localhost port binding. PNG avoids inline SVG execution.
This local profile has no authentication. Public/remote deployment requires a separate
access-control boundary. D2, chart tools, standalone service extraction, and persistent
workflow storage are deliberately outside this first version.

## Verification

```sh
./gradlew test
python3 scripts/verify-visuals-mcp.py http://127.0.0.1:8083
```

The smoke test performs MCP initialization/discovery, calls all four real renderers/tools,
fetches each PNG, and checks rejection of an unsafe source. It makes no model/provider calls.
The unit/integration suite covers per-visual failure/retry, preserved paragraph anchors,
source validation, asset persistence, HTTP errors, and renderer retry behavior.

Stop only the rendering stack with:

```sh
docker compose -f docker-compose.visuals.yaml down
```

## Automatic tool selection

Selection guidance is shared in `DiagramToolCatalog` by the visual planner and MCP tool
descriptions. Each diagram gets its own engine and a reason recorded in `purpose`; one
article can mix renderers. Directory trees, mount relationships, dependencies and references
prefer Graphviz; UML classes and interaction sequences prefer PlantUML; decisions and
workflows prefer Mermaid. The structured Java memory MCP tool also uses Graphviz.
The blog receives a typed plan and routes its engine directly to the renderer. External
MCP agents select tools from their descriptions. User-specified compatible choices take
precedence. No diagram is generated when the planner determines it adds no explanation.

Graphviz project and documentation: https://graphviz.org/
