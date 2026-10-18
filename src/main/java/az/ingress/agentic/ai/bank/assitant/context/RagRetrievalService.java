package az.ingress.agentic.ai.bank.assitant.context;

import az.ingress.agentic.ai.training.context.RagContextFormatter;
import az.ingress.agentic.ai.training.services.HybridSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RagRetrievalService {

    private final HybridSearchService hybridSearchService;

    public String retrieve(
            String question,
            String ingestionId) {

        var retrieval = hybridSearchService.search(
                new HybridSearchService.HybridInput(
                        question,
                        ingestionId,
                        3,
                        20
                )
        );

        return RagContextFormatter.format(
                retrieval.matches()
                        .stream()
                        .map(hit ->
                                new RagContextFormatter.Passage(
                                        hit.id(),
                                        hit.text(),
                                        hit.metadata()
                                )
                        )
                        .toList()
        );
    }
}