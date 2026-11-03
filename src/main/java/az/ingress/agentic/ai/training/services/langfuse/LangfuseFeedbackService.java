package az.ingress.agentic.ai.training.services.langfuse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class LangfuseFeedbackService {

    private static final Logger log =
            LoggerFactory.getLogger(LangfuseFeedbackService.class);

    private final RestClient client;

    public LangfuseFeedbackService(
            RestClient.Builder builder,
            @Value("${langfuse.base-url}") String baseUrl,
            @Value("${langfuse.public-key}") String publicKey,
            @Value("${langfuse.secret-key}") String secretKey) {

        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();

        requestFactory.setConnectTimeout(5_000);
        requestFactory.setReadTimeout(10_000);

        this.client = builder.clone()
                .baseUrl(baseUrl.replaceAll("/+$", ""))
                .requestFactory(requestFactory)
                .defaultHeaders(headers ->
                        headers.setBasicAuth(publicKey, secretKey))
                .build();
    }

    public void submit(
            UUID feedbackId,
            String traceId,
            int value,
            String comment) {

        if (feedbackId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Feedback ID is required"
            );
        }

        if (traceId == null
                || traceId.isBlank()
                || traceId.matches("0+")) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The answer has no valid Langfuse trace ID"
            );
        }

        if (value != 0 && value != 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Feedback value must be 0 or 1"
            );
        }

        ScoreRequest body = new ScoreRequest(
                feedbackId.toString(),
                traceId,
                "user_feedback",
                "BOOLEAN",
                value,
                comment == null ? "" : comment.trim()
        );

        try {
            client.post()
                    .uri("/api/public/scores")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            log.info(
                    "Langfuse accepted feedback: scoreId={}, traceId={}",
                    feedbackId,
                    traceId
            );

        } catch (RestClientResponseException exception) {
            log.warn(
                    "Langfuse rejected feedback: status={}, scoreId={}, traceId={}",
                    exception.getStatusCode().value(),
                    feedbackId,
                    traceId
            );

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Langfuse rejected feedback. Check the backend logs.",
                    exception
            );

        } catch (RestClientException exception) {
            log.warn(
                    "Langfuse connection failed: scoreId={}, traceId={}, errorType={}",
                    feedbackId,
                    traceId,
                    exception.getClass().getSimpleName()
            );

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Could not reach Langfuse. Please retry.",
                    exception
            );
        }
    }

    private record ScoreRequest(
            String id,
            String traceId,
            String name,
            String dataType,
            int value,
            String comment
    ) {
    }
}