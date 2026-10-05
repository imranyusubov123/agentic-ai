# Technical visuals implementation plan

1. Deploy a separate local Kroki Compose stack (Graphviz/PlantUML gateway and Mermaid companion).
2. Add a reusable `visuals` package: bounded renderer, persistent PNG assets, typed Java memory template, REST asset access.
3. Expose purpose-specific tools via Spring AI MCP under an opt-in local profile. Reuse the rendering service directly inside the blog; no loopback MCP calls.
4. Extend visual plans with an engine/source while accepting legacy Mermaid data. Store blocks before planning; isolate render failures; retry individual visuals; preserve assets on placement changes.
5. Show generated assets, failures, and retry controls in Blog Studio. Keep human approval boundary.
6. Test validation, asset persistence, renderer failures, workflow recovery, and MCP discovery/calls. Smoke-test the real containers without paid LLM calls.

Initial scope: Graphviz, PlantUML, Mermaid, structured Java memory diagrams, local deployment, PNG export. D2 and data charts can follow through the same renderer interface. PNG is deliberately used for stored assets so untrusted SVG is never embedded inline. Workflow storage remains process-local; visual assets persist on disk.

## Completed in this version

- Local Kroki 0.28.0 gateway and Mermaid companion deployed.
- `visuals` package, persistent PNG store, memory template and four MCP tools implemented.
- Blog renderer routing, saved paragraph anchors, independent render status, single-visual retry,
  image preview and image-aware Markdown export implemented.
- 42 tests passing. Real MCP discovery, four tool calls, PNG downloads and rejected unsafe source
  verified by `scripts/verify-visuals-mcp.py` without model calls.
- App running at port 8083 with the visuals profile; the MCP endpoint is `/mcp`.

Follow-up scope: D2/chart tools, manual-placement locking, durable workflow storage, standalone
service extraction, and a portable export bundle containing Markdown plus images.
