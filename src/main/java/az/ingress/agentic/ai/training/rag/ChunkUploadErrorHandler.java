package az.ingress.agentic.ai.training.rag;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

/**
 * Global registration also catches multipart failures before controller invocation.
 * Requests outside /api/documents/chunks are rethrown to normal error handling.
 * Does not log document text, questions, titles, or exception messages that may
 * contain uploaded content. Keep this class under your application scan package.
 */
@Slf4j
@RestControllerAdvice
public class ChunkUploadErrorHandler {

    private static final String ATTRIBUTE = ChunkUploadErrorHandler.class.getName() + ".diagnostics";

    private static final class Diagnostics {
        final String requestId = UUID.randomUUID().toString();
        final long started = System.nanoTime();
        String stage = "REQUEST_BINDING";
        String code;
        String parserException;
        Integer line;
        Integer chunkIndex;
    }

    private static Diagnostics diagnostics(HttpServletRequest request) {
        Object existing = request.getAttribute(ATTRIBUTE);
        if (existing instanceof Diagnostics value) {
            return value;
        }
        Diagnostics value = new Diagnostics();
        request.setAttribute(ATTRIBUTE, value);
        return value;
    }

    private static Diagnostics current() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        Diagnostics value = diagnostics(attributes.getRequest());
        if (attributes.getResponse() != null) {
            attributes.getResponse().setHeader("X-Request-ID", value.requestId);
        }
        return value;
    }

    public static String requestId() {
        return current().requestId;
    }

    public static void begin(MultipartFile file) {
        Diagnostics d = current();
        log.info("requestId={} event=upload_received bytes={} multipartField={}",
                d.requestId, file.getSize(), safe(file.getName()));
    }

    public static void stage(String stage) {
        Diagnostics d = current();
        d.stage = stage;
        d.code = null;
        d.parserException = null;
        d.line = null;
        d.chunkIndex = null;
        log.info("requestId={} stage={} event=started", d.requestId, stage);
    }

    public static void line(int line) {
        Diagnostics d = current();
        d.line = line;
        d.chunkIndex = null;
    }

    public static void chunk(Integer index) {
        current().chunkIndex = index;
    }

    public static void code(String code) {
        current().code = code;
    }

    public static void parserFailure(String code, Exception exception) {
        Diagnostics d = current();
        d.code = code;
        d.parserException = exception.getClass().getSimpleName();
        // Jackson messages can include document contents; intentionally omit them.
    }

    public static void complete(String operation, int count) {
        Diagnostics d = current();
        log.info("requestId={} operation={} event=completed count={} durationMs={}",
                d.requestId, operation, count, elapsed(d));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handle(Exception ex, HttpServletRequest request) throws Exception {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (!path.equals("/api/documents/chunks") && !path.startsWith("/api/documents/chunks/")) {
            throw ex;
        }

        Diagnostics d = diagnostics(request);
        int status;
        String code;
        String message;
        HttpHeaders headers = new HttpHeaders();

        if (ex instanceof MaxUploadSizeExceededException) {
            status = 413;
            code = "UPLOAD_TOO_LARGE";
            message = "Upload exceeds the configured multipart limit. Check spring.servlet.multipart.max-file-size "
                    + "and spring.servlet.multipart.max-request-size.";
        } else if (ex instanceof MissingServletRequestPartException missing) {
            status = 400;
            code = "MISSING_MULTIPART_PART";
            message = "Missing multipart part '" + safe(missing.getRequestPartName())
                    + "'. In Swagger select a file; in Postman use Body > form-data, key file, type File.";
        } else if (ex instanceof MultipartException) {
            status = 400;
            code = "MALFORMED_MULTIPART";
            message = "Could not parse the multipart upload. Send form-data with a file field. "
                    + "Let Swagger/Postman generate Content-Type and its boundary.";
        } else if (ex instanceof HttpMediaTypeNotSupportedException) {
            status = 415;
            code = "UNSUPPORTED_CONTENT_TYPE";
            message = "Preview/ingest require multipart/form-data; search requires application/json.";
            headers.putAll(((ErrorResponse) ex).getHeaders());
        } else if (ex instanceof HttpMessageNotReadableException) {
            status = 400;
            code = "INVALID_REQUEST_BODY";
            message = "Search requires a JSON object with question and ingestionId strings, "
                    + "and an optional integer topK. Check JSON syntax and field types.";
        } else if (ex instanceof ResponseStatusException known) {
            status = known.getStatusCode().value();
            headers.putAll(known.getHeaders());
            code = d.code != null ? d.code : (status < 500 ? "VALIDATION_ERROR" : "PROCESSING_FAILED");
            message = status < 500 && known.getReason() != null
                    ? known.getReason()
                    : "Processing failed during " + d.stage + ". Use requestId to locate the server log.";
        } else if (ex instanceof ErrorResponse frameworkError) {
            status = frameworkError.getStatusCode().value();
            headers.putAll(frameworkError.getHeaders());
            code = "HTTP_REQUEST_ERROR";
            message = "Spring rejected this request. Check method, parameters, headers and request body. "
                    + "Exception type: " + ex.getClass().getSimpleName() + ".";
        } else {
            status = 500;
            code = "INTERNAL_ERROR";
            message = "Unexpected failure during " + d.stage + ". Use requestId to locate the server log.";
        }

        String exceptionType = d.parserException != null ? d.parserException : ex.getClass().getSimpleName();
        if (status < 500) {
            log.warn("requestId={} status={} code={} stage={} line={} chunkIndex={} exception={} detail={}",
                    d.requestId, status, code, d.stage, d.line, d.chunkIndex, exceptionType, safe(message));
        } else {
            log.error("requestId={} status={} code={} stage={} line={} chunkIndex={} exception={} durationMs={}",
                    d.requestId, status, code, d.stage, d.line, d.chunkIndex, exceptionType, elapsed(d));
            // Preserve diagnostic stack locations without messages containing credentials/content.
            Throwable cause = ex;
            for (int depth = 0; cause != null && depth < 8; depth++, cause = cause.getCause()) {
                log.error("requestId={} causeDepth={} type={}", d.requestId, depth, cause.getClass().getName());
                StackTraceElement[] frames = cause.getStackTrace();
                for (int i = 0; i < Math.min(frames.length, 10); i++) {
                    log.error("requestId={} at={}", d.requestId, frames[i]);
                }
            }
        }

        headers.set("X-Request-ID", d.requestId);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpStatus httpStatus = HttpStatus.resolve(status);
        return new ResponseEntity<>(new ApiError(
                Instant.now().toString(), status,
                httpStatus == null ? "HTTP error" : httpStatus.getReasonPhrase(),
                code, message, path, d.requestId, d.stage, d.line, d.chunkIndex, exceptionType
        ), headers, status);
    }

    private static long elapsed(Diagnostics d) {
        return (System.nanoTime() - d.started) / 1_000_000;
    }

    private static String safe(String value) {
        if (value == null) return "";
        String cleaned = value.replaceAll("[\\p{Cc}]", " ");
        return cleaned.length() > 2000 ? cleaned.substring(0, 2000) : cleaned;
    }

    public record ApiError(
            String timestamp,
            int status,
            String error,
            String code,
            String message,
            String path,
            String requestId,
            String stage,
            Integer line,
            Integer chunkIndex,
            String exceptionType
    ) {
    }
}

