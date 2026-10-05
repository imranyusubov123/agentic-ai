# Technical blog workflow — first version

The `az.ingress.ai_exprementation.blog` package implements a synchronous workflow:

`Researcher → Writer → VisualPlanner → TechnicalReviewer → AWAITING_HUMAN_APPROVAL`

There is no approval action or publisher in this version. A completed run is a review packet,
not an approved article. A blocking technical review still reaches the human boundary with
its issues intact. A failed stage stops downstream calls and returns `FAILED` with the
failed stage and any artifacts already produced.

## Package structure

- `model`: `Topic`, `Source`, `ResearchResult`, `ArticleDraft`, `ReviewIssue`, `ReviewResult`,
  `WorkflowStatus`, `WorkflowEvent`, `BlogWorkflow`, and package-private validation helpers.
  Records defensively copy lists; topic and required output text cannot be blank.
- `service`: `Researcher`, `Writer`, `TechnicalReviewer`, and `BlogWorkflowService`.
- `store`: `BlogWorkflowStore` and its process-local `InMemoryBlogWorkflowStore` implementation.
- `web`: `BlogWorkflowController`.
- Tests: `BlogWorkflowServiceTest` and `BlogComponentsTest` under the matching test package.

Each specialized component injects the existing `openAIChatClient` bean from `AiProviderConfig`.
This reuses the existing model and configuration without adding clients, providers, or dependencies.
Unlike the conversational `chatClient` bean, this client has no shared chat-memory advisor.
The components use Spring AI's `.call().entity(Record.class)` structured conversion; this is
schema-guided output conversion, not a guarantee of provider-native strict JSON schema enforcement.
Invalid, malformed or empty output fails the stage. Draft source IDs must exist in the research brief.

## Invoke

Run the existing application with its usual OpenAI and PostgreSQL configuration. Set
`SPRING_AI_OPENAI_API_KEY` in your environment to supply your key. From the project directory:

```sh
./gradlew bootRun
```

Then create one run:

```sh
curl -X POST http://localhost:8080/api/blog/workflows \
  -H 'Content-Type: application/json' \
  -d '{
    "title": "Your first Spring AI ChatClient application",
    "audience": "Java developers familiar with Spring Boot",
    "scope": "One practical example; identify version assumptions and production caveats"
  }'
```

Creation waits for four model calls, including visual planning. The JSON response includes the run `id`, `status`,
`topic`, `research`, `draft`, `review`, timestamps, and transition `history`. Read it again with:

```sh
curl http://localhost:8080/api/blog/workflows/REPLACE_WITH_RUN_UUID
```

`POST` returns HTTP 200 for a workflow snapshot, including failed runs: check `status` and
`failedStage`. Invalid topic JSON returns 400; an unknown run ID returns 404. There are no
automatic workflow retries or resumptions; the existing provider client may apply its own retries.
Repeating POST starts another independent run and incurs another set of model calls.

Alternatively, inject `BlogWorkflowService` and call `run(new Topic(title, audience, scope))`.

## Human review and current limitations

Research is a model-generated brief. No source retrieval tool is wired in: source URLs are
unverified candidate references, even when they look authoritative. The prompts require gaps
and version uncertainty to be disclosed. A human must open references, check claims and pinned
versions, compile/run examples, and resolve technical blockers before approving publication.
The technical reviewer is another model call; it does not execute code. Prompt instructions
reduce, but do not eliminate, hallucination or prompt injection risks.

Storage is in memory, is lost on restart, and has no retention limit. This is intended for small,
local experiments. A database-backed store can replace the interface later. Immutable snapshots
preserve artifacts and timestamped transitions so later Planner, FactChecker, SEO, and Publisher
stages can be added explicitly. Logs include only run ID, stage/status, and exception class;
provider error bodies and article contents are not logged by the workflow. Failure messages are
intentionally sanitized. Approval persistence, authentication, a queue, durable storage, and
publication are outside this first version.

## Tests and validation

```sh
./gradlew test --tests 'az.ingress.ai_exprementation.blog.*'
./gradlew test
```

New tests use a deterministic `ChatModel` with the real Spring AI `ChatClient`, plus lightweight
stage stubs. They cover structured parsing, malformed/null output, bean qualification, HTTP
serialization/input validation, execution order, citation ID validation, every stage's failure
path, artifact retention, independent run IDs, immutable collections, and the human boundary
with and without blocking issues. They require no model requests or database.

In the implementation environment, Gradle could not start because its local lock socket was
blocked by the sandbox; escalation was disabled. All application sources and the new tests
compiled with `javac --release 17` against cached dependencies (including Spring AI 2.0.1).
The original implementation was checked directly with JUnit. During the visual-placement update,
permissions were granted and the complete Gradle test suite, including the application-context test, passed.

