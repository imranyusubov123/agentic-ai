package az.ingress.agentic.ai.training.controller;

import az.ingress.agentic.ai.training.services.BankingFeedbackService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/banking")
@RequiredArgsConstructor
public class BankingFeedbackController {

    private final BankingFeedbackService feedbackService;

    @PostMapping("/feedback")
    public ResponseEntity<Void> submitFeedback(
            HttpSession session,
            @Valid @RequestBody FeedbackRequest request) {
        log.info("Feedback received ....{}", request);
        feedbackService.save(
                session,
                request.id(),
                request.conversationId(),
                request.messageId(),
                request.traceId(),
                request.value(),
                request.comment()
        );


        return ResponseEntity.noContent().build();
    }

    public record FeedbackRequest(
            @NotNull UUID id,
            @NotNull UUID conversationId,
            @NotNull UUID messageId,
            @Size(max = 128)
            String traceId,
            @NotBlank
            @Pattern(regexp = "user_feedback")
            String name,
            @NotNull
            @Min(0)
            @Max(1)
            Integer value,
            @Size(max = 2000)
            String comment
    ) {
    }
}