package az.ingress.agentic.ai.training.rag;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
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
import tools.jackson.databind.JsonNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Slf4j
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
        ChunkUploadErrorHandler.begin(file);
        PreparedUpload prepared = prepare(file, title);

        ChunkUploadErrorHandler.complete("preview", prepared.chunks().size());
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
            @RequestParam(value = "title", required = false) String title) {
        ChunkUploadErrorHandler.begin(file);
        PreparedUpload prepared = prepare(file, title);

        ChunkUploadErrorHandler.stage("VALIDATE_FOR_INGESTION");
        // Reject the separator-only chunks from the earlier Docling run.
        for (ChunkPreview chunk : prepared.chunks()) {
            ChunkUploadErrorHandler.chunk(chunk.chunkIndex());
            if (isFormattingOnly(chunk.rawText())) {
                ChunkUploadErrorHandler.code("FORMATTING_ONLY_CHUNK");
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
        ChunkUploadErrorHandler.stage("EMBED_AND_STORE");
        vectorStore.add(documents);
        ChunkUploadErrorHandler.complete("ingest", documents.size());

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
        ChunkUploadErrorHandler.stage("VALIDATE_SEARCH");
        if (input == null) {
            throw badRequest("A JSON search request body is required.");
        }
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

        ChunkUploadErrorHandler.stage("VECTOR_SEARCH");
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

        ChunkUploadErrorHandler.complete("search", matches.size());
        return new SearchResponse(input.question(), matches);
    }

    private PreparedUpload prepare(MultipartFile file, String title) {
        ChunkUploadErrorHandler.stage("VALIDATE_UPLOAD");
        if (file.isEmpty()) {
            ChunkUploadErrorHandler.code("EMPTY_UPLOAD");
            throw badRequest("Upload a non-empty .chunks.jsonl file.");
        }

        String documentTitle = clean(title);
        List<DoclingChunk> sourceChunks = readChunks(file);

        ChunkUploadErrorHandler.stage("HASH_UPLOAD");
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

        ChunkUploadErrorHandler.stage("PREPARE_CHUNKS");
        for (DoclingChunk chunk : sourceChunks) {
            ChunkUploadErrorHandler.chunk(chunk.chunkIndex());
            String text = clean(chunk.text());

            if (text.isBlank()) {
                ChunkUploadErrorHandler.code("EMPTY_AFTER_CLEANUP");
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

            if (!warnings.isEmpty()) {
                log.warn("requestId={} stage=PREPARE_CHUNKS chunkIndex={} warnings={}",
                        ChunkUploadErrorHandler.requestId(), chunk.chunkIndex(), warnings);
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
        ChunkUploadErrorHandler.stage("READ_JSONL");
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

                ChunkUploadErrorHandler.line(lineNumber);
                String trimmed = line.strip();
                if (trimmed.equals("{") || trimmed.equals("[")) {
                    ChunkUploadErrorHandler.code("EXPECTED_JSONL");
                    throw badRequest("Line " + lineNumber
                            + " contains only an opening bracket. Expected one complete chunk JSON object per line. "
                            + "This may be pretty-printed JSON or a JSON array; select Docling's .chunks.jsonl export.");
                }
                if (trimmed.startsWith("PK")) {
                    ChunkUploadErrorHandler.code("ZIP_UPLOADED");
                    throw badRequest("The upload appears to be a ZIP archive. Extract it and upload the .chunks.jsonl file.");
                }

                JsonNode node;
                try {
                    node = jsonMapper.readTree(line);
                } catch (JacksonException ex) {
                    ChunkUploadErrorHandler.parserFailure("INVALID_JSON", ex);
                    throw badRequest("Invalid JSON on line " + lineNumber
                            + ". Each non-empty line must contain one complete JSON object. "
                            + "Upload .chunks.jsonl, not Markdown, PDF, ZIP, or full-document JSON.");
                }
                if (node == null || !node.isObject()) {
                    ChunkUploadErrorHandler.code("EXPECTED_CHUNK_OBJECT");
                    throw badRequest("Line " + lineNumber
                            + " must be a chunk object, not an array, string, number, or null.");
                }
                if (node.has("schema_name") || node.has("document")) {
                    ChunkUploadErrorHandler.code("FULL_DOCUMENT_UPLOADED");
                    throw badRequest("Line " + lineNumber
                            + " appears to contain a full Docling document or conversion response. "
                            + "Select chunks in Docling and upload the .chunks.jsonl export.");
                }

                DoclingChunk chunk;
                try {
                    chunk = jsonMapper.readValue(line, DoclingChunk.class);
                } catch (JacksonException ex) {
                    ChunkUploadErrorHandler.parserFailure("CHUNK_FIELD_TYPE_MISMATCH", ex);
                    throw badRequest("JSON on line " + lineNumber
                            + " cannot be mapped to a chunk. Expected filename/text/raw_text as strings; "
                            + "chunk_index/num_tokens as integers; page_numbers as an integer array; "
                            + "headings/captions/doc_items as string arrays; metadata as an object.");
                }
                ChunkUploadErrorHandler.chunk(chunk.chunkIndex());

                validateChunk(chunk, lineNumber);

                ChunkKey key = new ChunkKey(
                        chunk.filename(),
                        chunk.chunkIndex()
                );

                if (!seen.add(key)) {
                    ChunkUploadErrorHandler.code("DUPLICATE_CHUNK");
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
            ChunkUploadErrorHandler.code("NO_CHUNKS");
            throw badRequest("The file contains no chunks.");
        }

        return chunks;
    }

    private static void validateChunk(DoclingChunk chunk, int lineNumber) {
        List<String> violations = new ArrayList<>();
        if (chunk == null) {
            violations.add("chunk must be an object");
        } else {
            if (chunk.filename() == null || chunk.filename().isBlank()) {
                violations.add("filename is required and must not be blank");
            }
            if (chunk.chunkIndex() == null || chunk.chunkIndex() < 0) {
                violations.add("chunk_index is required and must be an integer >= 0");
            }
            if (chunk.text() == null || chunk.text().isBlank()) {
                violations.add("text is required and must not be blank");
            }
        }
        if (!violations.isEmpty()) {
            ChunkUploadErrorHandler.code("INVALID_CHUNK_FIELDS");
            throw badRequest("Line " + lineNumber + ": " + String.join("; ", violations) + ".");
        }

        if (chunk.pageNumbers() != null
                && chunk.pageNumbers().stream()
                .anyMatch(page -> page == null || page < 1)) {
            ChunkUploadErrorHandler.code("INVALID_PAGE_NUMBERS");
            throw badRequest(
                    "Invalid page_numbers on line " + lineNumber + ": each entry must be an integer >= 1."
            );
        }

        if (chunk.docItems() != null
                && chunk.docItems().stream()
                .anyMatch(item -> item == null || item.isBlank())) {
            ChunkUploadErrorHandler.code("INVALID_SOURCE_REFERENCES");
            throw badRequest(
                    "Invalid doc_items on line " + lineNumber + ": entries must be non-null, non-blank strings."
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
    ) {
    }

    private record PreparedUpload(
            String ingestionId,
            List<ChunkPreview> chunks
    ) {
    }

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
    ) {
    }

    public record ChunkPreview(
            String id,
            int chunkIndex,
            String text,
            Map<String, Object> metadata,
            String rawText,
            List<String> warnings
    ) {
    }

    public record PreviewResponse(
            String ingestionId,
            int chunkCount,
            List<ChunkPreview> chunks
    ) {
    }

    public record IngestResponse(
            String ingestionId,
            int chunkCount,
            String status
    ) {
    }

    public record SearchInput(
            String question,
            String ingestionId,
            Integer topK
    ) {
    }

    public record SearchHit(
            String id,
            Double score,
            String text,
            Map<String, Object> metadata
    ) {
    }

    public record SearchResponse(
            String question,
            List<SearchHit> matches
    ) {
    }
}