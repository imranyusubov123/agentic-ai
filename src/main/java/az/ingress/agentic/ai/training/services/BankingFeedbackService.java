package az.ingress.agentic.ai.training.services;

import az.ingress.agentic.ai.training.services.langfuse.LangfuseFeedbackService;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class BankingFeedbackService {

    private final LangfuseFeedbackService langfuseFeedbackService;

    public BankingFeedbackService(
            LangfuseFeedbackService langfuseFeedbackService) {
        this.langfuseFeedbackService = langfuseFeedbackService;
    }

    private static final String SESSION_STATE =
            BankingFeedbackService.class.getName() + ".state";

    /*
     * Call this from your chat controller after an answer completes,
     * before sending the SSE "done" event.
     *
     * traceId must come from your backend tracing context.
     */
    public void registerCompletedAnswer(
            HttpSession session,
            UUID conversationId,
            UUID messageId,
            String traceId) {

        Objects.requireNonNull(conversationId);
        Objects.requireNonNull(messageId);

        SessionState state = state(session);

        synchronized (state) {
            AnswerKey key = new AnswerKey(conversationId, messageId);
            Answer existing = state.answers.get(key);

            if (existing != null) {
                if (!Objects.equals(existing.traceId(), traceId)) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "This message ID already belongs to another answer"
                    );
                }

                return;
            }

            state.answers.put(key, new Answer(traceId));
        }
    }

    public void save(
            HttpSession session,
            UUID feedbackId,
            UUID conversationId,
            UUID messageId,
            String suppliedTraceId,
            int value,
            String comment) {

        SessionState state = state(session);

        String normalizedComment =
                comment == null ? "" : comment.trim();

        synchronized (state) {
            AnswerKey key = new AnswerKey(conversationId, messageId);
            Answer answer = state.answers.get(key);

            // An answer from another session is also treated as missing.
            if (answer == null) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Completed answer not found in this session"
                );
            }

            if (suppliedTraceId != null
                    && !suppliedTraceId.equals(answer.traceId())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Trace does not match this answer"
                );
            }

            // One rating per answer, including duplicate submissions.
            UUID existingId = state.feedbackByAnswer.get(key);

            if (existingId != null) {
                SavedFeedback existing = state.feedback.get(existingId);

                if (existing.id().equals(feedbackId)
                        && existing.value() == value
                        && existing.comment().equals(normalizedComment)) {
                    return;
                }

                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Feedback has already been submitted for this answer"
                );
            }

            // A feedback ID cannot be reused for a different answer.
            if (state.feedback.containsKey(feedbackId)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Feedback ID is already in use"
                );
            }

            if (answer.traceId() == null || answer.traceId().isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "The completed answer has no Langfuse trace ID"
                );
            }

            // Use the original chat trace, not the feedback request trace.
            // Do not mark feedback as saved unless Langfuse accepts it.
            langfuseFeedbackService.submit(
                    feedbackId,
                    answer.traceId(),
                    value,
                    normalizedComment
            );

            SavedFeedback feedback = new SavedFeedback(
                    feedbackId,
                    state.reviewerId,
                    conversationId,
                    messageId,
                    answer.traceId(),
                    "user_feedback",
                    value,
                    normalizedComment,
                    Instant.now()
            );

            state.feedback.put(feedbackId, feedback);
            state.feedbackByAnswer.put(key, feedbackId);
        }
    }

    private SessionState state(HttpSession session) {
        synchronized (session) {
            SessionState state =
                    (SessionState) session.getAttribute(SESSION_STATE);

            if (state == null) {
                state = new SessionState();
                session.setAttribute(SESSION_STATE, state);
            }

            return state;
        }
    }

    private static final class SessionState {

        private final UUID reviewerId = UUID.randomUUID();

        private final Map<AnswerKey, Answer> answers = new HashMap<>();

        private final Map<UUID, SavedFeedback> feedback = new HashMap<>();

        private final Map<AnswerKey, UUID> feedbackByAnswer =
                new HashMap<>();
    }

    private record AnswerKey(
            UUID conversationId,
            UUID messageId
    ) {
    }

    private record Answer(
            String traceId
    ) {
    }

    public record SavedFeedback(
            UUID id,
            UUID anonymousReviewerId,
            UUID conversationId,
            UUID messageId,
            String traceId,
            String name,
            int value,
            String comment,
            Instant submittedAt
    ) {
    }
}
