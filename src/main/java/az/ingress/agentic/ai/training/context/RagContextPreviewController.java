package az.ingress.agentic.ai.training.context;

import az.ingress.agentic.ai.training.services.HybridSearchService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rag/context")
public class RagContextPreviewController {

    private final HybridSearchService hybridSearchService;

    public RagContextPreviewController(
            HybridSearchService hybridSearchService
    ) {
        this.hybridSearchService = hybridSearchService;
    }

    @PostMapping("/preview")
    public ContextPreview preview(@RequestBody PreviewRequest request) {

        int topK = request.topK() == null ? 3 : request.topK();
        int candidateK = request.candidateK() == null
                ? 20
                : request.candidateK();

        var searchResult = hybridSearchService.search(
                new HybridSearchService.HybridInput(
                        request.question(),
                        request.ingestionId(),
                        topK,
                        candidateK
                )
        );

        List<ChunkPreview> chunks = searchResult.matches().stream()
                .map(hit -> new ChunkPreview(
                        hit.id(),
                        hit.hybridScore(),
                        characterCount(hit.text()),
                        hit.text(),
                        hit.metadata()
                ))
                .toList();

        String context = formatContext(chunks);

        return new ContextPreview(
                searchResult.question(),
                searchResult.ingestionId(),
                chunks.size(),
                characterCount(context),
                chunks,
                context
        );
    }

    private static String formatContext(List<ChunkPreview> chunks) {
        if (chunks.isEmpty()) {
            return "No supporting document passages were retrieved.";
        }

        StringBuilder context = new StringBuilder();

        for (int i = 0; i < chunks.size(); i++) {
            ChunkPreview chunk = chunks.get(i);

            Map<String, Object> metadata = chunk.metadata() == null
                    ? Map.of()
                    : chunk.metadata();

            context.append("[S").append(i + 1).append("]\n")
                    .append("Chunk ID: ")
                    .append(chunk.id()).append('\n')
                    .append("Source: ")
                    .append(metadata.getOrDefault(
                            "source_file", "Unknown"
                    )).append('\n')
                    .append("Pages: ")
                    .append(metadata.getOrDefault(
                            "page_numbers", List.of()
                    )).append('\n')
                    .append("Headings: ")
                    .append(metadata.getOrDefault(
                            "headings", List.of()
                    )).append('\n')
                    .append("Passage:\n")
                    .append(chunk.text() == null ? "" : chunk.text())
                    .append("\n\n");
        }

        return context.toString();
    }

    private static int characterCount(String text) {
        return text == null
                ? 0
                : text.codePointCount(0, text.length());
    }

    public record PreviewRequest(
            String question,
            String ingestionId,
            Integer topK,
            Integer candidateK
    ) {
    }

    public record ChunkPreview(
            String id,
            double hybridScore,
            int textCharacters,
            String text,
            Map<String, Object> metadata
    ) {
    }

    public record ContextPreview(
            String question,
            String ingestionId,
            int chunkCount,
            int contextCharacters,
            List<ChunkPreview> chunks,
            String context
    ) {
    }
}