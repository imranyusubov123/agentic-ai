package az.ingress.agentic.ai.training.rag;

import az.ingress.agentic.ai.training.services.HybridSearchService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/documents/chunks")
public class HybridSearchController {
    private final HybridSearchService hybridSearchService;

    public HybridSearchController(HybridSearchService hybridSearchService) {
        this.hybridSearchService = hybridSearchService;
    }

    @PostMapping(value = "/search/hybrid", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public HybridSearchService.HybridResponse search(
            @RequestBody HybridSearchService.HybridInput input) {
        ChunkUploadErrorHandler.stage("HYBRID_RETRIEVAL");
        HybridSearchService.HybridResponse response = hybridSearchService.search(input);
        ChunkUploadErrorHandler.complete("hybrid-search", response.matches().size());
        return response;
    }
}
