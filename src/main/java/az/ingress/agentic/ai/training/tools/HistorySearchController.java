package az.ingress.agentic.ai.training.tools;

import az.ingress.agentic.ai.training.services.ConversationArchiveService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
public class HistorySearchController {

    private final ConversationArchiveService archiveService;

    @PostMapping("/api/history/search")
    public ConversationArchiveService.HistoryResult search(@RequestBody HistorySearchRequest request) {
        if (request.conversationId() == null
                || request.conversationId().isBlank()
                || request.question() == null
                || request.question().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "conversationId and question are required."
            );
        }

        double minimumScore = request.minimumScore() == null
                ? 0.3
                : request.minimumScore();

        if (!Double.isFinite(minimumScore)
                || minimumScore < -1
                || minimumScore > 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "minimumScore must be between -1 and 1."
            );
        }

        return archiveService.searchOlder(
                request.conversationId(),
                request.question(),
                minimumScore
        );
    }

    public record HistorySearchRequest(
            String conversationId,
            String question,
            Double minimumScore
    ) {
    }
}
