package az.ingress.agentic.ai.bank.assitant.service;

import az.ingress.agentic.ai.bank.assitant.context.ContextOrchestrator;
import az.ingress.agentic.ai.bank.assitant.observability.AiUsageMetricsService;
import az.ingress.agentic.ai.training.context.AiContext;
import az.ingress.agentic.ai.training.dto.SupportResponse;
import az.ingress.agentic.ai.training.services.ConversationArchiveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class BankAssistantService {

    private final ContextOrchestrator contextOrchestrator;
    private final BankingChatService bankingChatService;
    private final AiUsageMetricsService usageMetricsService;
    private final ConversationArchiveService archiveService;

    public SupportResponse chat(
            String message,
            String conversationId,
            String ingestionId,
            String language,
            String channel) {

        UUID exchangeId = UUID.randomUUID();
        String requestId = exchangeId.toString();

        long started = System.nanoTime();

        try {
            // 1. Prepare context
            ContextOrchestrator.PreparedContext prepared =
                    contextOrchestrator.prepare(
                            message,
                            conversationId,
                            ingestionId,
                            language,
                            channel
                    );

            AiContext context = prepared.context();

            // 2. Invoke LLM
            BankingChatService.ChatExecution execution =
                    bankingChatService.chat(
                            message,
                            context,
                            prepared.combinedContext()
                    );

            // 3. Record model usage
            usageMetricsService.record(
                    execution.response(),
                    requestId
            );

            // 4. Validate structured response
            SupportResponse answer = execution.answer();

            if (answer == null || answer.answer() == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "The model did not return a structured answer."
                );
            }

            // 5. Archive original exchange
            archiveService.save(
                    exchangeId,
                    context.conversationId(),
                    message,
                    answer.answer()
            );

            log.info(
                    "BANK_COMPLETED requestId={} durationMs={}",
                    requestId,
                    elapsedMillis(started)
            );

            return answer;

        } catch (RuntimeException ex) {

            log.error(
                    "BANK_FAILED requestId={} type={} durationMs={}",
                    requestId,
                    ex.getClass().getSimpleName(),
                    elapsedMillis(started),
                    ex
            );

            throw ex;
        }
    }

    private static long elapsedMillis(long started) {
        return TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - started
        );
    }
}