## Editing an existing article with chat memory

Creation responses now include `requestId`, an alias of the existing workflow `id`.
This is a stable article/conversation identifier, not an idempotency key for each HTTP call.
Existing clients using `id` continue to work. Copy either value from the creation response:

```sh
curl -X POST http://localhost:8080/api/blog/workflows/REPLACE_WITH_REQUEST_ID/revisions \
  -H 'Content-Type: application/json' \
  -d '{"instructions":"Shorten the introduction and use simpler language. Keep the code examples."}'
```

Send further requests to the same URL, for example:

```json
{"instructions":"Keep that style, but expand the explanation after the first code example."}
```

The Writer now has a dedicated Spring AI `MessageWindowChatMemory` with up to 20 messages
per article (roughly ten successful user/assistant exchanges). It includes the original
creation turn and subsequent successful editorial turns. Each revision also explicitly receives
the current topic, research, draft and technical review, so the latest article remains available
when older turns leave the window. Researcher and reviewer calls remain stateless.
The existing model/client configuration is reused; no new provider or dependency is added.

Revision flow: `REVISING → REVIEWING → AWAITING_HUMAN_APPROVAL`. Unknown source IDs are rejected.
Failed edits retain the previous draft and matching review, record a failure, and can be retried.
Unsuccessful turns are not added to editorial memory. Blocking review findings remain visible
at the human boundary; they are not approval. Edits for the same article are serialized in-process.

The revision endpoint returns 404 for an unknown ID, 400 for missing/blank instructions or invalid
JSON, and 409 for a workflow that has no usable draft or is still running. Model-stage failures
return a workflow snapshot with `FAILED`, as creation does. Repeating a revision POST runs it
again; it is not deduplicated. GET on the existing workflow endpoint returns the latest draft.

Both article storage and chat memory remain in memory and are lost on restart. There is no
persistent revision archive or manual Markdown replacement endpoint yet. Updating the topic or
retrieving fresh research is not part of this revision endpoint: create a new workflow for a new
topic. Older editorial preferences may leave the bounded memory window. No publication occurs.

## Explanatory diagrams and exact placement

New workflows automatically plan zero to three Mermaid flowcharts or sequence diagrams after
writing and before technical review. Review receives the article with placed diagrams, captions,
and descriptions. `layout` in the workflow response contains:

- `blocks`: paragraph, heading, and fenced-code blocks, each with an application-generated `id`.
- `visuals`: each has `spec` (`visualId`, `afterBlockId`, `purpose`, `mermaid`, `caption`, `altText`),
  a specification `version`, and a placement/review `status`.

The planner selects exact block IDs. Unknown anchors, duplicate visual IDs and more than three
visuals are rejected. A diagram belongs immediately after its anchor block. Multiple diagrams
at the same anchor follow plan order. This first implementation supports Markdown paragraphs,
ATX headings, and fenced code with backticks or tildes; it is not a full Markdown document AST.
Nested lists and complex HTML layouts are not independently addressable semantic elements.

Unchanged blocks retain IDs across insertions and reordering. Matching is conservative and
text-based: rewriting a paragraph gives it a new ID. Identical duplicate paragraphs are matched
in occurrence order. An edited/deleted anchor marks its visual `NEEDS_PLACEMENT`. Other article
edits mark surviving visuals `NEEDS_REVIEW`. Neither status is silently exported. A warning is
added to the technical review. This avoids guessing where a deleted explanation moved.

View a workflow's article with diagrams rendered to SVG in your browser:

```text
http://localhost:8080/api/blog/workflows/YOUR_REQUEST_ID/preview
```

The read-only preview uses pinned Mermaid, Marked, and DOMPurify libraries from jsDelivr, with
strict Mermaid security and sanitized Markdown/SVG. It needs browser internet access to load
these libraries. If loading times out, it shows source text; Mermaid syntax errors are displayed
beside the affected diagram. Inspect the preview as part of human review. Backend validation
checks placement and prohibited directives, not full Mermaid parsing or visual accuracy.
READY means the visual has a valid placement and was submitted for model review, not human
approval or proof that it renders correctly. Generated SVGs are browser-rendered, not persisted
image files. Charts, raster illustrations, persistent visual revision history, and automatic
repair of invalid Mermaid syntax are not included yet.

Export Markdown with only READY diagrams inserted at validated positions:

```sh
curl http://localhost:8080/api/blog/workflows/YOUR_REQUEST_ID/markdown
```

Generate/replan diagrams for an existing article, including articles created before this update
if still available in the same process (all in-memory data is lost on restart):

