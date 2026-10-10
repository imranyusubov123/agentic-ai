package az.ingress.agentic.ai.training.rag;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@RestController
@RequestMapping("/api/documents/chunks")
public class ChunkUploadController {

    private static final String EMBEDDING_MODEL = "text-embedding-3-small";
    private static final String PREPARATION_VERSION = "1";

    private final JsonMapper jsonMapper;
    private final VectorStore vectorStore;

    public ChunkUploadController(
            JsonMapper jsonMapper,
            VectorStore vectorStore
    ) {
        this.jsonMapper = jsonMapper;
        this.vectorStore = vectorStore;
    }

    @PostMapping(
            value = "/preview",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public PreviewResponse preview(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title
    ) {
        PreparedUpload prepared = prepare(file, title);

        return new PreviewResponse(
                prepared.ingestionId(),
                prepared.chunks().size(),
                prepared.chunks()
        );
    }

    @PostMapping(
            value = "/ingest",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public IngestResponse ingest(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title
    ) {
        PreparedUpload prepared = prepare(file, title);

        // Reject the separator-only chunks from the earlier Docling run.
        for (ChunkPreview chunk : prepared.chunks()) {
            if (isFormattingOnly(chunk.rawText())) {
                throw badRequest(
                        "Chunk " + chunk.chunkIndex()
                                + " contains only table formatting. "
                                + "Upload the corrected Docling chunks."
                );
            }
        }

        List<Document> documents = prepared.chunks().stream()
                .map(chunk -> Document.builder()
                        .id(chunk.id())
                        .text(chunk.text())
                        .metadata(chunk.metadata())
                        .build())
                .toList();

        // Spring AI calls OpenAI for embeddings and upserts into pgvector.
        vectorStore.add(documents);

        return new IngestResponse(
                prepared.ingestionId(),
                documents.size(),
                "Stored"
        );
    }

    @PostMapping(
            value = "/search",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public SearchResponse search(@RequestBody SearchInput input) {
        if (input.question() == null || input.question().isBlank()) {
            throw badRequest("question is required.");
        }

        if (input.ingestionId() == null || input.ingestionId().isBlank()) {
            throw badRequest("ingestionId is required.");
        }

        int topK = input.topK() == null ? 3 : input.topK();

        if (topK < 1 || topK > 20) {
            throw badRequest("topK must be between 1 and 20.");
        }

        FilterExpressionBuilder filter = new FilterExpressionBuilder();

        List<Document> documents = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(input.question().strip())
                        .topK(topK)
                        .similarityThreshold(0.0)
                        .filterExpression(
                                filter.eq(
                                        "ingestion_id",
                                        input.ingestionId().strip()
                                ).build()
                        )
                        .build()
        );

        List<SearchHit> matches = documents == null
                ? List.of()
                : documents.stream()
                .map(document -> new SearchHit(
                        document.getId(),
                        document.getScore(),
                        document.getText(),
                        document.getMetadata()
                ))
                .toList();

        return new SearchResponse(input.question(), matches);
    }

    private PreparedUpload prepare(MultipartFile file, String title) {
        if (file.isEmpty()) {
            throw badRequest("Upload a non-empty .chunks.jsonl file.");
        }

        String documentTitle = clean(title);
        List<DoclingChunk> sourceChunks = readChunks(file);

        // Stable for the same uploaded bytes, title, and configuration.
        String ingestionId;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(file.getBytes());
            digest.update((byte) 0);
            digest.update(documentTitle.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(EMBEDDING_MODEL.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(PREPARATION_VERSION.getBytes(StandardCharsets.UTF_8));
            ingestionId = HexFormat.of().formatHex(digest.digest());
        } catch (IOException ex) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not read the uploaded file.",
                    ex
            );
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable.", ex);
        }

        List<ChunkPreview> prepared = new ArrayList<>();

        for (DoclingChunk chunk : sourceChunks) {
            String text = clean(chunk.text());

            if (text.isBlank()) {
                throw badRequest(
                        "Chunk " + chunk.chunkIndex()
                                + " is empty after cleanup."
                );
            }

            if (!documentTitle.isBlank()) {
                text = "Document: " + documentTitle + "\n\n" + text;
            }

            List<String> headings = chunk.headings() == null
                    ? List.of()
                    : chunk.headings().stream()
                    .filter(Objects::nonNull)
                    .map(ChunkUploadController::clean)
                    .filter(value -> !value.isBlank())
                    .toList();

            List<Integer> pages = chunk.pageNumbers() == null
                    ? List.of()
                    : chunk.pageNumbers();

            List<String> sourceItems = chunk.docItems() == null
                    ? List.of()
                    : chunk.docItems();

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("ingestion_id", ingestionId);
            metadata.put("source_file", chunk.filename());
            metadata.put("chunk_index", chunk.chunkIndex());
            metadata.put("headings", headings);
            metadata.put("page_numbers", pages);
            metadata.put("doc_items", sourceItems);
            metadata.put("embedding_model", EMBEDDING_MODEL);
            metadata.put("preparation_version", PREPARATION_VERSION);

            if (!documentTitle.isBlank()) {
                metadata.put("document_title", documentTitle);
            }

            if (chunk.metadata() != null) {
                metadata.put("docling_metadata", chunk.metadata());
            }

            if (chunk.captions() != null && !chunk.captions().isEmpty()) {
                metadata.put("captions", chunk.captions());
            }

            if (chunk.numTokens() != null) {
                // Original count: not recalculated after cleanup/title.
                metadata.put("docling_reported_tokens", chunk.numTokens());
            }

            String identity = ingestionId
                    + "\u0000" + chunk.filename()
                    + "\u0000" + chunk.chunkIndex();

            String id = UUID.nameUUIDFromBytes(
                    identity.getBytes(StandardCharsets.UTF_8)
            ).toString();

            List<String> warnings = new ArrayList<>();

            if (pages.isEmpty()) {
                warnings.add("Missing source page numbers.");
            }

            if (sourceItems.isEmpty()) {
                warnings.add("Missing source item references.");
            }

            if (headings.isEmpty()) {
                warnings.add("No section headings supplied.");
            }

            if (chunk.rawText() == null || chunk.rawText().isBlank()) {
                warnings.add(
                        "Missing raw_text: cannot check for formatting-only content."
                );
            } else if (isFormattingOnly(chunk.rawText())) {
                warnings.add("Chunk contains only table formatting.");
            }

            prepared.add(new ChunkPreview(
                    id,
                    chunk.chunkIndex(),
                    text,
                    metadata,
                    chunk.rawText(),
                    warnings
            ));
        }

        return new PreparedUpload(ingestionId, prepared);
    }

    private List<DoclingChunk> readChunks(MultipartFile file) {
        List<DoclingChunk> chunks = new ArrayList<>();
        Set<ChunkKey> seen = new HashSet<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        file.getInputStream(),
                        StandardCharsets.UTF_8
                )
        )) {
            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;

                if (lineNumber == 1 && line.startsWith("\uFEFF")) {
                    line = line.substring(1);
                }

                if (line.isBlank()) {
                    continue;
                }

                DoclingChunk chunk;

                try {
                    chunk = jsonMapper.readValue(line, DoclingChunk.class);
                } catch (JacksonException ex) {
                    throw badRequest(
                            "Invalid JSON on line " + lineNumber
                                    + ". Upload .chunks.jsonl, "
                                    + "not the ZIP or full-document JSON."
                    );
                }

                validateChunk(chunk, lineNumber);

                ChunkKey key = new ChunkKey(
                        chunk.filename(),
                        chunk.chunkIndex()
                );

                if (!seen.add(key)) {
                    throw badRequest(
                            "Duplicate filename/chunk_index on line "
                                    + lineNumber
                    );
                }

                chunks.add(chunk);
            }
        } catch (IOException ex) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not read the uploaded file.",
                    ex
            );
        }

        if (chunks.isEmpty()) {
            throw badRequest("The file contains no chunks.");
        }

        return chunks;
    }

    private static void validateChunk(DoclingChunk chunk, int lineNumber) {
        if (chunk == null
                || chunk.filename() == null
                || chunk.filename().isBlank()
                || chunk.chunkIndex() == null
                || chunk.chunkIndex() < 0
                || chunk.text() == null
                || chunk.text().isBlank()) {
            throw badRequest(
                    "Line " + lineNumber
                            + " requires filename, chunk_index >= 0, and text."
            );
        }

        if (chunk.pageNumbers() != null
                && chunk.pageNumbers().stream()
                .anyMatch(page -> page == null || page < 1)) {
            throw badRequest(
                    "Invalid page_numbers on line " + lineNumber
            );
        }

        if (chunk.docItems() != null
                && chunk.docItems().stream()
                .anyMatch(item -> item == null || item.isBlank())) {
            throw badRequest(
                    "Invalid doc_items on line " + lineNumber
            );
        }
    }

    private static boolean isFormattingOnly(String rawText) {
        String text = clean(rawText);
        return !text.isBlank() && text.matches("[\\s|:\\-]+");
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replaceAll("[\\p{Cc}&&[^\\r\\n\\t]]", "")
                .replaceAll("•(?:[ \\t]*•)+", "•")
                .replaceAll(
                        "(?m)^([ \\t]*[-*])[ \\t]+[·•][ \\t]*",
                        "$1 "
                )
                .strip();
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                message
        );
    }

    private record ChunkKey(
            String filename,
            Integer chunkIndex
    ) {}

    private record PreparedUpload(
            String ingestionId,
            List<ChunkPreview> chunks
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DoclingChunk(
            String filename,
            @JsonProperty("chunk_index") Integer chunkIndex,
            String text,
            @JsonProperty("raw_text") String rawText,
            @JsonProperty("num_tokens") Integer numTokens,
            List<String> headings,
            List<String> captions,
            @JsonProperty("page_numbers") List<Integer> pageNumbers,
            @JsonProperty("doc_items") List<String> docItems,
            Map<String, Object> metadata
    ) {}

    public record ChunkPreview(
            String id,
            int chunkIndex,
            String text,
            Map<String, Object> metadata,
            String rawText,
            List<String> warnings
    ) {}

    public record PreviewResponse(
            String ingestionId,
            int chunkCount,
            List<ChunkPreview> chunks
    ) {}

    public record IngestResponse(
            String ingestionId,
            int chunkCount,
            String status
    ) {}

    public record SearchInput(
            String question,
            String ingestionId,
            Integer topK
    ) {}

    public record SearchHit(
            String id,
            Double score,
            String text,
            Map<String, Object> metadata
    ) {}

    public record SearchResponse(
            String question,
            List<SearchHit> matches
    ) {}
}