package az.ingress.agentic.ai.training;

import az.ingress.agentic.ai.training.context.AiContext;
import az.ingress.agentic.ai.training.context.AiContextBuilder;
import az.ingress.agentic.ai.training.context.RagContextFormatter;
import az.ingress.agentic.ai.training.dto.SupportResponse;
import az.ingress.agentic.ai.training.services.HybridSearchService;
import az.ingress.agentic.ai.training.tools.BankingTools;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
public class BankAssistant {

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final BankingTools bankingTools;
    private final AiContextBuilder contextBuilder;
    private final HybridSearchService hybridSearchService;

    @Value("classpath:/prompts/support-user.st")
    private Resource userPrompt;

    @GetMapping("/bank")
    public SupportResponse chat(
            @RequestParam("message") String message,
            @RequestParam("conversationId") String conversationId,
            @RequestParam(value = "ingestionId", required = false)
            String ingestionId,
            @RequestParam(value = "language", defaultValue = "English")
            String language,
            @RequestParam(value = "channel", defaultValue = "Web")
            String channel
    ) {
        if (message.isBlank() || conversationId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "message and conversationId must not be blank."
            );
        }

        String requestId = UUID.randomUUID().toString();
        long requestStarted = System.nanoTime();

        AiContext context = contextBuilder.build(
                message,
                conversationId,
                language,
                channel
        );

        // Inspect stored memory before the advisor processes this request.
        // This is NOT the final assembled model request.
        var historyBefore = chatMemory.get(context.conversationId());

        long historyTextCharacters = historyBefore.stream()
                .mapToLong(item -> characterCount(item.getText()))
                .sum();

        log.info(
                "BANK_MEMORY_BEFORE requestId={} "
                        + "storedMessages={} storedTextChars={}",
                requestId,
                historyBefore.size(),
                historyTextCharacters
        );

        var retrieval = hybridSearchService.search(
                new HybridSearchService.HybridInput(
                        message,
                        ingestionId,
                        3,
                        20
                )
        );

        String evidence = RagContextFormatter.format(
                retrieval.matches().stream()
                        .map(hit -> new RagContextFormatter.Passage(
                                hit.id(),
                                hit.text(),
                                hit.metadata()
                        ))
                        .toList()
        );

        String existingContext = context.knowledgeContext() == null
                ? ""
                : context.knowledgeContext();

        String combinedContext =
                "Existing application context:\n"
                        + existingContext
                        + "\n\nRetrieved document evidence for this request:\n"
                        + evidence;

        log.info(
                "BANK_CONTEXT requestId={} chunks={} "
                        + "questionChars={} existingContextChars={} "
                        + "retrievedContextChars={} combinedContextChars={}",
                requestId,
                retrieval.matches().size(),
                characterCount(message),
                characterCount(existingContext),
                characterCount(evidence),
                characterCount(combinedContext)
        );

        long modelStarted = System.nanoTime();

        try {
            var result = chatClient.prompt()
                    .user(user -> user
                            .text(userPrompt)
                            .param("message", message)
                            .param("language", context.language())
                            .param("channel", context.channel())
                            .param("context", combinedContext))
                    .advisors(advisor -> advisor.param(
                            ChatMemory.CONVERSATION_ID,
                            context.conversationId()
                    ))
                    .tools(bankingTools)
                    .call()
                    .responseEntity(SupportResponse.class);

            var response = result.getResponse();

            if (response != null
                    && response.getMetadata() != null
                    && response.getMetadata().getUsage() != null) {

                var metadata = response.getMetadata();
                var usage = metadata.getUsage();

                log.info(
                        "BANK_AI_USAGE requestId={} model={} "
                                + "inputTokens={} outputTokens={} "
                                + "totalTokens={} modelDurationMs={}",
                        requestId,
                        metadata.getModel(),
                        usage.getPromptTokens(),
                        usage.getCompletionTokens(),
                        usage.getTotalTokens(),
                        elapsedMillis(modelStarted)
                );
            } else {
                log.warn(
                        "BANK_AI_USAGE requestId={} usageUnavailable=true",
                        requestId
                );
            }

            SupportResponse answer = result.getEntity();

            if (answer == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "The model did not return a structured answer."
                );
            }

            // The memory advisor saves the exchange.
            // Do not manually add the same messages again here.
            var historyAfter = chatMemory.get(context.conversationId());

            log.info(
                    "BANK_MEMORY_AFTER requestId={} storedMessages={}",
                    requestId,
                    historyAfter.size()
            );

            log.info(
                    "BANK_COMPLETED requestId={} totalDurationMs={}",
                    requestId,
                    elapsedMillis(requestStarted)
            );

            return answer;

        } catch (RuntimeException exception) {
            log.error(
                    "BANK_FAILED requestId={} exceptionType={} "
                            + "totalDurationMs={}",
                    requestId,
                    exception.getClass().getSimpleName(),
                    elapsedMillis(requestStarted)
            );

            throw exception;
        }
    }

    private static int characterCount(String text) {
        return text == null
                ? 0
                : text.codePointCount(0, text.length());
    }

    private static long elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }
}