```sh
curl -X POST http://localhost:8080/api/blog/workflows/YOUR_REQUEST_ID/visuals/plan \
  -H 'Content-Type: application/json' \
  -d '{"instructions":"Explain the request ID and memory flow. Place the diagram after the introductory explanation and before implementation."}'
```

This returns a complete replacement plan (zero to three visuals); omitted visuals are removed.
Existing IDs are preserved when the planner keeps the same concept. Changed specifications
increment their version. Use this endpoint for natural-language diagram edits; ordinary
`/revisions` updates the article and flags affected visuals rather than silently replanning them.
Visual planning instructions are one-shot; current visual specifications, including captions
and placement, are provided in subsequent planning requests.

For a deterministic placement override, obtain a block ID from `layout.blocks` and a visual ID
from `layout.visuals[].spec.visualId`:

```sh
curl -X PATCH http://localhost:8080/api/blog/workflows/YOUR_REQUEST_ID/visuals/memory-flow/placement \
  -H 'Content-Type: application/json' \
  -d '{"afterBlockId":"COPY_A_BLOCK_ID_FROM_THE_WORKFLOW"}'
```

Moving a visual reruns review and returns to the human-approval boundary. No content is published.
Unknown workflows/visuals return 404; invalid anchors return 400; running or draftless workflows
return 409. Invalid replacement plans leave the existing layout intact. Review failures preserve
the previous layout and mark the workflow FAILED so you can retry. Full version snapshots remain
out of scope; the current visual specification and its version are stored, alongside stage history.

## Blog Studio (local UI)

Start the Spring Boot application and open **http://localhost:8080/blog/index.html**.
No separate frontend build or server is required. Spring serves the HTML, CSS, and JavaScript.

- Create an article by supplying a topic, audience, and scope.
- Select an existing article in the sidebar. The URL includes its request ID so it can be bookmarked.
- Read the rendered article, research sources, review issues, and workflow activity.
- Ask for changes in the editor; revisions reuse the article's chat memory.
- Replan explanatory diagrams or select the paragraph each diagram should follow.
- Open a standalone preview or download the composed Markdown.

The new `GET /api/blog/workflows` returns lightweight entries (`id`, `title`, `status`,
`updatedAt`) in most-recently-updated order. Existing mutation APIs are unchanged.
Generation and revision remain synchronous; the UI disables actions while awaiting the
result and preserves instructions on failure. Use Refresh to retrieve changes from another
window. The app still stops at the human-review boundary: it does not record final approval
or publish. Storage and memory remain process-local and disappear when the server restarts.
The dashboard itself has no external JavaScript dependencies; the existing preview loads
Markdown/Mermaid rendering libraries from a CDN and falls back to text when unavailable.
This is a local, unauthenticated workspace; do not expose it as a public editing service.

## Server-rendered visuals and MCP

The new [technical visuals guide](TECHNICAL_VISUALS.md) supersedes the Mermaid-only rendering
notes above. Plans can select Graphviz, PlantUML, or Mermaid. The server renders and stores
PNGs through local Kroki; Blog Studio displays those assets and supports individual render
retries. A failed visual no longer discards the article. See the guide for the opt-in MCP
profile, local deployment, tool inputs, and asset persistence limitations.

## Direct interactive editing

Use **Edit content** to edit the article title and Markdown without an AI rewrite. Click a
rendered diagram (or **Edit diagram script** in the sidebar) to edit its renderer, source,
caption and image description. **Render & save diagram** invokes Kroki only for that visual;
it preserves the article text, other visuals, and the old image if rendering fails. Diagram
source appears only in the editing dialog, never in the standalone reader preview.

- `PATCH /api/blog/workflows/{id}/content`: `title`, `markdown`, `expectedUpdatedAt`.
- `PATCH /api/blog/workflows/{id}/visuals/{visualId}`: `engine`, `source`, `caption`, `altText`, `expectedUpdatedAt`.

Use the exact `updatedAt` from the snapshot when opening the editor. Stale edits return 409;
invalid input returns 400; a failed render returns 422 without replacing the saved snapshot.
Manual edits invalidate the earlier automated review and require human review. Editing text
can mark diagram anchors as missing; relocate them using the existing placement controls.
The new Java endpoints require restarting the application. Export existing work first while
workflow storage remains in-memory. No automated review/model call is made by manual saves.

### Manual text formatting tools

The content editor includes heading, bold/italic, list, quote, link, inline-code, code-block,
table and divider buttons. Select text before applying a format; the editor retains Markdown
storage. It includes session-local undo/redo, spellcheck, word/character counts and an unsaved
formatting preview. Keyboard shortcuts: Ctrl/Cmd+B, I, K, Z, Shift+Z (or Y). Diagram source is
excluded from the preview. Preview libraries load from the same CDN as the reader; editing
works without them. `node scripts/test-text-editor.mjs` checks formatting transformations.
